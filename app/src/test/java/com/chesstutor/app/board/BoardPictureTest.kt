package com.chesstutor.app.board

import com.chesstutor.app.core.Position
import org.junit.Assert.assertEquals
import org.junit.Test

/** A picture is cropped to the pieces, because the middle of many boards is empty. */
class BoardPictureTest {

    private fun window(fen: String, whiteAtBottom: Boolean = true, rows: Int = 3, cols: Int = 5) =
        pictureWindow(Position.tryFromFen(fen).getOrThrow(), whiteAtBottom, rows, cols)

    @Test
    fun aBusyMiddleStaysInTheMiddle() {
        val italian = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
        assertEquals(2 to 1, window(italian, rows = 4, cols = 6))
    }

    @Test
    fun theStartingPositionShowsARowOfPiecesNotTheEmptyMiddle() {
        val (row, _) = pictureWindow(Position.initial(), whiteAtBottom = true, rows = 2, cols = 8)
        assertEquals("the pawns, the pieces nearest the middle", 1, row)
    }

    @Test
    fun anEndgameInACornerIsFound() {
        // White king and rook on g1 and h2, the black king far away on a8.
        assertEquals(5 to 3, window("k7/8/8/8/8/8/7R/6K1 w - - 0 1"))
    }

    @Test
    fun aFlippedBoardLooksOnTheOtherSide() {
        assertEquals(0 to 0, window("k7/8/8/8/8/8/7R/6K1 w - - 0 1", whiteAtBottom = false))
    }

    @Test
    fun windowsThatTieGoToTheTop() {
        assertEquals(0 to 6, window("7k/8/8/8/8/8/8/K7 w - - 0 1", rows = 2, cols = 2))
    }

    @Test
    fun aWindowAsBigAsTheBoardHasNowhereToGo() {
        assertEquals(0 to 0, pictureWindow(Position.initial(), whiteAtBottom = true, rows = 8, cols = 8))
    }
}
