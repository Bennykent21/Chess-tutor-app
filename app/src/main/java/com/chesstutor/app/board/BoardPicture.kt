package com.chesstutor.app.board

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.chesstutor.app.core.Position
import kotlin.math.abs
import kotlin.math.ceil

/** Positions used purely as pictures, on tiles that have no position of their own. */
object BoardScenes {
    const val OPENING = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
    const val MIDDLEGAME = "2rq1rk1/pp1bppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/2KR1B1R b - - 0 11"
    const val ATTACK = "r1b2rk1/ppq2ppp/2p5/4n3/4P3/2N1BP2/PPP3PP/R2Q1RK1 w - - 0 14"
    const val SETUP = "2r3k1/pp3ppp/2n1b3/3p4/3P4/2N1B3/PP3PPP/2R3K1 w - - 0 20"
    const val ENDGAME = "8/5pk1/6p1/3R4/8/6P1/r4PK1/8 w - - 0 40"
    const val TRAP = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
}

/** The share of a tile's height that stays clear of the fade behind its words. */
private const val CLEAR_SHARE = 0.6f

/**
 * A board filling its box: a picture, with no coordinates and nothing to tap. It is cropped to
 * the pieces when the middle of the board is empty, as it is in a starting position or an endgame.
 */
@Composable
fun BoardPicture(fen: String?, theme: BoardTheme, whiteAtBottom: Boolean = true, zoom: Float = 1.5f) {
    val position = remember(fen) { fen?.let { Position.tryFromFen(it).getOrNull() } ?: Position.initial() }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val board = max(maxWidth, maxHeight) * zoom
        val square = board / 8
        val rows = ceil(maxHeight / square * CLEAR_SHARE).toInt().coerceIn(1, 8)
        val cols = (maxWidth / square).toInt().coerceIn(1, 8)
        val (row, col) = remember(fen, whiteAtBottom, rows, cols) { pictureWindow(position, whiteAtBottom, rows, cols) }
        // The board sits centred, so these move it from there to show the window.
        val left = within(maxWidth - board, (maxWidth - square * cols) / 2 - square * col)
        val top = within(maxHeight - board, -(square * row))
        BoardView(
            position = position,
            theme = theme,
            modifier = Modifier.offset(left + (board - maxWidth) / 2, top + (board - maxHeight) / 2).requiredSize(board),
            whiteAtBottom = whiteAtBottom,
            showCoordinates = false,
            showLegalDots = false,
        )
    }
}

/** Keeps the board's edge from coming inside the box: [value] between [lowest] and 0. */
private fun within(lowest: Dp, value: Dp): Dp = min(0.dp, max(lowest, value))

/** A window with this many pieces is worth looking at; the action is nearer the middle than the most pieces are. */
private const val ENOUGH_PIECES = 4

/**
 * The top-left square, as seen on screen, of the [rows] by [cols] window to show: the one nearest
 * the middle with [ENOUGH_PIECES], or as many as any window has. Ties go to the first from the top.
 */
internal fun pictureWindow(position: Position, whiteAtBottom: Boolean, rows: Int, cols: Int): Pair<Int, Int> {
    fun hasPiece(row: Int, col: Int) =
        (if (whiteAtBottom) position.pieceAt(col, 7 - row) else position.pieceAt(7 - col, row)) != null
    fun pieces(window: Pair<Int, Int>) = (0 until rows).sumOf { r -> (0 until cols).count { c -> hasPiece(window.first + r, window.second + c) } }
    fun offCentre(window: Pair<Int, Int>) = abs(2 * window.first + rows - 8) + abs(2 * window.second + cols - 8)
    val windows = (0..8 - rows).flatMap { row -> (0..8 - cols).map { col -> row to col } }
    val enough = minOf(ENOUGH_PIECES, windows.maxOf(::pieces))
    return windows.filter { pieces(it) >= enough }.minBy(::offCentre)
}
