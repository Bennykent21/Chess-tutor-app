package com.chesstutor.app.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.engine.EngineLine
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1100dp-xxhdpi")
class AnalysisPanelsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sq(name: String) = Square.fromAlgebraic(name)

    @Test
    fun engineLinesReadAsNumberedSan() {
        assertEquals("1. e4 e5 2. Nf3", lineSan(Position.initial(), listOf("e2e4", "e7e5", "g1f3")))
        val blackToMove = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        assertEquals("1... c5 2. Nf3", lineSan(blackToMove, listOf("c7c5", "g1f3")))
        assertEquals("stops at an illegal move", "1. e4", lineSan(Position.initial(), listOf("e2e4", "e2e4")))
        assertEquals("1. e4", lineSan(Position.initial(), listOf("e2e4", "e7e5"), maxPlies = 1))
    }

    @Test
    fun drawingTheSameArrowOrRingTwiceErasesIt() {
        val arrow = BoardArrow(sq("e2"), sq("e4"), MarkKind.USER)
        val once = toggleArrow(emptyList(), arrow)
        assertEquals(listOf(arrow), once)
        assertEquals(emptyList<BoardArrow>(), toggleArrow(once, arrow.copy(kind = MarkKind.BEST)))
        val ring = SquareRing(sq("f7"))
        assertEquals(emptyList<SquareRing>(), toggleRing(toggleRing(emptyList(), ring), ring))
    }

    @Test
    fun drawModeDrawsInsteadOfMoving() {
        val session = AnalysisSession()
        val arrows = mutableListOf<Pair<Square, Square>>()
        val rings = mutableListOf<Square>()
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BoardHost(
                    session = session,
                    theme = BoardTheme(),
                    modifier = Modifier.width(320.dp),
                    drawMode = true,
                    onDrawArrow = { from, to -> arrows += from to to },
                    onDrawRing = { rings += it },
                )
            }
        }
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput {
            val step = width / 8f
            fun centre(file: Int, rankFromTop: Int) = Offset((file + 0.5f) * step, (rankFromTop + 0.5f) * step)
            down(centre(4, 6)) // e2
            moveTo(centre(4, 5))
            moveTo(centre(4, 4)) // e4
            up()
            click(centre(5, 1)) // f7
        }
        compose.waitForIdle()
        assertEquals(listOf(sq("e2") to sq("e4")), arrows)
        assertEquals(listOf(sq("f7")), rings)
        assertNull("no move was played", session.state.value.lastMove)
    }

    @Test
    fun analysisPanelsTogether() {
        val session = AnalysisSession()
        listOf("e2e4", "e7e5", "g1f3", "b8c6").forEach { session.onMove(Move.fromUci(it)) }
        session.back()
        session.back()
        session.onMove(Move.fromUci("f1c4"))
        session.onMove(Move.fromUci("g8f6"))
        val position = session.state.value.position
        val lines = listOf(
            EngineLine(rank = 1, moves = listOf("d2d3", "f8c5", "c2c3"), centipawns = 31, depth = 18),
            EngineLine(rank = 2, moves = listOf("b1c3", "f8b4"), centipawns = 12, depth = 18),
            EngineLine(rank = 3, moves = listOf("g1f3", "b8c6"), centipawns = -40, depth = 18),
        )
        var selected = -1
        compose.setContent {
            CtTheme {
                CtScreenBackground {
                    Column(Modifier.padding(CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        EngineLinesPanel(position = position, lines = lines, engineName = "Stockfish", onPlayLine = {})
                        AnalysisToolbar(session.view.value, drawing = true, onStart = {}, onBack = {}, onForward = {}, onEnd = {}, onToggleDrawing = {})
                        AnalysisMoveList(view = session.view.value, onSelect = { selected = it })
                    }
                }
            }
        }
        compose.onNode(hasContentDescription("Line 1: Evaluation +0.3 pawns for White. 3. d3 Bc5 4. c3")).assertExists()
        compose.onNode(hasContentDescription("2. Bc4 side line")).performClick()
        assertEquals(session.view.value.tokens.filterIsInstance<NotationToken.MoveToken>().first { it.san == "Bc4" }.nodeId, selected)
        compose.onRoot().captureRoboImage()
    }
}
