package com.chesstutor.app.feature.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.repository.GameRepository
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotRepository
import com.chesstutor.app.domain.bot.ChatLevel
import com.chesstutor.app.domain.bot.MaiaSource
import com.chesstutor.app.domain.bot.CustomBot
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.bot.RosterBot
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.NextLesson
import com.chesstutor.app.domain.game.NextStep
import com.chesstutor.app.domain.game.NextStepRecommender
import com.chesstutor.app.domain.account.TrainingReport
import com.chesstutor.app.domain.learn.LessonLibrary
import com.chesstutor.app.domain.learn.LessonProgress
import com.chesstutor.app.feature.account.TrainingSources
import com.chesstutor.app.feature.learn.LearnHubModel
import com.chesstutor.app.domain.game.PlayRecommendation
import com.chesstutor.app.domain.game.PlayRecommender
import com.chesstutor.app.domain.game.SetupBoard
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.opening.BattleChoice
import com.chesstutor.app.domain.opening.BattleMode
import com.chesstutor.app.domain.opening.Opening
import com.chesstutor.app.domain.opening.OpeningBattles
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.puzzle.PuzzleStats
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.domain.account.GameReviewer
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

/** "Watch two bots": who plays White and who plays Black. */
data class WatchDraft(val whiteId: String = "kofi", val blackId: String = "ingrid")

/** The "From a position" page: the board being set up, the player's side and the opponent. */
data class PositionDraft(
    val board: SetupBoard = SetupBoard.START,
    val side: SideChoice = SideChoice.WHITE,
    val opponentId: String = "ziggy",
    /** The piece the next tap places; null erases. */
    val brush: Piece? = Piece(PieceType.QUEEN, PieceColor.WHITE),
)

/** An unfinished game shown on Home's Continue card. */
data class ResumeCard(
    val bot: RosterBot,
    val moveCount: Int,
    val playerColor: PieceColor,
    /** Where the game stands now, for the card's board picture. */
    val fen: String? = null,
)

/** The resume card for a saved game: its bot, how far it got and its current position. */
private fun ActiveGameSnapshot.toResumeCard(bot: RosterBot): ResumeCard =
    ResumeCard(bot, moves.size, playerColor, GameReviewer.positions(startFen, moves).lastOrNull()?.toFen())

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
    /** The next lesson in Learn, for the Continue card; null once every lesson is done. */
    val nextLesson: NextLesson? = null,
    /** The "Recommended" card: mistakes to fix, a weak puzzle theme or a bot. */
    val nextStep: NextStep? = null,
)

/** What Home reads from Learn, Puzzles and Account for its Continue and Recommended cards. */
class HomeLearning(
    val training: Flow<TrainingReport> = flowOf(TrainingSources.EMPTY_REPORT),
    val loadLessons: suspend () -> LessonLibrary = { LessonLibrary.EMPTY },
    val lessonProgress: Flow<Map<String, LessonProgress>> = flowOf(emptyMap()),
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
    /** Maia's human-like networks (D26); null plays every bot in the engine mode. */
    private val maia: MaiaSource? = null,
    learning: HomeLearning = HomeLearning(),
    /** Where the Training report is computed; tests pass their own dispatcher. */
    worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val playerRating = MutableStateFlow(800)
    private val dueMistakes = MutableStateFlow(0)

    /** The next lesson and the Training report, combined before they join the rest of Home. */
    private val learnSide: Flow<Pair<NextLesson?, TrainingReport>> = combine(
        flow { emit(runCatching { learning.loadLessons() }.getOrDefault(LessonLibrary.EMPTY)) },
        learning.lessonProgress,
        learning.training,
    ) { library, progress, report -> nextLessonOf(library, progress) to report }
        // The Training report counts every puzzle played: keep that off the main thread.
        .flowOn(worker)
    private val snapshot = MutableStateFlow(store.load())

    /** Loaded eagerly, so a saved game against one of them can be resumed at once. */
    val myBots: StateFlow<List<CustomBot>> = bots.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val home: StateFlow<HomeUiState> =
        combine(games.observeGames(), puzzleStats, snapshot, playerRating, myBots) { list, stats, saved, rating, mine ->
            HomeUiState(
                resume = saved?.let { s -> findBot(s.botId, mine)?.let { s.toResumeCard(it) } },
                recommendation = PlayRecommender.recommend(list.botGamesNewestFirst(), rating),
                dayStreak = stats.dayStreak,
                dailySolved = stats.solvedToday,
                gamesPlayed = list.count { it.id.startsWith("bot_") },
                myBots = mine,
            )
        }.combine(combine(learnSide, dueMistakes) { side, due -> side to due }) { state, (side, due) ->
            val (lesson, report) = side
            state.copy(
                nextLesson = lesson,
                nextStep = state.recommendation?.let { NextStepRecommender.recommend(due, report.weaknesses, it) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(resume = snapshot.value?.let { s ->
            BotRoster.byId(s.botId)?.let { s.toResumeCard(it) }
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

    private val chatLevel = MutableStateFlow(ChatLevel.SOME)

    /** How chatty bots are, from the player's settings. */
    fun setChatLevel(level: ChatLevel) {
        chatLevel.value = level
    }

    // ------------------------------------------------------------------ Watch two bots (review R6)

    private val mutableWatch = MutableStateFlow(WatchDraft())
    val watchDraft: StateFlow<WatchDraft> = mutableWatch.asStateFlow()

    fun updateWatch(change: (WatchDraft) -> WatchDraft) {
        mutableWatch.value = change(mutableWatch.value)
    }

    /** Starts a bot-vs-bot game: the second bot plays the "player" side, White. */
    fun startWatch(): Boolean {
        val draft = mutableWatch.value
        val white = findBot(draft.whiteId) ?: return false
        val black = findBot(draft.blackId) ?: return false
        launch(GameSetup(black.profile, PieceColor.WHITE, GameRules(takebacks = false, hints = false), watch = white.profile))
        return true
    }

    /** The player's rating, for picking a fair bot (it lives in the app-wide state). */
    fun setPlayerRating(rating: Int) {
        playerRating.value = rating
    }

    /** Mistakes due in Puzzles > Mistakes review (app-wide state), for the Recommended card. */
    fun setDueMistakes(count: Int) {
        dueMistakes.value = count
    }

    /** Quick match: the recommended bot, a random colour, the default rules. */
    fun quickMatch() {
        val bot = home.value.recommendation?.bot ?: BotRoster.closestTo(playerRating.value)
        start(bot, SideChoice.RANDOM, GameRules())
    }

    fun start(bot: RosterBot, side: SideChoice, rules: GameRules, startFen: String = Position.STARTING_FEN) {
        val color = when (side) {
            SideChoice.WHITE -> PieceColor.WHITE
            SideChoice.BLACK -> PieceColor.BLACK
            SideChoice.RANDOM -> if (random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
        }
        launch(GameSetup(bot.profile, color, rules, startFen))
    }

    /** The same bot and rules again, with colours swapped. */
    fun rematch() {
        val last = mutableGame.value ?: return
        // A battle is replayed as it was (drill it again); a game swaps colours.
        if (last.battle != null) launch(last.setup, battle = last.battle)
        else launch(last.setup.copy(playerColor = last.setup.playerColor.opposite()))
    }

    // ------------------------------------------------------------------ From a position (review R4)

    private val mutableExplore = MutableStateFlow(ExploreDraft())
    /** Explore's start page (D32). */
    val exploreDraft: StateFlow<ExploreDraft> = mutableExplore.asStateFlow()

    fun updateExplore(change: (ExploreDraft) -> ExploreDraft) {
        mutableExplore.value = change(mutableExplore.value)
    }

    private val mutablePosition = MutableStateFlow(PositionDraft())
    /** The position being set up. */
    val positionDraft: StateFlow<PositionDraft> = mutablePosition.asStateFlow()

    fun updatePosition(change: (PositionDraft) -> PositionDraft) {
        mutablePosition.value = change(mutablePosition.value)
    }

    /** Starts a game from the set-up position; false while it is not playable. */
    fun startFromPosition(): Boolean {
        val draft = mutablePosition.value
        if (draft.board.problem() != null) return false
        val bot = findBot(draft.opponentId) ?: return false
        start(bot, draft.side, GameRules(), startFen = draft.board.toFen())
        return true
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

    /**
     * Prepares Opening Battle from a lesson (plan Phase 4D): the opening named [lineName] in
     * the database, the lesson's [side] (or random when it suits both), "Play it" with the
     * line's exact replies. The line fills in once the database is read.
     */
    fun presetBattle(lineName: String, side: PieceColor?) {
        val sideChoice = when (side) {
            PieceColor.WHITE -> SideChoice.WHITE
            PieceColor.BLACK -> SideChoice.BLACK
            null -> SideChoice.RANDOM
        }
        mutableBattle.value = mutableBattle.value.copy(side = sideChoice, line = null, mode = BattleMode.PLAY, exactReplies = true)
        viewModelScope.launch {
            val tree = mutableTree.value ?: openings().also { mutableTree.value = it }
            val line = OpeningBattles.presetLine(tree, lineName) ?: return@launch
            mutableBattle.value = mutableBattle.value.copy(line = line)
        }
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
            if (game.state.value.phase != GamePhase.OVER && game.battle == null && game.setup.watch == null) save(game)
        }
        mutableGame.value = null
    }

    private fun launch(setup: GameSetup, resumeFrom: ActiveGameSnapshot? = null, battle: BattleChoice? = null) {
        closeGame()
        val game = BotGame(
            setup = setup,
            brain = BotBrain(engine, random, maia),
            engine = engine,
            openings = openings,
            games = games,
            scope = viewModelScope,
            clock = clock,
            resumeMoves = resumeFrom?.moves.orEmpty(),
            resumeClocks = resumeFrom?.let { it.whiteMs to it.blackMs },
            battle = battle,
            random = random,
            chatLevel = { chatLevel.value },
        )
        mutableGame.value = game
        game.start()
        // Battles are short practice and watched games are not the player's: neither is saved for resuming.
        if (battle != null || setup.watch != null) return
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
        private val maia: MaiaSource? = null,
        private val learning: HomeLearning = HomeLearning(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(engine, openings, games, bots, puzzleStats, store, maia = maia, learning = learning) as T
    }
}

/** The Continue card's lesson: the one Learn's own Continue card picks (started first, else the next new one). */
internal fun nextLessonOf(library: LessonLibrary, progress: Map<String, LessonProgress>): NextLesson? {
    val (course, lesson) = LearnHubModel.continueLesson(library, progress) ?: return null
    return NextLesson(
        lessonId = lesson.id,
        courseTitle = course.title,
        lessonTitle = lesson.title,
        number = course.lessons.indexOf(lesson) + 1,
        total = course.lessons.size,
        started = progress[lesson.id]?.started == true,
        fen = lesson.steps.firstOrNull()?.fen,
    )
}

private fun List<GameRecord>.botGamesNewestFirst(): List<GameRecord> =
    filter { it.id.startsWith("bot_") }.sortedByDescending { it.dateMillis }
