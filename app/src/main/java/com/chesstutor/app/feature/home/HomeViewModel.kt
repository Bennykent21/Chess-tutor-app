package com.chesstutor.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotRepository
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.PlayRecommendation
import com.chesstutor.app.domain.game.PlayRecommender
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.BattleChoice
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.domain.opening.Opening
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

/** Opening Battle's setup page (plan 6.1): side, opening line, mode, replies and opponent. */
data class BattleDraft(
    val side: SideChoice = SideChoice.RANDOM,
    val line: Opening? = null,
    val mode: BattleMode = BattleMode.PLAY,
    val exactReplies: Boolean = true,
    val opponentId: String = "ziggy",
) {
    companion object {
        /** Opponents offered for a battle, easiest first. */
        val OPPONENTS = listOf("juno", "ziggy", "tessa", "ingrid", "bolt")
    }
}

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
    /** The player's own bots, oldest first. */
    val myBots: List<CustomBot> = emptyList(),
)

/**
 * The Home tab's view model: the roster, the resume and recommended cards, and the game being
 * played. Unfinished games are saved after every move ([ActiveGameStore]) and cleared at the end.
 */
class HomeViewModel(
    private val engine: EngineService,
    private val openings: suspend () -> OpeningTree,
    private val games: GameRepository,
    private val bots: BotRepository,
    puzzleStats: Flow<PuzzleStats>,
    private val store: ActiveGameStore,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val playerRating = MutableStateFlow(800)
    private val snapshot = MutableStateFlow(store.load())

    /** Loaded eagerly, so a saved game against one of them can be resumed at once. */
    val myBots: StateFlow<List<CustomBot>> = bots.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val home: StateFlow<HomeUiState> =
        combine(games.observeGames(), puzzleStats, snapshot, playerRating, myBots) { list, stats, saved, rating, mine ->
            HomeUiState(
                resume = saved?.let { s -> findBot(s.botId, mine)?.let { ResumeCard(it, s.moves.size, s.playerColor) } },
                recommendation = PlayRecommender.recommend(list.botGamesNewestFirst(), rating),
                dayStreak = stats.dayStreak,
                dailySolved = stats.solvedToday,
                gamesPlayed = list.count { it.id.startsWith("bot_") },
                myBots = mine,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(resume = snapshot.value?.let { s ->
            BotRoster.byId(s.botId)?.let { ResumeCard(it, s.moves.size, s.playerColor) }
        }))

    /** A roster bot or one of the player's own, by id. */
    fun findBot(id: String): RosterBot? = findBot(id, myBots.value)

    private fun findBot(id: String, mine: List<CustomBot>): RosterBot? =
        BotRoster.byId(id) ?: customBot(id, mine)?.asRosterBot()

    /** The rules a bot's page starts with: the player's own bots bring their saved rules. */
    fun rulesFor(id: String): GameRules = customBot(id, myBots.value)?.rules ?: GameRules()

    /** A just-saved bot counts before Room reports it back, so its page can open at once. */
    private var lastSaved: CustomBot? = null

    private fun customBot(id: String, mine: List<CustomBot>): CustomBot? =
        mine.firstOrNull { it.id == id } ?: lastSaved?.takeIf { it.id == id }

    // ------------------------------------------------------------------ bot builder (3D)

    private val mutableDraft = MutableStateFlow(BotDraft())
    /** The bot being built or edited. */
    val draft: StateFlow<BotDraft> = mutableDraft.asStateFlow()

    fun newBot() {
        mutableDraft.value = BotDraft()
    }

    fun editBot(id: String) {
        myBots.value.firstOrNull { it.id == id }?.let { mutableDraft.value = BotDraft.from(it) }
    }

    fun updateDraft(change: (BotDraft) -> BotDraft) {
        mutableDraft.value = change(mutableDraft.value)
    }

    /** Saves the draft and returns the bot's id, or null if it cannot be saved yet. */
    fun saveDraft(): String? {
        val current = mutableDraft.value
        if (!current.canSave) return null
        val now = clock()
        val bot = current.toCustomBot(newId = "${CustomBot.ID_PREFIX}$now${random.nextInt(1000)}", now = now)
        lastSaved = bot
        viewModelScope.launch { bots.save(bot) }
        mutableDraft.value = BotDraft.from(bot)
        return bot.id
    }

    fun deleteBot(id: String) {
        if (lastSaved?.id == id) lastSaved = null
        viewModelScope.launch { bots.delete(id) }
        if (snapshot.value?.botId == id) clearSaved()
    }

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
        val last = mutableGame.value ?: return
        // A battle is replayed as it was (drill it again); a game swaps colours.
        if (last.battle != null) launch(last.setup, battle = last.battle)
        else launch(last.setup.copy(playerColor = last.setup.playerColor.opposite()))
    }

    // ------------------------------------------------------------------ Opening Battle (3E)

    private val mutableBattle = MutableStateFlow(BattleDraft())
    /** The battle being set up. */
    val battle: StateFlow<BattleDraft> = mutableBattle.asStateFlow()

    private val mutableTree = MutableStateFlow<OpeningTree?>(null)
    /** The opening database for the picker; null until [loadOpenings] has read it. */
    val openingTree: StateFlow<OpeningTree?> = mutableTree.asStateFlow()

    fun loadOpenings() {
        if (mutableTree.value != null) return
        viewModelScope.launch { mutableTree.value = openings() }
    }

    fun updateBattle(change: (BattleDraft) -> BattleDraft) {
        mutableBattle.value = change(mutableBattle.value)
    }

    /** Starts the battle; false while no opening is picked. */
    fun startBattle(): Boolean {
        val draft = mutableBattle.value
        val line = draft.line ?: return false
        val bot = BotRoster.byId(draft.opponentId) ?: return false
        val color = when (draft.side) {
            SideChoice.WHITE -> PieceColor.WHITE
            SideChoice.BLACK -> PieceColor.BLACK
            SideChoice.RANDOM -> if (random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }
        // Drills undo wrong moves themselves, so they have no takebacks and no clock.
        val rules = GameRules(takebacks = draft.mode != BattleMode.DRILL, hints = true)
        launch(GameSetup(bot.profile, color, rules), battle = BattleChoice(line, draft.mode, draft.exactReplies))
        return true
    }

    /** Picks up the saved unfinished game. */
    fun resume() {
        val saved = snapshot.value ?: return
        val bot = findBot(saved.botId)
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
            if (game.state.value.phase != GamePhase.OVER && game.battle == null) save(game)
        }
        mutableGame.value = null
    }

    private fun launch(setup: GameSetup, resumeFrom: ActiveGameSnapshot? = null, battle: BattleChoice? = null) {
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
            battle = battle,
            random = random,
        )
        mutableGame.value = game
        game.start()
        // Battles are short practice: they are not saved for resuming.
        if (battle != null) return
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
        private val bots: BotRepository,
        private val puzzleStats: Flow<PuzzleStats>,
        private val store: ActiveGameStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(engine, openings, games, bots, puzzleStats, store) as T
    }
}

private fun List<GameRecord>.botGamesNewestFirst(): List<GameRecord> =
    filter { it.id.startsWith("bot_") }.sortedByDescending { it.dateMillis }
