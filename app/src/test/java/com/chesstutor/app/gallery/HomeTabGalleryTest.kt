package com.chesstutor.app.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.board.PieceSet
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.data.update.UpdateState
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotDraft
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.FriendSetup
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.NextStep
import com.chesstutor.app.domain.game.PlayRecommender
import com.chesstutor.app.domain.game.PlayerClock
import com.chesstutor.app.domain.game.TimeControl
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.domain.update.AppRelease
import com.chesstutor.app.domain.update.AppUpdates
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import com.chesstutor.app.feature.analysis.AnalysisScreen
import com.chesstutor.app.feature.friend.FriendDraft
import com.chesstutor.app.feature.friend.FriendPlayActions
import com.chesstutor.app.feature.friend.FriendPlayScreen
import com.chesstutor.app.feature.friend.FriendSetupScreen
import com.chesstutor.app.feature.friend.NearbyLobbyScreen
import com.chesstutor.app.feature.friend.NearbyStatus
import com.chesstutor.app.feature.home.BattleDraft
import com.chesstutor.app.feature.home.BattleSetupScreen
import com.chesstutor.app.feature.home.BotBuilderScreen
import com.chesstutor.app.feature.home.BotSetupScreen
import com.chesstutor.app.feature.home.BotsScreen
import com.chesstutor.app.feature.home.CustomGameScreen
import com.chesstutor.app.feature.home.PlayMenuActions
import com.chesstutor.app.feature.home.PlayMenuScreen
import com.chesstutor.app.feature.home.ExploreDraft
import com.chesstutor.app.feature.home.ExploreSetupScreen
import com.chesstutor.app.feature.home.FirstRunScreen
import com.chesstutor.app.feature.home.HomeActions
import com.chesstutor.app.feature.home.HomeScreen
import com.chesstutor.app.feature.home.HomeUiState
import com.chesstutor.app.feature.home.OpeningPickerScreen
import com.chesstutor.app.feature.home.PositionDraft
import com.chesstutor.app.feature.home.PositionSetupScreen
import com.chesstutor.app.feature.home.ResumeCard
import com.chesstutor.app.feature.home.WatchDraft
import com.chesstutor.app.feature.home.WatchSetupScreen
import com.chesstutor.app.feature.play.BotGame
import com.chesstutor.app.feature.play.FriendGame
import com.chesstutor.app.feature.play.PlayScreen
import com.chesstutor.app.feature.update.WhatsNewScreen
import com.chesstutor.app.viewmodel.AppUiState
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen of the Home tab at phone size, light and dark, for the visual pass (Phase 14B).
 * Pictures land in `build/outputs/roborazzi/gallery/home/` when run with `recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class HomeTabGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
    private val tree by lazy { File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream) }

    @After
    fun tearDown() = scope.cancel()

    private var dark by mutableStateOf(false)

    /** Shows [content] in light, then dark, and saves both. */
    private fun shoot(name: String, prepare: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = if (dark) Appearance.DARK else Appearance.LIGHT, reduceMotion = true) { content() }
        }
        step()
        prepare()
        step()
        captureScreenRoboImage("$DIR/${name}_light.png")
        dark = true
        step()
        captureScreenRoboImage("$DIR/${name}_dark.png")
    }

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    private class FakeEngine : EngineService {
        override val status: StateFlow<EngineStatus> = MutableStateFlow(EngineStatus())
        override suspend fun bestMove(fen: String, movetimeMs: Int): MoveChoice = MoveChoice.fromUci("g1f3")
        override suspend fun evaluate(fen: String, movetimeMs: Int): EngineEvaluation = EngineEvaluation(centipawns = 40)
        override suspend fun analyse(fen: String, lines: Int, movetimeMs: Int): List<EngineLine> {
            val first = LegalMoveGenerator.generateLegalMoves(Position.fromFen(fen)).firstOrNull() ?: return emptyList()
            return listOf(EngineLine(1, listOf(first.uci), 40))
        }
        override suspend fun botMove(fen: String, elo: Int): MoveChoice? = null
        override suspend fun healthCheck(): EngineHealthReport? = null
    }

    private val noHome = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun home() {
        val home = HomeUiState(
            resume = ResumeCard(BotRoster.byId("juno")!!, moveCount = 14, playerColor = PieceColor.WHITE),
            recommendation = PlayRecommender.recommend(emptyList(), 800),
            dayStreak = 4,
            gamesPlayed = 3,
        ).let { it.copy(nextStep = NextStep.Bot(it.recommendation!!)) }
        shoot("01_home") { HomeScreen(state = AppUiState(assessmentState = "COMPLETE"), home = home, actions = noHome) }
    }

    @Test
    fun firstRun() = shoot("02_first_run") { FirstRunScreen(mascot = Mascot.PIP, pieceSet = PieceSet.DEFAULT, onName = {}, onStartingLevel = {}) }

    @Test
    fun bots() = shoot("03_bots") { BotsScreen(roster = BotRoster.bots, myBots = emptyList(), onOpenBot = {}, onNewBot = {}, onBack = {}) }

    @Test
    fun playMenu() = shoot("03b_play") { PlayMenuScreen(PlayMenuActions(), onBack = {}) }

    @Test
    fun customGame() = shoot("03c_custom_game") { CustomGameScreen(theme = BoardTheme(), onStart = { _, _, _, _ -> }, onBack = {}) }

    @Test
    fun chess960() = shoot("03d_chess960") { CustomGameScreen(theme = BoardTheme(), startIn960 = true, onStart = { _, _, _, _ -> }, onBack = {}) }

    @Test
    fun botSetup() = shoot("04_bot_page") { BotSetupScreen(BotRoster.byId("marlo")!!, onPlay = { _, _, _ -> }, onBack = {}) }

    @Test
    fun botBuilder() = shoot("05_bot_builder") { BotBuilderScreen(draft = BotDraft(), onChange = {}, onSave = {}, onBack = {}) }

    @Test
    fun play() {
        val engine = FakeEngine()
        val game = BotGame(
            GameSetup(BotRoster.byId("wayne")!!.profile, PieceColor.WHITE, GameRules(timeControl = TimeControl.RAPID_10, evalBar = true)),
            BotBrain(engine, Random(3)), engine, { tree }, InMemoryGameRepository(), scope, clock = { scheduler.currentTime },
        )
        game.start()
        shoot("06_play", prepare = {
            game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e2e4" })
            step(5_000)
            game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e4d5" })
            step(5_000)
        }) {
            PlayScreen(game, AppSettings(), onFeedback = {}, onRematch = {}, onAnalyse = { _, _ -> }, onBack = {}, onReview = {})
        }
        dark = false
        compose.onNodeWithText("Resign").performClick()
        compose.onNode(hasText("Resign") and hasAnyAncestor(isDialog())).performClick()
        step()
        captureScreenRoboImage("$DIR/07_play_result_light.png")
        dark = true
        step()
        captureScreenRoboImage("$DIR/07_play_result_dark.png")
    }

    @Test
    fun openingPicker() = shoot("08_opening_picker") { OpeningPickerScreen(tree = tree, onPick = {}, onBack = {}) }

    @Test
    fun battleSetup() = shoot("09_opening_battle") {
        BattleSetupScreen(
            draft = BattleDraft(line = tree.openings.first { it.name == "Sicilian Defense: Najdorf Variation" }),
            onChange = {}, onPickOpening = {}, onStart = {}, onBack = {},
        )
    }

    @Test
    fun positionSetup() = shoot("10_from_a_position") {
        PositionSetupScreen(draft = PositionDraft(), theme = AppSettings().boardTheme, opponents = BotRoster.bots, onChange = {}, onStart = {}, onBack = {})
    }

    @Test
    fun exploreSetup() = shoot("11_explore_setup") { ExploreSetupScreen(draft = ExploreDraft(), theme = AppSettings().boardTheme, onChange = {}, onStart = {}, onBack = {}) }

    @Test
    fun explore() = shoot("12_explore") {
        AnalysisScreen(startFen = Position.STARTING_FEN, settings = AppSettings(), line = listOf("e2e4", "c7c5", "g1f3"), engine = null, onFeedback = {}, onBack = {})
    }

    @Test
    fun watchSetup() = shoot("13_watch_setup") {
        WatchSetupScreen(draft = WatchDraft(whiteId = "juno", blackId = "wayne"), bots = BotRoster.bots, onChange = {}, onStart = {}, onBack = {})
    }

    @Test
    fun friendSetup() = shoot("14_friend_setup") {
        FriendSetupScreen(draft = FriendDraft(friendName = "Ada"), ownerName = "Sam", theme = BoardTheme(), onChange = {}, onPickOpening = {}, onSetPosition = {}, onStart = {}, onBack = {})
    }

    @Test
    fun friendGame() {
        val friendScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined + SupervisorJob())
        val game = FriendGame(FriendSetup("Sam", "Ada", whiteClock = PlayerClock.of(10), blackClock = PlayerClock.of(10)), { tree }, InMemoryGameRepository(), friendScope, clock = { 0L })
        game.start()
        for (uci in listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")) {
            game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == uci })
        }
        shoot("15_friend_game") { FriendPlayScreen(game, AppSettings(), FriendPlayActions(onFeedback = {}, onRematch = {}, onReview = {}, onAnalyse = { _, _ -> }, onBack = {})) }
        friendScope.cancel()
    }

    @Test
    fun nearbyLobby() = shoot("16_nearby") { NearbyLobbyScreen(NearbyStatus.Hosting("KMQX", "192.168.1.5:41234"), joining = false, onJoin = {}, onCancel = {}) }

    @Test
    fun whatsNew() {
        val notes = AppUpdates.parseNotes("1.1.3", "### Everywhere\n\n- A cleaner look on every screen.\n\n### Game review\n\n- Accuracy closer to chess.com.")
        val release = AppRelease("1.1.3", "https://example.org/a.apk", 95_379_129L, null, listOf(notes))
        shoot("17_whats_new") { WhatsNewScreen(UpdateState.Available(release), onUpdate = {}, onAllow = {}, onBack = {}) }
    }

    private companion object {
        const val DIR = "build/outputs/roborazzi/gallery/home"
    }
}
