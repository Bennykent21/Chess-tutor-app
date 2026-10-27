package com.chesstutor.app.board

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The interactive board (step 1B), driven through touch and through TalkBack actions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class BoardHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val feedback = mutableListOf<BoardFeedback>()

    private fun show(session: BoardSession, autoQueen: Boolean = false, whiteAtBottom: Boolean = true) {
        compose.setContent {
            CtTheme(reduceMotion = true) {
                BoardHost(
                    session = session,
                    theme = BoardTheme(),
                    modifier = Modifier.width(320.dp),
                    whiteAtBottom = whiteAtBottom,
                    autoQueen = autoQueen,
                    onFeedback = { feedback += it },
                )
            }
        }
    }

    private fun square(description: String) =
        compose.onNode(hasContentDescription(description, substring = true))

    /** Centre of [name] in board pixels, for a board [size] pixels wide. */
    private fun centre(name: String, size: Float, whiteAtBottom: Boolean = true): Offset {
        val cell = BoardGeometry.cellOf(Square.fromAlgebraic(name), whiteAtBottom)
        val step = size / 8f
        return Offset((cell.col + 0.5f) * step, (cell.row + 0.5f) * step)
    }

    @Test
    fun talkBackUsersPlayByActivatingTwoSquares() {
        val session = FreePlaySession()
        show(session)
        square("e2, white pawn").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasContentDescription("e4, empty, legal move")).assertExists()
        square("e4, empty").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(Move.fromUci("e2e4"), session.state.value.lastMove)
        assertEquals(listOf(BoardFeedback(BoardSound.MOVE, HapticPattern.TICK)), feedback)
        square("e4, white pawn").assertExists()
    }

    @Test
    fun dragAndDropPlaysAMove() {
        val session = FreePlaySession()
        show(session)
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput {
            val size = width.toFloat()
            down(centre("g1", size))
            moveTo(centre("g2", size))
            moveTo(centre("f3", size))
            up()
        }
        compose.waitForIdle()
        assertEquals(Move.fromUci("g1f3"), session.state.value.lastMove)
    }

    @Test
    fun anIllegalDropSnapsBackWithABuzzOnly() {
        val session = FreePlaySession()
        show(session)
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput {
            val size = width.toFloat()
            down(centre("g1", size))
            moveTo(centre("g3", size))
            up()
        }
        compose.waitForIdle()
        assertEquals(null, session.state.value.lastMove)
        assertEquals(listOf(MoveEffects.ILLEGAL), feedback)
    }

    /** Regression (phone review): while a pawn was dragged, the other pawns drew enlarged. */
    @Test
    fun draggingAPawnLeavesTheOtherPawnsTheirSize() {
        val session = FreePlaySession()
        show(session)
        val board = compose.onNodeWithContentDescription("Chess board", substring = true)
        board.performTouchInput {
            val size = width.toFloat()
            down(centre("e2", size))
            moveTo(centre("e3", size))
            moveTo(centre("e4", size))
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Board_dragging.png")
        board.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(Move.fromUci("e2e4"), session.state.value.lastMove)
    }

    /** Regression (phone review): pressing a target and sliding off it used to play the move. */
    @Test
    fun pressingATargetAndSlidingOffItCancelsTheMove() {
        val session = FreePlaySession()
        show(session)
        val board = compose.onNodeWithContentDescription("Chess board", substring = true)
        board.performTouchInput {
            val size = width.toFloat()
            click(centre("g1", size))
            down(centre("f3", size))
            moveTo(centre("h4", size))
            up()
        }
        compose.waitForIdle()
        assertEquals("released elsewhere: no move", null, session.state.value.lastMove)
        board.performTouchInput { click(centre("f3", width.toFloat())) }
        compose.waitForIdle()
        assertEquals("the knight is still picked up", Move.fromUci("g1f3"), session.state.value.lastMove)
    }

    @Test
    fun tapTapWorksWithBlackAtTheBottom() {
        val session = FreePlaySession(Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"))
        show(session, whiteAtBottom = false)
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput {
            val size = width.toFloat()
            click(centre("e7", size, whiteAtBottom = false))
            click(centre("e5", size, whiteAtBottom = false))
        }
        compose.waitForIdle()
        assertEquals(Move.fromUci("e7e5"), session.state.value.lastMove)
    }

    @Test
    fun promotionOffersFourPiecesUnlessAutoQueenIsOn() {
        val promotion = "8/4P2k/8/8/8/8/6K1/8 w - - 0 1"
        val session = FreePlaySession(Position.fromFen(promotion))
        show(session)
        square("e7, white pawn").performSemanticsAction(SemanticsActions.OnClick)
        square("e8, empty").performSemanticsAction(SemanticsActions.OnClick)
        listOf("queen", "knight", "rook", "bishop").forEach {
            compose.onNodeWithContentDescription("Promote to $it").assertExists()
        }
        compose.onNodeWithContentDescription("Promote to knight").performClick()
        compose.waitForIdle()
        assertEquals(PieceType.KNIGHT, session.state.value.lastMove?.promotion)
        assertEquals(BoardSound.PROMOTE, feedback.single().sound)
    }

    @Test
    fun autoQueenPromotesStraightAway() {
        val session = FreePlaySession(Position.fromFen("8/4P2k/8/8/8/8/6K1/8 w - - 0 1"))
        show(session, autoQueen = true)
        square("e7, white pawn").performSemanticsAction(SemanticsActions.OnClick)
        square("e8, empty").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(PieceType.QUEEN, session.state.value.lastMove?.promotion)
        assertTrue(compose.onAllNodes(hasContentDescription("Promote to knight")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aDraggedPieceIsLiftedAndLeavesAGhost() {
        val session = FreePlaySession(Position.fromFen("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"))
        show(session)
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput {
            val size = width.toFloat()
            down(centre("f3", size))
            moveTo(centre("f4", size))
            moveTo(centre("g5", size))
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
        compose.onNodeWithContentDescription("Chess board", substring = true).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(Move.fromUci("f3g5"), session.state.value.lastMove)
    }

    @Test
    fun squareDescriptionsNameThePiece() {
        assertEquals("e1, white king", squareDescription(Position.initial(), Square.fromAlgebraic("e1")))
        assertEquals("d8, black queen", squareDescription(Position.initial(), Square.fromAlgebraic("d8")))
        assertEquals("e4, empty", squareDescription(Position.initial(), Square.fromAlgebraic("e4")))
    }

    @Test
    fun checkedKingIsFoundOnlyWhenInCheck() {
        assertEquals(null, checkedKing(Position.initial()))
        val mated = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertEquals(Square.fromAlgebraic("e1"), checkedKing(mated))
    }
}
