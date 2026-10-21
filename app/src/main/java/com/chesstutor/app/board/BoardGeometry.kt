package com.chesstutor.app.board

import com.chesstutor.app.core.Square

/** A cell on screen: [col] 0..7 from the left, [row] 0..7 from the top. */
data class BoardCell(val col: Int, val row: Int)

/** Maps between squares and screen cells for either orientation. Pure, so it is unit tested. */
object BoardGeometry {

    fun cellOf(square: Square, whiteAtBottom: Boolean): BoardCell =
        if (whiteAtBottom) BoardCell(col = square.file, row = 7 - square.rank)
        else BoardCell(col = 7 - square.file, row = square.rank)

    fun squareAt(cell: BoardCell, whiteAtBottom: Boolean): Square =
        if (whiteAtBottom) Square.of(file = cell.col, rank = 7 - cell.row)
        else Square.of(file = 7 - cell.col, rank = cell.row)

    /** The square under a point on a board [boardSize] wide, or null outside the board. */
    fun squareAt(x: Float, y: Float, boardSize: Float, whiteAtBottom: Boolean): Square? {
        if (boardSize <= 0f || x < 0f || y < 0f || x >= boardSize || y >= boardSize) return null
        val cellSize = boardSize / 8f
        val col = (x / cellSize).toInt().coerceIn(0, 7)
        val row = (y / cellSize).toInt().coerceIn(0, 7)
        return squareAt(BoardCell(col, row), whiteAtBottom)
    }

    /** Coordinates are printed on the bottom row (files) and the left column (ranks). */
    fun showsFileLabel(cell: BoardCell): Boolean = cell.row == 7

    fun showsRankLabel(cell: BoardCell): Boolean = cell.col == 0
}
