package com.chesstutor.app.feature.train

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.board.BoardTheme
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.AppTheme
import com.chesstutor.app.design.Appearance
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.opening.InMemoryMyLineStore
import com.chesstutor.app.domain.opening.MyLine
import com.chesstutor.app.domain.opening.OpeningTree
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Your openings (Phase 12): building, naming and keeping a line, and its pages at phone size. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class MyLinesTest {

    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val friedLiver = MyLine("1", "Fried Liver", PieceColor.WHITE, "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 d7d5 e4d5 f6d5 g5f7".split(' '))

    @Test
    fun aLineIsBuiltWithBothSidesNamedByTheBookAndKept() = runTest(dispatcher) {
        val store = InMemoryMyLineStore(listOf(friedLiver.copy(id = "0", name = "Italian Game")))
        var ids = 0
        val tree = File("src/main/assets/openings.tsv.gz").inputStream().use(OpeningTree::fromStream)
        val vm = MyLinesViewModel(store, { tree }, newId = { "id${ids++}" })
        advanceUntilIdle()
        vm.newLine()
        val session = vm.draft.value!!.session
        "e2e4 e7e5 g1f3 b8c6 f1c4".split(' ').forEach { uci ->
            session.onMove(LegalMoveGenerator.generateLegalMoves(session.game.position).first { it.uci == uci })
        }
        assertEquals("Italian Game", vm.draft.value!!.bookName)
        vm.undo()
        assertEquals(4, vm.draft.value!!.moves.size)
        vm.setSide(PieceColor.BLACK)
        val saved = vm.save("Italian Game")!!
        assertEquals("a name already used gets a number", "Italian Game 2", saved.name)
        assertEquals(PieceColor.BLACK, saved.side)
        assertEquals(2, store.lines().value.size)
        assertNull(vm.draft.value)
        assertEquals("Mine: Italian Game 2", saved.toTrainerLine().name)
        assertEquals("1.e4 e5 2.Nf3 Nc6", sanLine(saved.moves))
    }

    @Test
    fun theList() {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                MyLinesScreen(listOf(friedLiver, friedLiver.copy(id = "2", name = "Caro-Kann: my Advance", side = PieceColor.BLACK, moves = "e2e4 c7c6 d2d4 d7d5 e4e5 c8f5".split(' '))), BoardTheme(), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("As White · 6 moves").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_my_lines.png")
    }

    @Test
    fun aLinesPageInTheDark() {
        var played = false
        compose.setContent {
            CtTheme(theme = AppTheme.PARCHMENT, appearance = Appearance.DARK, reduceMotion = true) {
                MyLineScreen(friedLiver, BoardTheme(), onDrill = {}, onPlay = { played = true }, onDelete = {}, onBack = {})
            }
        }
        compose.onNodeWithText("1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.Ng5 d5 5.exd5 Nxd5 6.Nxf7").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_my_line_dark.png")
        compose.onNodeWithText("Play it out vs Stockfish").performClick()
        assertTrue(played)
    }

    @Test
    fun theBuilderOnTheBoardScreen() = runTest(dispatcher) {
        val vm = MyLinesViewModel(InMemoryMyLineStore(), { OpeningTree.EMPTY })
        vm.newLine()
        val draft = vm.draft.value
        compose.setContent {
            CtTheme(reduceMotion = true) {
                LineBuilderScreen(draft, AppSettings(), onSide = {}, onUndo = {}, onSave = {}, onFeedback = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Play the moves of both sides.").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/train_line_builder.png")
    }

    @Test
    fun namesStayApart() {
        assertEquals("Mine", MyLine.freeName("Mine", emptyList()))
        assertEquals("Mine 3", MyLine.freeName(" Mine ", listOf("Mine", "Mine 2")))
        assertEquals("My line", MyLine.freeName("  ", emptyList()))
    }
}
