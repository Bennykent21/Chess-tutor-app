package com.chesstutor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.ChessPosition
import com.chesstutor.app.ui.theme.ChessTutorColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChessBoard(
    fen: String,
    modifier: Modifier = Modifier,
    selectedSquare: String? = null,
    legalTargets: Set<String> = emptySet(),
    lastMove: Pair<String, String>? = null,
    badSquare: String? = null,
    recommendedArrow: Pair<String, String>? = null,
    flipped: Boolean = false,
    onSquareTapped: (String) -> Unit
) {
    val lightSquareColor = ChessTutorColors.SqLight
    val darkSquareColor = ChessTutorColors.SqDark
    val selectedColor = ChessTutorColors.SquareSelected
    val lastMoveColor = ChessTutorColors.SquareLastMove
    val badColor = ChessTutorColors.SquareBad
    val targetDotColor = Color(0x4D121A20)
    val arrowColor = ChessTutorColors.Brass.copy(alpha = 0.92f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(0.dp))
            .pointerInput(flipped, fen) {
                detectTapGestures { offset ->
                    val squareSize = size.width / 8f
                    val fileIdx = (offset.x / squareSize).toInt().coerceIn(0, 7)
                    val rankIdx = (offset.y / squareSize).toInt().coerceIn(0, 7)

                    val actualFile = if (flipped) 7 - fileIdx else fileIdx
                    val actualRank = if (flipped) rankIdx else 7 - rankIdx

                    val fileChar = ('a' + actualFile)
                    val rankChar = ('1' + actualRank)
                    val squareStr = "$fileChar$rankChar"
                    onSquareTapped(squareStr)
                }
            }
    ) {
        val squareSize = size.width / 8f
        val pos = ChessPosition(fen)

        // Coordinate text paint
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = squareSize * 0.18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }

        // 1. Draw 8x8 Board Squares
        for (col in 0..7) {
            for (row in 0..7) {
                val file = if (flipped) 7 - col else col
                val rank = if (flipped) row else 7 - row

                val isLight = (file + rank) % 2 != 0
                val squareColor = if (isLight) lightSquareColor else darkSquareColor

                val topLeft = Offset(col * squareSize, row * squareSize)
                drawRect(
                    color = squareColor,
                    topLeft = topLeft,
                    size = Size(squareSize, squareSize)
                )

                val squareStr = "${('a' + file)}${('1' + rank)}"

                // Highlight last move
                if (lastMove != null && (lastMove.first == squareStr || lastMove.second == squareStr)) {
                    drawRect(
                        color = lastMoveColor,
                        topLeft = topLeft,
                        size = Size(squareSize, squareSize)
                    )
                }

                // Highlight selected square
                if (selectedSquare == squareStr) {
                    drawRect(
                        color = selectedColor,
                        topLeft = topLeft,
                        size = Size(squareSize, squareSize)
                    )
                }

                // Highlight bad square (e.g. missed move or blunder)
                if (badSquare == squareStr) {
                    drawRect(
                        color = badColor,
                        topLeft = topLeft,
                        size = Size(squareSize, squareSize)
                    )
                }

                // Board Coordinates matching mockup:
                // Rank number at left-top of leftmost column
                if (col == 0) {
                    val coordColorInt = if (isLight) 0xFF6E5F44.toInt() else 0xFFD5E2E9.toInt()
                    textPaint.color = coordColorInt
                    val rankText = "${'1' + rank}"
                    drawContext.canvas.nativeCanvas.drawText(
                        rankText,
                        topLeft.x + 4.dp.toPx(),
                        topLeft.y + squareSize * 0.22f,
                        textPaint
                    )
                }
                // File letter at bottom-right of bottom row
                if (row == 7) {
                    val coordColorInt = if (isLight) 0xFF6E5F44.toInt() else 0xFFD5E2E9.toInt()
                    textPaint.color = coordColorInt
                    val fileText = "${'a' + file}"
                    drawContext.canvas.nativeCanvas.drawText(
                        fileText,
                        topLeft.x + squareSize - 12.dp.toPx(),
                        topLeft.y + squareSize - 4.dp.toPx(),
                        textPaint
                    )
                }

                // Highlight legal move targets
                if (squareStr in legalTargets) {
                    val center = Offset(topLeft.x + squareSize / 2f, topLeft.y + squareSize / 2f)
                    val pieceOnSquare = pos.pieceAt(squareStr)
                    if (pieceOnSquare != null) {
                        // Capture ring (82% size)
                        drawCircle(
                            color = targetDotColor,
                            radius = squareSize * 0.41f,
                            center = center,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    } else {
                        // Move dot (21% size)
                        drawCircle(
                            color = targetDotColor,
                            radius = squareSize * 0.105f,
                            center = center
                        )
                    }
                }

                // Draw Piece (accurate Staunton-style silhouette)
                val pieceChar = pos.pieceAt(squareStr)
                if (pieceChar != null) {
                    val isWhite = pos.internalPosition.pieceAt(com.example.chess.core.Square.fromAlgebraic(squareStr))?.color == com.example.chess.core.PieceColor.WHITE
                    drawSvgPiece(
                        piece = pieceChar.lowercaseChar(),
                        isWhite = isWhite,
                        topLeft = topLeft,
                        squareSize = squareSize
                    )
                }
            }
        }

        // 2. Draw Recommended Arrow (if present)
        if (recommendedArrow != null) {
            val fromSq = recommendedArrow.first
            val toSq = recommendedArrow.second
            if (fromSq.length == 2 && toSq.length == 2) {
                val fromFile = fromSq[0] - 'a'
                val fromRank = fromSq[1] - '1'
                val toFile = toSq[0] - 'a'
                val toRank = toSq[1] - '1'

                val fromCol = if (flipped) 7 - fromFile else fromFile
                val fromRow = if (flipped) fromRank else 7 - fromRank
                val toCol = if (flipped) 7 - toFile else toFile
                val toRow = if (flipped) toRank else 7 - toRank

                val startOffset = Offset(fromCol * squareSize + squareSize / 2f, fromRow * squareSize + squareSize / 2f)
                val endOffset = Offset(toCol * squareSize + squareSize / 2f, toRow * squareSize + squareSize / 2f)

                drawArrow(startOffset, endOffset, arrowColor, squareSize)
            }
        }
    }
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color, squareSize: Float) {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val headLength = squareSize * 0.32f
    val shaftStroke = squareSize * 0.12f

    // Line shaft stops slightly before the tip
    val backOffset = headLength * 0.85f
    val shaftEnd = Offset(
        (end.x - cos(angle) * backOffset).toFloat(),
        (end.y - sin(angle) * backOffset).toFloat()
    )

    drawLine(
        color = color,
        start = start,
        end = shaftEnd,
        strokeWidth = shaftStroke,
        cap = StrokeCap.Round
    )

    // Arrowhead triangle
    val headAngle = PI / 5.5
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(
            (end.x - headLength * cos(angle - headAngle)).toFloat(),
            (end.y - headLength * sin(angle - headAngle)).toFloat()
        )
        lineTo(
            (end.x - headLength * cos(angle + headAngle)).toFloat(),
            (end.y - headLength * sin(angle + headAngle)).toFloat()
        )
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

// ============================================================================
// Accurate piece geometry, traced from the mockup's SVG <defs> (viewBox 0 0 45 45).
// Paths are built once in that fixed 45x45 coordinate space; each draw call
// wraps them in a translate+scale transform so they land on the right square
// at the right size, matching the mockup's silhouettes exactly rather than
// the earlier hand-approximated shapes.
// ============================================================================

private const val PIECE_STROKE_WIDTH = 1.15f

private val pawnBodyPath = Path().apply {
    moveTo(17.1f, 18.4f)
    lineTo(27.9f, 18.4f)
    cubicTo(27.5f, 20.5f, 26.3f, 21.5f, 25.5f, 22.2f)
    cubicTo(27.6f, 25.2f, 28.5f, 28.8f, 28.7f, 32.4f)
    lineTo(16.3f, 32.4f)
    cubicTo(16.5f, 28.8f, 17.4f, 25.2f, 19.5f, 22.2f)
    cubicTo(18.7f, 21.5f, 17.5f, 20.5f, 17.1f, 18.4f)
    close()
}

private val rookCrownPath = Path().apply {
    moveTo(12.9f, 13.4f)
    lineTo(17.2f, 13.4f)
    lineTo(17.2f, 16.5f)
    lineTo(20.6f, 16.5f)
    lineTo(20.6f, 13.4f)
    lineTo(24.4f, 13.4f)
    lineTo(24.4f, 16.5f)
    lineTo(27.8f, 16.5f)
    lineTo(27.8f, 13.4f)
    lineTo(32.1f, 13.4f)
    lineTo(32.1f, 21f)
    lineTo(12.9f, 21f)
    close()
}

private val rookBodyPath = Path().apply {
    moveTo(15.4f, 21f)
    lineTo(29.6f, 21f)
    lineTo(28.5f, 32f)
    lineTo(16.5f, 32f)
    close()
}

private val knightPath = Path().apply {
    moveTo(24.6f, 7.8f)
    cubicTo(26.6f, 10.4f, 27.6f, 13.0f, 28.1f, 15.5f)
    cubicTo(30.7f, 18.1f, 31.7f, 22.1f, 31.7f, 31.6f)
    lineTo(13.4f, 31.6f)
    cubicTo(13.4f, 27.1f, 14.6f, 23.8f, 16.5f, 21.0f)
    lineTo(12.8f, 23.1f)
    cubicTo(11.0f, 24.1f, 9.7f, 23.4f, 10.0f, 21.5f)
    cubicTo(10.5f, 17.9f, 13.3f, 14.3f, 16.9f, 12.2f)
    cubicTo(18.4f, 11.3f, 19.9f, 10.1f, 20.5f, 8.5f)
    close()
}

private val bishopBodyPath = Path().apply {
    moveTo(22.5f, 11.8f)
    cubicTo(27.2f, 14.5f, 29.7f, 18.6f, 29.7f, 22.5f)
    cubicTo(29.7f, 24.9f, 28.5f, 26.8f, 27.0f, 27.8f)
    lineTo(18.0f, 27.8f)
    cubicTo(16.5f, 26.8f, 15.3f, 24.9f, 15.3f, 22.5f)
    cubicTo(15.3f, 18.6f, 17.8f, 14.5f, 22.5f, 11.8f)
    close()
}

private val queenCrownPath = Path().apply {
    moveTo(11f, 16.6f)
    lineTo(14.5f, 28.0f)
    lineTo(30.5f, 28.0f)
    lineTo(34f, 16.6f)
    lineTo(28.25f, 21.2f)
    lineTo(25.5f, 13.8f)
    lineTo(23.0f, 21.2f)
    lineTo(20.5f, 13.8f)
    lineTo(17.75f, 21.2f)
    close()
}

private val kingCrossPath = Path().apply {
    moveTo(21.1f, 4.6f)
    lineTo(23.9f, 4.6f)
    lineTo(23.9f, 7.9f)
    lineTo(27.2f, 7.9f)
    lineTo(27.2f, 10.7f)
    lineTo(23.9f, 10.7f)
    lineTo(23.9f, 14.0f)
    lineTo(21.1f, 14.0f)
    lineTo(21.1f, 10.7f)
    lineTo(17.8f, 10.7f)
    lineTo(17.8f, 7.9f)
    lineTo(21.1f, 7.9f)
    close()
}

private val kingBodyPath = Path().apply {
    moveTo(22.5f, 13.6f)
    cubicTo(17.3f, 13.6f, 13.2f, 17.3f, 13.2f, 21.9f)
    cubicTo(13.2f, 24.5f, 14.5f, 26.6f, 16.2f, 28.0f)
    lineTo(28.8f, 28.0f)
    cubicTo(30.5f, 26.6f, 31.8f, 24.5f, 31.8f, 21.9f)
    cubicTo(31.8f, 17.3f, 27.7f, 13.6f, 22.5f, 13.6f)
    close()
}

private fun DrawScope.fillAndStroke(path: Path, fill: Color, stroke: Color) {
    drawPath(path, color = fill, style = Fill)
    drawPath(path, color = stroke, style = Stroke(width = PIECE_STROKE_WIDTH, join = StrokeJoin.Round))
}

private fun DrawScope.roundedBase(fill: Color, stroke: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    val topLeft = Offset(x, y)
    val size = Size(w, h)
    val corner = CornerRadius(r, r)
    drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = corner, style = Fill)
    drawRoundRect(color = stroke, topLeft = topLeft, size = size, cornerRadius = corner, style = Stroke(width = PIECE_STROKE_WIDTH, join = StrokeJoin.Round))
}

/**
 * Draws one piece at [topLeft] scaled to fill a [squareSize] square, using
 * the exact silhouette geometry from the mockup's SVG defs (0..45 viewBox).
 */
private fun DrawScope.drawSvgPiece(
    piece: Char,
    isWhite: Boolean,
    topLeft: Offset,
    squareSize: Float
) {
    val fillColor = if (isWhite) ChessTutorColors.PcWhite else ChessTutorColors.PcBlack
    val strokeColor = if (isWhite) ChessTutorColors.PcWhiteInk else ChessTutorColors.PcBlackInk
    val scale = squareSize / 45f

    withTransform({
        translate(left = topLeft.x, top = topLeft.y)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        when (piece) {
            'p' -> {
                drawCircle(fillColor, radius = 5.9f, center = Offset(22.5f, 12.6f), style = Fill)
                drawCircle(strokeColor, radius = 5.9f, center = Offset(22.5f, 12.6f), style = Stroke(PIECE_STROKE_WIDTH))
                fillAndStroke(pawnBodyPath, fillColor, strokeColor)
                roundedBase(fillColor, strokeColor, 12.4f, 31.9f, 20.2f, 4.9f, 1.7f)
            }
            'r' -> {
                fillAndStroke(rookCrownPath, fillColor, strokeColor)
                fillAndStroke(rookBodyPath, fillColor, strokeColor)
                roundedBase(fillColor, strokeColor, 11.7f, 31.5f, 21.6f, 5.1f, 1.7f)
            }
            'n' -> {
                fillAndStroke(knightPath, fillColor, strokeColor)
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 1.15f, center = Offset(24.4f, 14.6f), style = Fill)
                roundedBase(fillColor, strokeColor, 11.7f, 31.5f, 21.6f, 5.1f, 1.7f)
            }
            'b' -> {
                drawCircle(fillColor, radius = 2.7f, center = Offset(22.5f, 9.2f), style = Fill)
                drawCircle(strokeColor, radius = 2.7f, center = Offset(22.5f, 9.2f), style = Stroke(PIECE_STROKE_WIDTH))
                fillAndStroke(bishopBodyPath, fillColor, strokeColor)
                drawLine(strokeColor, start = Offset(19.7f, 16.3f), end = Offset(25.3f, 21.9f), strokeWidth = 1.7f, cap = StrokeCap.Round)
                roundedBase(fillColor, strokeColor, 15.9f, 27.6f, 13.2f, 3.5f, 1.2f)
                roundedBase(fillColor, strokeColor, 11.9f, 31.5f, 21.2f, 5.1f, 1.7f)
            }
            'q' -> {
                val crownCircles = listOf(
                    Offset(11f, 15.4f) to 2.2f,
                    Offset(16.75f, 12.4f) to 2.2f,
                    Offset(22.5f, 11.2f) to 2.4f,
                    Offset(28.25f, 12.4f) to 2.2f,
                    Offset(34f, 15.4f) to 2.2f
                )
                crownCircles.forEach { (center, radius) ->
                    drawCircle(fillColor, radius = radius, center = center, style = Fill)
                    drawCircle(strokeColor, radius = radius, center = center, style = Stroke(PIECE_STROKE_WIDTH))
                }
                fillAndStroke(queenCrownPath, fillColor, strokeColor)
                roundedBase(fillColor, strokeColor, 13.4f, 27.7f, 18.2f, 3.5f, 1.2f)
                roundedBase(fillColor, strokeColor, 11.5f, 31.6f, 22f, 5.1f, 1.7f)
            }
            'k' -> {
                fillAndStroke(kingCrossPath, fillColor, strokeColor)
                fillAndStroke(kingBodyPath, fillColor, strokeColor)
                roundedBase(fillColor, strokeColor, 13.4f, 27.7f, 18.2f, 3.5f, 1.2f)
                roundedBase(fillColor, strokeColor, 11.5f, 31.6f, 22f, 5.1f, 1.7f)
            }
        }
    }
}
