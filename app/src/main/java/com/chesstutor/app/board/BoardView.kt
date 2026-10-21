package com.chesstutor.app.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtType

/** Square marks an activity asks for. Empty by default: a plain board. */
data class BoardMarks(
    val lastMove: Pair<Square, Square>? = null,
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    /** The king in check, if any. */
    val check: Square? = null,
)

/**
 * The new board renderer (plan section 4, step 1A): squares, marks, coordinates and pieces
 * in one Canvas, in the player's [theme]. Static for now; step 1B adds input and motion.
 */
@Composable
fun BoardView(
    position: Position,
    theme: BoardTheme,
    modifier: Modifier = Modifier,
    whiteAtBottom: Boolean = true,
    showCoordinates: Boolean = true,
    showLegalDots: Boolean = true,
    marks: BoardMarks = BoardMarks(),
) {
    val painters = rememberPiecePainters(theme.pieceSet)
    val textMeasurer = rememberTextMeasurer()
    val description = boardDescription(position, whiteAtBottom)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CtShapes.Chip)
            .semantics { contentDescription = description },
    ) {
        val cell = size.minDimension / 8f
        drawSquares(theme.colors, marks, whiteAtBottom, cell)
        if (theme.colors.sheen) drawSheen()
        if (showCoordinates) drawCoordinates(theme.colors, whiteAtBottom, cell, textMeasurer)
        if (showLegalDots) drawLegalTargets(position, marks.legalTargets, whiteAtBottom, cell)
        drawPieces(position, painters, whiteAtBottom, cell)
    }
}

/** What TalkBack reads for the whole board until step 1B adds per-square semantics. */
internal fun boardDescription(position: Position, whiteAtBottom: Boolean): String {
    val toMove = if (position.sideToMove == PieceColor.WHITE) "White to move" else "Black to move"
    val bottom = if (whiteAtBottom) "White at the bottom" else "Black at the bottom"
    return "Chess board. $toMove. $bottom."
}

private fun DrawScope.cellTopLeft(square: Square, whiteAtBottom: Boolean, cell: Float): Offset {
    val c = BoardGeometry.cellOf(square, whiteAtBottom)
    return Offset(c.col * cell, c.row * cell)
}

private fun DrawScope.drawSquares(colors: BoardColors, marks: BoardMarks, whiteAtBottom: Boolean, cell: Float) {
    val light = Color(colors.light)
    val dark = Color(colors.dark)
    val cellSize = Size(cell, cell)
    for (index in 0 until 64) {
        val square = Square(index)
        val topLeft = cellTopLeft(square, whiteAtBottom, cell)
        drawRect(if (square.isLightSquare) light else dark, topLeft, cellSize)
        if (marks.lastMove != null && (square == marks.lastMove.first || square == marks.lastMove.second)) {
            drawRect(Color(BoardMarkColors.LAST_MOVE), topLeft, cellSize)
        }
        if (square == marks.selected) {
            drawRect(Color(BoardMarkColors.SELECTED), topLeft, cellSize)
        }
        if (square == marks.check) {
            val center = topLeft + Offset(cell / 2f, cell / 2f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(BoardMarkColors.CHECK), Color.Transparent),
                    center = center,
                    radius = cell * 0.7f,
                ),
                topLeft = topLeft,
                size = cellSize,
            )
        }
    }
}

/** A soft light-to-shade diagonal that gives wood and stone a little depth. */
private fun DrawScope.drawSheen() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.06f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

private fun DrawScope.drawCoordinates(colors: BoardColors, whiteAtBottom: Boolean, cell: Float, textMeasurer: TextMeasurer) {
    // Coordinates scale with the board rather than the type scale, so they always fit a square.
    val baseStyle = CtType.Caption.copy(fontSize = (cell * 0.2f).toSp(), lineHeight = (cell * 0.24f).toSp(), fontWeight = FontWeight.Bold)
    val inset = cell * 0.06f
    for (index in 0 until 64) {
        val square = Square(index)
        val c = BoardGeometry.cellOf(square, whiteAtBottom)
        val ink = Color(if (square.isLightSquare) colors.coordinateOnLight else colors.coordinateOnDark)
        val topLeft = Offset(c.col * cell, c.row * cell)
        if (BoardGeometry.showsRankLabel(c)) {
            drawText(textMeasurer, square.rankChar.toString(), topLeft + Offset(inset, inset * 0.5f), baseStyle.copy(color = ink))
        }
        if (BoardGeometry.showsFileLabel(c)) {
            val layout = textMeasurer.measure(square.fileChar.toString(), baseStyle.copy(color = ink))
            val origin = topLeft + Offset(cell - layout.size.width - inset, cell - layout.size.height)
            drawText(layout, topLeft = origin)
        }
    }
}

private fun DrawScope.drawLegalTargets(position: Position, targets: Set<Square>, whiteAtBottom: Boolean, cell: Float) {
    val color = Color(BoardMarkColors.LEGAL_DOT)
    targets.forEach { square ->
        val center = cellTopLeft(square, whiteAtBottom, cell) + Offset(cell / 2f, cell / 2f)
        if (position.pieceAt(square) != null) {
            drawCircle(color, radius = cell * 0.44f, center = center, style = Stroke(width = cell * 0.08f))
        } else {
            drawCircle(color, radius = cell * 0.15f, center = center)
        }
    }
}

private fun DrawScope.drawPieces(position: Position, painters: Map<Piece, Painter>, whiteAtBottom: Boolean, cell: Float) {
    for (index in 0 until 64) {
        val square = Square(index)
        val piece = position.pieceAt(square) ?: continue
        val painter = painters[piece] ?: continue
        val topLeft = cellTopLeft(square, whiteAtBottom, cell)
        translate(topLeft.x, topLeft.y) {
            with(painter) { draw(Size(cell, cell)) }
        }
    }
}
