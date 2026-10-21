package com.chesstutor.app.board

import com.chesstutor.app.core.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeometryTest {

    private fun sq(name: String) = Square.fromAlgebraic(name)

    @Test
    fun whiteAtBottomPutsA1BottomLeftAndH8TopRight() {
        assertEquals(BoardCell(col = 0, row = 7), BoardGeometry.cellOf(sq("a1"), whiteAtBottom = true))
        assertEquals(BoardCell(col = 7, row = 0), BoardGeometry.cellOf(sq("h8"), whiteAtBottom = true))
        assertEquals(BoardCell(col = 4, row = 6), BoardGeometry.cellOf(sq("e2"), whiteAtBottom = true))
    }

    @Test
    fun blackAtBottomRotatesTheBoardHalfATurn() {
        assertEquals(BoardCell(col = 7, row = 0), BoardGeometry.cellOf(sq("a1"), whiteAtBottom = false))
        assertEquals(BoardCell(col = 0, row = 7), BoardGeometry.cellOf(sq("h8"), whiteAtBottom = false))
        assertEquals(BoardCell(col = 3, row = 1), BoardGeometry.cellOf(sq("e2"), whiteAtBottom = false))
    }

    @Test
    fun cellAndSquareRoundTripForEverySquareInBothOrientations() {
        for (whiteAtBottom in listOf(true, false)) {
            val cells = (0 until 64).map { BoardGeometry.cellOf(Square(it), whiteAtBottom) }
            assertEquals("every square has its own cell", 64, cells.toSet().size)
            (0 until 64).forEach { index ->
                val square = Square(index)
                assertEquals(square, BoardGeometry.squareAt(BoardGeometry.cellOf(square, whiteAtBottom), whiteAtBottom))
            }
        }
    }

    @Test
    fun pointsMapToTheSquareUnderThem() {
        val size = 800f // 100 px squares
        assertEquals(sq("a8"), BoardGeometry.squareAt(1f, 1f, size, whiteAtBottom = true))
        assertEquals(sq("a1"), BoardGeometry.squareAt(50f, 750f, size, whiteAtBottom = true))
        assertEquals(sq("h1"), BoardGeometry.squareAt(799.9f, 799.9f, size, whiteAtBottom = true))
        assertEquals(sq("h1"), BoardGeometry.squareAt(1f, 1f, size, whiteAtBottom = false))
        assertEquals(sq("e4"), BoardGeometry.squareAt(450f, 450f, size, whiteAtBottom = true))
        assertEquals(sq("d5"), BoardGeometry.squareAt(450f, 450f, size, whiteAtBottom = false))
    }

    @Test
    fun pointsOutsideTheBoardHitNothing() {
        for ((x, y) in listOf(-1f to 10f, 10f to -1f, 800f to 10f, 10f to 800f)) {
            assertNull(BoardGeometry.squareAt(x, y, 800f, whiteAtBottom = true))
        }
        assertNull(BoardGeometry.squareAt(1f, 1f, 0f, whiteAtBottom = true))
    }

    @Test
    fun labelsSitOnTheBottomRowAndLeftColumn() {
        assertTrue(BoardGeometry.showsFileLabel(BoardCell(3, 7)))
        assertFalse(BoardGeometry.showsFileLabel(BoardCell(3, 6)))
        assertTrue(BoardGeometry.showsRankLabel(BoardCell(0, 2)))
        assertFalse(BoardGeometry.showsRankLabel(BoardCell(1, 2)))
    }
}
