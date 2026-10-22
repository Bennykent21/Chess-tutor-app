package com.chesstutor.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.PlayRecommendation
import com.chesstutor.app.domain.game.PlayRecommender
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.feature.play.ActiveGameSnapshot
import com.chesstutor.app.feature.play.ActiveGameStore
import com.chesstutor.app.feature.play.BotGame
import com.chesstutor.app.feature.play.GamePhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/** An unfinished game shown on Home's Continue card. */
data class ResumeCard(val bot: RosterBot, val moveCount: Int, val playerColor: PieceColor)

/** Everything Home draws besides the player's name and rating (plan 6.1). */
data class HomeUiState(
    val roster: List<RosterBot> = BotRoster.bots,
    val resume: ResumeCard? = null,
    val recommendation: PlayRecommendation? = null,
    val dayStreak: Int = 0,
    val dailySolved: Boolean = false,
    val gamesPlayed: Int = 0,
)

/**
 * The Home tab's view model: the roster, the resume and recommended cards, and the game being
 * played. Unfinished games are saved after every move ([ActiveGameStore]) and cleared at the end.
 */
class HomeViewModel(
    private val engine: EngineService,
    private val openings: suspend () -> OpeningTree,
    private val games: GameRepository,
    puzzleStats: Flow<PuzzleStats>,
    private val store: ActiveGameStore,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val playerRating = MutableStateFlow(800)
    private val snapshot = MutableStateFlow(store.load())

    val home: StateFlow<HomeUiState> =
        combine(games.observeGames(), puzzleStats, snapshot, playerRating) { list, stats, saved, rating ->
            HomeUiState(
                resume = saved?.let { s -> BotRoster.byId(s.botId)?.let { ResumeCard(it, s.moves.size, s.playerColor) } },
                recommendation = PlayRecommender.recommend(list.botGamesNewestFirst(), rating),
                dayStreak = stats.dayStreak,
                dailySolved = stats.solvedToday,
                gamesPlayed = list.count { it.id.startsWith("bot_") },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(resume = snapshot.value?.let { s ->
            BotRoster.byId(s.botId)?.let { ResumeCard(it, s.moves.size, s.playerColor) }
        }))

    private val mutableGame = MutableStateFlow<BotGame?>(null)
    /** The game on screen, if any. */
    val game: StateFlow<BotGame?> = mutableGame.asStateFlow()
    private var saver: Job? = null

    /** The player's rating, for picking a fair bot (it lives in the app's older state for now). */
    fun setPlayerRating(rating: Int) {
        playerRating.value = rating
    }

    /** Quick match: the recommended bot, a random colour, the default rules. */
    fun quickMatch() {
        val bot = home.value.recommendation?.bot ?: BotRoster.closestTo(playerRating.value)
        start(bot, SideChoice.RANDOM, GameRules())
    }

    fun start(bot: RosterBot, side: SideChoice, rules: GameRules) {
        val color = when (side) {
            SideChoice.WHITE -> PieceColor.WHITE
            SideChoice.BLACK -> PieceColor.BLACK
            SideChoice.RANDOM -> if (random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }
        launch(GameSetup(bot.profile, color, rules))
    }

    /** The same bot and rules again, with colours swapped. */
    fun rematch() {
        val last = mutableGame.value?.setup ?: return
        launch(last.copy(playerColor = last.playerColor.opposite()))
    }

    /** Picks up the saved unfinished game. */
    fun resume() {
        val saved = snapshot.value ?: return
        val bot = BotRoster.byId(saved.botId)
        if (bot == null) {
            clearSaved()
            return
        }
        launch(GameSetup(bot.profile, saved.playerColor, saved.rules, saved.startFen), saved)
    }

    /** Leaves the game screen: an unfinished game stays saved for the Continue card. */
    fun closeGame() {
        saver?.cancel()
        mutableGame.value?.let { game ->
            game.pause()
            if (game.state.value.phase != GamePhase.OVER) save(game)
        }
        mutableGame.value = null
    }

    private fun launch(setup: GameSetup, resumeFrom: ActiveGameSnapshot? = null) {
        closeGame()
        val game = BotGame(
            setup = setup,
            brain = BotBrain(engine, random),
            engine = engine,
            openings = openings,
            games = games,
            scope = viewModelScope,
            clock = clock,
            resumeMoves = resumeFrom?.moves.orEmpty(),
            resumeClocks = resumeFrom?.let { it.whiteMs to it.blackMs },
        )
        mutableGame.value = game
        game.start()
        saver = viewModelScope.launch {
            combine(game.session.state, game.state) { board, state -> board.moves.size to state.phase }.collect { (moves, phase) ->
                when {
                    phase == GamePhase.OVER -> clearSaved()
                    moves > 0 && phase != GamePhase.STARTING -> save(game)
                }
            }
        }
    }

    private fun save(game: BotGame) {
        val state = game.state.value
        val saved = ActiveGameSnapshot(
            botId = game.setup.bot.id,
            playerColor = game.setup.playerColor,
            rules = game.setup.rules,
            startFen = game.setup.startFen,
            moves = game.session.moves.map { it.uci },
            whiteMs = state.whiteMs,
            blackMs = state.blackMs,
            savedAt = clock(),
        )
        store.save(saved)
        snapshot.value = saved
    }

    private fun clearSaved() {
        store.clear()
        snapshot.value = null
    }

    override fun onCleared() {
        closeGame()
    }

    class Factory(
        private val engine: EngineService,
        private val openings: suspend () -> OpeningTree,
        private val games: GameRepository,
        private val puzzleStats: Flow<PuzzleStats>,
        private val store: ActiveGameStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(engine, openings, games, puzzleStats, store) as T
    }
}

private fun List<GameRecord>.botGamesNewestFirst(): List<GameRecord> =
    filter { it.id.startsWith("bot_") }.sortedByDescending { it.dateMillis }
