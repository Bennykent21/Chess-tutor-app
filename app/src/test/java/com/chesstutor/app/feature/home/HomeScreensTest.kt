package com.chesstutor.app.feature.home

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.chesstutor.app.domain.account.ThemeScore
import com.chesstutor.app.domain.bot.BotRoster
import com.chesstutor.app.domain.game.NextLesson
import com.chesstutor.app.domain.game.NextStep
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
import org.junit.Assert.assertTrue
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

    private val noActions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun homeShowsTheResumeCardQuickMatchAndMoreWaysToPlay() {
        var opened: String? = null
        var resumed = false
        var bots = false
        val home = HomeUiState(
            resume = ResumeCard(BotRoster.byId("juno")!!, moveCount = 14, playerColor = PieceColor.WHITE),
            recommendation = PlayRecommender.recommend(emptyList(), 800),
            dayStreak = 4,
            gamesPlayed = 3,
        ).let { it.copy(nextStep = NextStep.Bot(it.recommendation!!)) }
        val recommended = home.recommendation!!.bot
        compose.setContent {
            CtTheme(reduceMotion = true) {
                HomeScreen(
                    state = AppUiState(),
                    home = home,
                    actions = noActions.copy(onOpenBot = { opened = it }, onResume = { resumed = true }, onOpenBots = { bots = true }),
                )
            }
        }
        compose.onNodeWithText("vs Juno").assertExists()
        compose.onNodeWithText("Move 8, you play White").assertExists()
        compose.onNodeWithText("Quick match").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home.png")
        compose.onNodeWithText("vs Juno").performClick()
        assertTrue(resumed)
        compose.onNodeWithText("Play ${recommended.name}").performClick()
        assertEquals(recommended.id, opened)
        // D42: the bots live behind "Bots" in More ways to play, not on Home itself.
        compose.onNodeWithText("Meet the bots").assertDoesNotExist()
        compose.onNodeWithText("Bots").performScrollTo().performClick()
        assertTrue(bots)
    }

    @Test
    fun withNoGameWaitingTheCardsOfferTheNextLessonAndAWeakTheme() {
        var lesson: String? = null
        var theme: String? = null
        val home = HomeUiState(
            recommendation = PlayRecommender.recommend(emptyList(), 800),
            gamesPlayed = 3,
            nextLesson = NextLesson("o2", "Italian Game", "Castle early", number = 2, total = 5, started = false),
            nextStep = NextStep.Theme(ThemeScore("fork", attempts = 6, clean = 1)),
        )
        compose.setContent {
            CtTheme(reduceMotion = true) {
                HomeScreen(state = AppUiState(), home = home, actions = noActions.copy(onOpenLesson = { lesson = it }, onPractiseTheme = { theme = it }))
            }
        }
        compose.onNodeWithText("Italian Game").assertExists()
        compose.onNodeWithText("Lesson 2 of 5").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Home_next.png")
        compose.onNodeWithText("Castle early").performClick()
        assertEquals("o2", lesson)
        compose.onNodeWithText("Practise Fork").performClick()
        assertEquals("fork", theme)
    }

    @Test
    fun theBotsPageListsEveryBotYourOwnAndBuildAndWatch() {
        var opened: String? = null
        var built = false
        var watched = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotsScreen(
                    roster = BotRoster.bots,
                    myBots = emptyList(),
                    onOpenBot = { opened = it },
                    onNewBot = { built = true },
                    onWatch = { watched = true },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("My bots").assertDoesNotExist()
        compose.onNodeWithText("Build a bot").performClick()
        assertTrue(built)
        compose.onNodeWithText("Watch two").performClick()
        assertTrue(watched)
        compose.onNodeWithText("Pebble").performScrollTo().performClick()
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
        compose.onNodeWithText("Have a pawn, on me. It gets wild from here.").assertExists()
        // The first "Black" is the side to play; the second labels its openings as Black.
        compose.onAllNodesWithText("Black")[0].performClick()
        compose.onNodeWithText("10 min").performClick()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/BotSetup.png")
        compose.onNode(hasText("Play")).performClick()
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
        var reviewed: String? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PlayScreen(game, AppSettings(), onFeedback = {}, onRematch = { rematch = true }, onAnalyse = { _, _ -> }, onBack = {}, onReview = { reviewed = it })
            }
        }
        step()
        game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e2e4" })
        step(5_000)
        compose.onNodeWithText("Scandinavian Defense, in book").assertExists()
        compose.onNodeWithText("vs Wayne").assertExists()
        // Take the pawn: the captured pieces and the lead appear by the players.
        game.session.onMove(LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e4d5" })
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Taken: a pawn.", substring = true))[0].assertExists()
        step(5_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Play.png")

        val boardBefore = compose.onNodeWithContentDescription("Chess board", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Resign").performClick()
        compose.onNodeWithText("Resign this game?").assertExists()
        // The dialog's own Resign button (the bottom bar has one too).
        compose.onNode(hasText("Resign") and hasAnyAncestor(isDialog())).performClick()
        step()
        compose.onNodeWithText("Wayne won").assertExists()
        // D39: the result slides up over the screen; the board underneath stays put.
        assertEquals(boardBefore, compose.onNodeWithContentDescription("Chess board", substring = true).fetchSemanticsNode().boundsInRoot)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Play_result.png")
        compose.onNode(hasText("Review this game")).performClick()
        assertTrue("the saved game opens", reviewed!!.startsWith("bot_wayne_"))
        compose.onNode(hasText("Rematch") and hasClickAction()).performClick()
        assertEquals(true, rematch)
    }

    @Test
    fun theBuilderEditsTheDraft() {
        var draft by mutableStateOf(com.chesstutor.app.domain.bot.BotDraft())
        var saved = false
        var chose: com.chesstutor.app.core.PieceColor? = null
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BotBuilderScreen(
                    draft = draft,
                    onChange = { change -> draft = change(draft) },
                    onChooseOpenings = { chose = it },
                    onSave = { saved = true },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Save bot").assertIsNotEnabled()
        compose.onNodeWithText("Endgame grinder").performScrollTo().performClick()
        assertEquals(com.chesstutor.app.domain.bot.BotStyle.GRINDER, draft.style)
        compose.onNode(hasText("Black")).performScrollTo().performClick()
        assertEquals(com.chesstutor.app.core.PieceColor.BLACK, chose)
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
        compose.onNode(hasText("Search openings")).assertExists()
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

    @Test
    fun aPositionCanBeSetUpByTappingTheBoard() {
        var draft by mutableStateOf(PositionDraft(board = com.chesstutor.app.domain.game.SetupBoard.KINGS_ONLY))
        var started = false
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PositionSetupScreen(
                    draft = draft,
                    theme = AppSettings().boardTheme,
                    opponents = BotRoster.bots.take(4),
                    onChange = { draft = it(draft) },
                    onStart = { started = true },
                    onBack = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/PositionSetup.png")
        // A queen placed on e5 checks the black king while White is to move.
        draft = draft.copy(board = draft.board.place(com.chesstutor.app.core.Square.fromAlgebraic("e5"), draft.brush))
        compose.onNodeWithText("The side not to move is in check. Change who moves first.").assertExists()
        compose.onNodeWithText("Black to move").performClick()
        compose.onNode(hasText("Play from here")).performScrollTo().performClick()
        assertEquals(true, started)
        assertEquals("4k3/8/8/4Q3/8/8/8/4K3 b - - 0 1", draft.board.toFen())
    }

    @Test
    fun theRepertoirePickerTicksFamiliesAndLines() {
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        var picked by mutableStateOf(setOf("Italian Game"))
        compose.setContent {
            CtTheme(reduceMotion = true) {
                OpeningPickerScreen(
                    tree = tree,
                    onPick = {},
                    onBack = {},
                    title = "Openings as Black",
                    selection = OpeningSelection(picked, { picked = it }, com.chesstutor.app.domain.bot.BotOpeningChoices.black),
                )
            }
        }
        compose.onNodeWithText("Done, 1 chosen").assertExists()
        compose.onAllNodes(hasText("Caro-Kann Defense"))[0].performClick()
        assertEquals(true, "Caro-Kann Defense" in picked)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/RepertoirePicker.png")
        compose.onNodeWithText("Select all").performClick()
        assertEquals(tree.families.size, picked.size)
        compose.onNodeWithText("Clear").performClick()
        assertEquals(emptySet<String>(), picked)
        compose.onNode(hasSetTextAction()).performTextInput("Najdorf")
        compose.onAllNodes(hasText("B90 ·", substring = true))[0].performClick()
        assertEquals(setOf("Sicilian Defense: Najdorf Variation"), picked)
    }

    /** Regression (phone review): a bot's chat line used to push the board down and up. */
    @Test
    fun aChatLineFloatsOverTheBoardWithoutMovingIt() {
        val engine = FakeEngine()
        val game = BotGame(
            GameSetup(BotRoster.byId("wayne")!!.profile, PieceColor.WHITE), BotBrain(engine, Random(3)), engine,
            { OpeningTree.EMPTY }, InMemoryGameRepository(), scope, clock = { scheduler.currentTime },
            chatLevel = { com.chesstutor.app.domain.bot.ChatLevel.LOTS },
        )
        game.start()
        compose.setContent {
            CtTheme(reduceMotion = true) {
                PlayScreen(game, AppSettings(), onFeedback = {}, onRematch = {}, onAnalyse = { _, _ -> }, onBack = {})
            }
        }
        step()
        val greeting = game.state.value.chat!!.text
        val board = compose.onNodeWithContentDescription("Chess board", substring = true)
        val withChat = board.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(greeting).assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Play_chat.png")
        step(6_000)
        assertEquals("the line faded", null, game.state.value.chat)
        assertEquals("the board did not move", withChat, board.fetchSemanticsNode().boundsInRoot)

        game.session.onMove(com.chesstutor.app.core.LegalMoveGenerator.generateLegalMoves(game.session.game.position).first { it.uci == "e2e4" })
        step(5_000)
        game.state.value.chat?.let { line ->
            compose.onNodeWithText(line.text).performClick()
            compose.waitForIdle()
            assertEquals("a tap closes it", null, game.state.value.chat)
        }
        assertEquals(withChat, board.fetchSemanticsNode().boundsInRoot)
    }
}

