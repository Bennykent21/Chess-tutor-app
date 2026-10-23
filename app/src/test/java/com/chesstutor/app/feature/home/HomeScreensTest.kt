package com.chesstutor.app.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.repository.InMemoryGameRepository
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.MoveChoice
import com.chesstutor.app.domain.bot.BotBrain
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.GameRules
import com.chesstutor.app.domain.game.GameSetup
import com.chesstutor.app.domain.game.PlayRecommender
import com.chesstutor.app.domain.game.SideChoice
import com.chesstutor.app.domain.game.TimeControl
import com.chesstutor.app.domain.opening.OpeningTree
import com.chesstutor.app.engine.EngineEvaluation
import com.chesstutor.app.engine.EngineHealthReport
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import com.chesstutor.app.feature.play.BotGame
import com.chesstutor.app.feature.play.PlayScreen
import com.chesstutor.app.viewmodel.AppUiState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Home, a bot's page and a game (step 3C), with screenshots for review. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class HomeScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

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

    private fun step(ms: Long = 0) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    private val noActions = HomeActions({}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun homeShowsTheRosterAndTheResumeCard() {
        var opened: String? = null
        val home = HomeUiState(
            resume = ResumeCard(BotRoster.byId("juno")!!, moveCount = 14, playerColor = PieceColor.WHITE),
            recommendation = PlayRecommender.recommend(emptyList(), 800),
            dayStreak = 4,
            gamesPlayed = 3,
        )
        compose.setContent {
            CtTheme(reduceMotion = true) {
                HomeScreen(state = AppUiState(), home = home, actions = noActions.copy(onOpenBot = { opened = it }))
            }
        }
        compose.onNodeWithText("Your game vs Juno").assertExists()
        compose.onNodeWithText("Move 8 · you play White").assertExists()
        compose.onNodeWithText("Your game against Juno is waiting. Shall we finish it?").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home.png")
        compose.onNode(hasText("Pebble")).performScrollTo().performClick()
        assertEquals("pebble", opened)
    }

    @Test
    fun aBotPageSetsUpTheGame() {
        var chosen: Pair<SideChoice, GameRules>? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotSetupScreen(BotRoster.byId("marlo")!!, onPlay = { side, rules -> chosen = side to rules }, onBack = {})
            }
        }
        compose.onNodeWithText("Gives a pawn away for a wild game.").assertExists()
        compose.onNodeWithText("Black").performClick()
        compose.onNodeWithText("10 min").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/BotSetup.png")
        compose.onNode(hasText("Play Marlo")).performScrollTo().performClick()
        assertEquals(SideChoice.BLACK, chosen?.first)
        assertEquals(TimeControl.RAPID_10, chosen?.second?.timeControl)
    }

    @Test
    fun aGameShowsBothPlayersTheBoardAndTheResult() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        val engine = FakeEngine()
        val game = BotGame(
            GameSetup(BotRoster.byId("wayne")!!.profile, PieceColor.WHITE, GameRules(timeControl = TimeControl.RAPID_10, evalBar = true)),
            BotBrain(engine, Random(3)), engine, { tree }, InMemoryGameRepository(), scope, clock = { scheduler.currentTime },
        )
        game.start()
        var rematch = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PlayScreen(game, AppSettings(), onFeedback = {}, onRematch = { rematch = true }, onAnalyse = { _, _ -> }, onBack = {})
            }
        }
        step()
        game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e2e4" })
        step(5_000)
        compose.onNodeWithText("Scandinavian Defense · in book").assertExists()
        compose.onNodeWithText("vs Wayne").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Play.png")

        compose.onNodeWithText("Resign").performClick()
        compose.onNodeWithText("Resign this game?").assertExists()
        compose.onNode(hasText("Resign") and !hasText("Resign this game?")).performClick()
        step()
        compose.onNodeWithText("Wayne won").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Play_result.png")
        compose.onNode(hasText("Rematch")).performScrollTo().performClick()
        assertEquals(true, rematch)
    }

    @Test
    fun theBuilderEditsTheDraft() {
        var draft by mutableStateOf(com.chesstutor.app.domain.bot.BotDraft())
        var saved = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotBuilderScreen(
                    draft = draft,
                    onChange = { change -> draft = change(draft) },
                    onSave = { saved = true },
                    onDelete = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Give your bot a name to save it.").assertExists()
        compose.onNodeWithText("Endgame grinder").performScrollTo().performClick()
        compose.onNodeWithText("Sicilian Defense").performScrollTo().performClick()
        assertEquals(com.chesstutor.app.domain.bot.BotStyle.GRINDER, draft.style)
        assertEquals(true, "Sicilian Defense" in draft.black)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/BotBuilder.png")
        assertEquals(false, saved)
    }

    @Test
    fun theOpeningPickerSearchesAndPicksALine() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        var picked: com.chesstutor.app.domain.opening.Opening? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                OpeningPickerScreen(tree = tree, onPick = { picked = it }, onBack = {})
            }
        }
        compose.onNodeWithText("Sicilian Defense").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/OpeningPicker.png")
        compose.onNode(hasText("Search")).assertExists()
        // Searching narrows the list to the one family and opens it at the matching lines.
        compose.onNode(hasSetTextAction()).performTextInput("Najdorf")
        compose.onAllNodes(hasText("Najdorf Variation", substring = true))[0].assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/OpeningPicker_open.png")
        compose.onAllNodes(hasText("B90 ·", substring = true))[0].performClick()
        assertEquals("Sicilian Defense: Najdorf Variation", picked?.name)
    }

    @Test
    fun theBattlePageNeedsAnOpeningBeforeItStarts() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        var draft by mutableStateOf(BattleDraft())
        var started = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BattleSetupScreen(draft = draft, onChange = { draft = it(draft) }, onPickOpening = {}, onStart = { started = true }, onBack = {})
            }
        }
        compose.onNodeWithText("Choose an opening to start.").assertExists()
        draft = draft.copy(line = tree.openings.first { it.name == "Sicilian Defense: Najdorf Variation" })
        compose.onNodeWithText("Drill it").performClick()
        compose.onNodeWithText("Any Sicilian Defense").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/BattleSetup.png")
        compose.onNode(hasText("Start")).performScrollTo().performClick()
        assertEquals(true, started)
        assertEquals(com.chesstutor.app.domain.opening.BattleMode.DRILL, draft.mode)
    }
}

