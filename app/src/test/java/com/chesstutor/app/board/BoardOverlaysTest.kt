package com.chesstutor.app.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTheme
import com.chesstutor.app.domain.Mascot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class BoardOverlaysTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun evalShareIsEvenWhenLevelAndFullAtMate() {
        assertEquals(0.5f, EvalMath.whiteShare(0, null), 0.001f)
        assertEquals(0.5f, EvalMath.whiteShare(null, null), 0.001f)
        assertEquals(1f, EvalMath.whiteShare(null, 3), 0f)
        assertEquals(0f, EvalMath.whiteShare(null, -2), 0f)
        assertTrue(EvalMath.whiteShare(100, null) in 0.55f..0.65f)
        assertTrue("never quite empty without mate", EvalMath.whiteShare(-5000, null) > 0f)
        assertEquals(EvalMath.whiteShare(250, null), 1f - EvalMath.whiteShare(-250, null), 0.001f)
    }

    @Test
    fun evalLabelsReadLikeAChessSite() {
        assertEquals("+0.3", EvalMath.label(34, null))
        assertEquals("-1.2", EvalMath.label(-118, null))
        assertEquals("0.0", EvalMath.label(4, null))
        assertEquals("M3", EvalMath.label(null, 3))
        assertEquals("-M2", EvalMath.label(null, -2))
        assertEquals("Black mates in 2", EvalMath.spoken(null, -2))
        assertEquals("Evaluation +0.3 pawns for White", EvalMath.spoken(34, null))
    }

    @Test
    fun clocksShowTenthsOnlyWhenTimeIsShort() {
        assertEquals("5:00", formatClock(300_000))
        assertEquals("1:02:03", formatClock(3_723_000))
        assertEquals("0:20", formatClock(20_000))
        assertEquals("19.9", formatClock(19_950))
        assertEquals("0.0", formatClock(-50))
    }

    @Test
    fun moveListNumbersMovesAndUsesSan() {
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5").map(Move::fromUci)
        val entries = moveListOf(Position.initial(), moves)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5"), entries.map { it.san })
        assertEquals(listOf("1.", null, "2.", null, "3."), entries.map { it.numberLabel })
        assertEquals(listOf(1, 2, 3, 4, 5), entries.map { it.ply })
    }

    @Test
    fun moveListStartingWithBlackToMoveUsesAnEllipsis() {
        val start = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val entries = moveListOf(start, listOf(Move.fromUci("e7e5"), Move.fromUci("g1f3")))
        assertEquals(listOf("1...", "2."), entries.map { it.numberLabel })
        assertEquals(listOf(2, 3), entries.map { it.ply })
    }

    @Test
    fun moveListStopsAtAnIllegalMove() {
        val entries = moveListOf(Position.initial(), listOf(Move.fromUci("e2e4"), Move.fromUci("e2e4")))
        assertEquals(1, entries.size)
    }

    @Test
    fun overlaysTogether() {
        val position = Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4")
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5").map(Move::fromUci)
        compose.setContent {
            CtTheme {
                CtScreenBackground {
                    Column(Modifier.padding(CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtCoachStrip(Mascot.PIP, "Follow the arrow: it shows the idea.", label = "You play White", modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BoardClock(millis = 181_000, running = false)
                            BoardClock(millis = 12_400, running = true)
                        }
                        BoardWithEvalBar(evalBar = { BoardEvalBar(centipawns = 45, mateIn = null, whiteAtBottom = true, modifier = it) }) { boardModifier ->
                            BoardView(
                                position = position,
                                theme = BoardTheme(),
                                modifier = boardModifier,
                                marks = BoardMarks(
                                    arrows = listOf(
                                        BoardArrow(Square.fromAlgebraic("f3"), Square.fromAlgebraic("g5"), MarkKind.BEST),
                                        BoardArrow(Square.fromAlgebraic("c2"), Square.fromAlgebraic("c3"), MarkKind.HINT),
                                        BoardArrow(Square.fromAlgebraic("b1"), Square.fromAlgebraic("c3"), MarkKind.ALTERNATIVE),
                                    ),
                                    rings = listOf(SquareRing(Square.fromAlgebraic("f7"))),
                                ),
                            )
                        }
                        BoardMoveList(entries = moveListOf(Position.initial(), moves), currentPly = 6)
                    }
                }
            }
        }
        compose.onNode(hasContentDescription("Evaluation +0.5 pawns for White")).assertExists()
        compose.onNode(hasContentDescription("Black, Bc5, current move")).assertExists()
        compose.onRoot().captureRoboImage()
    }
}
