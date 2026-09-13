package com.chesstutor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.ChessPosition
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
    recommendedArrow: Pair<String, String>? = null,
    flipped: Boolean = false,
    onSquareTapped: (String) -> Unit
) {
    val lightSquareColor = Color(0xFFDCE2EC)
    val darkSquareColor = Color(0xFF6B7E96)
    val selectedColor = Color(0x77F59E0B)
    val lastMoveColor = Color(0x44F59E0B)
    val targetDotColor = Color(0x88F59E0B)
    val arrowColor = Color(0xCCF59E0B)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
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

                // Highlight legal move targets
                if (squareStr in legalTargets) {
                    val center = Offset(topLeft.x + squareSize / 2f, topLeft.y + squareSize / 2f)
                    val pieceOnSquare = pos.pieceAt(squareStr)
                    if (pieceOnSquare != null) {
                        // Capture ring
                        drawCircle(
                            color = targetDotColor,
                            radius = squareSize * 0.42f,
                            center = center,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    } else {
                        // Move dot
                        drawCircle(
                            color = targetDotColor,
                            radius = squareSize * 0.16f,
                            center = center
                        )
                    }
                }

                // Draw Vector Piece
                val pieceChar = pos.pieceAt(squareStr)
                if (pieceChar != null) {
                    val isWhite = pos.internalPosition.pieceAt(com.example.chess.core.Square.fromAlgebraic(squareStr))?.color == com.example.chess.core.PieceColor.WHITE
                    drawVectorPiece(
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
    val strokeWidth = squareSize * 0.12f
    val headLength = squareSize * 0.35f

    // Line shaft
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Arrowhead
    val headAngle = PI / 6.0
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

/**
 * Pure vector piece drawing that renders crisp vector silhouettes with high contrast
 * outlines on any screen density without relying on system font emojis or unicode traps.
 */
private fun DrawScope.drawVectorPiece(
    piece: Char,
    isWhite: Boolean,
    topLeft: Offset,
    squareSize: Float
) {
    val primaryColor = if (isWhite) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val strokeColor = if (isWhite) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val accentColor = if (isWhite) Color(0xFF94A3B8) else Color(0xFFF59E0B)

    val cx = topLeft.x + squareSize / 2f
    val cy = topLeft.y + squareSize / 2f
    val r = squareSize * 0.36f

    when (piece) {
        'p' -> { // Pawn
            // Base
            val basePath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.45f, cy + r * 0.5f)
                lineTo(cx - r * 0.45f, cy + r * 0.5f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Body
            val bodyPath = Path().apply {
                moveTo(cx - r * 0.35f, cy + r * 0.5f)
                lineTo(cx + r * 0.35f, cy + r * 0.5f)
                lineTo(cx + r * 0.18f, cy - r * 0.1f)
                lineTo(cx - r * 0.18f, cy - r * 0.1f)
                close()
            }
            drawPath(bodyPath, primaryColor)
            drawPath(bodyPath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Head circle
            drawCircle(primaryColor, r * 0.32f, Offset(cx, cy - r * 0.35f))
            drawCircle(strokeColor, r * 0.32f, Offset(cx, cy - r * 0.35f), style = Stroke(width = 2.dp.toPx()))
        }

        'n' -> { // Knight
            val knightPath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.4f, cy + r * 0.3f)
                lineTo(cx + r * 0.6f, cy - r * 0.1f)
                lineTo(cx + r * 0.45f, cy - r * 0.7f)
                lineTo(cx + r * 0.2f, cy - r * 0.85f)
                lineTo(cx - r * 0.1f, cy - r * 0.65f)
                lineTo(cx - r * 0.5f, cy - r * 0.35f)
                lineTo(cx - r * 0.65f, cy - r * 0.1f)
                lineTo(cx - r * 0.45f, cy)
                lineTo(cx - r * 0.35f, cy + r * 0.4f)
                close()
            }
            drawPath(knightPath, primaryColor)
            drawPath(knightPath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Mane / Eye detail
            drawCircle(accentColor, r * 0.08f, Offset(cx - r * 0.15f, cy - r * 0.35f))
        }

        'b' -> { // Bishop
            // Base
            val basePath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.4f, cy + r * 0.5f)
                lineTo(cx - r * 0.4f, cy + r * 0.5f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Miter oval
            drawOval(
                color = primaryColor,
                topLeft = Offset(cx - r * 0.36f, cy - r * 0.65f),
                size = Size(r * 0.72f, r * 1.15f)
            )
            drawOval(
                color = strokeColor,
                topLeft = Offset(cx - r * 0.36f, cy - r * 0.65f),
                size = Size(r * 0.72f, r * 1.15f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Top cross dot
            drawCircle(accentColor, r * 0.10f, Offset(cx, cy - r * 0.75f))
        }

        'r' -> { // Rook
            // Castle body
            val rookPath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.5f, cy + r * 0.45f)
                lineTo(cx + r * 0.4f, cy - r * 0.35f)
                lineTo(cx + r * 0.65f, cy - r * 0.45f)
                // Crenellations
                lineTo(cx + r * 0.65f, cy - r * 0.8f)
                lineTo(cx + r * 0.35f, cy - r * 0.8f)
                lineTo(cx + r * 0.35f, cy - r * 0.6f)
                lineTo(cx + r * 0.12f, cy - r * 0.6f)
                lineTo(cx + r * 0.12f, cy - r * 0.8f)
                lineTo(cx - r * 0.12f, cy - r * 0.8f)
                lineTo(cx - r * 0.12f, cy - r * 0.6f)
                lineTo(cx - r * 0.35f, cy - r * 0.6f)
                lineTo(cx - r * 0.35f, cy - r * 0.8f)
                lineTo(cx - r * 0.65f, cy - r * 0.8f)
                lineTo(cx - r * 0.65f, cy - r * 0.45f)
                lineTo(cx - r * 0.4f, cy - r * 0.35f)
                lineTo(cx - r * 0.5f, cy + r * 0.45f)
                close()
            }
            drawPath(rookPath, primaryColor)
            drawPath(rookPath, strokeColor, style = Stroke(width = 2.dp.toPx()))
        }

        'q' -> { // Queen
            val queenPath = Path().apply {
                moveTo(cx - r * 0.7f, cy + r * 0.85f)
                lineTo(cx + r * 0.7f, cy + r * 0.85f)
                lineTo(cx + r * 0.45f, cy + r * 0.45f)
                // Crown spikes
                lineTo(cx + r * 0.75f, cy - r * 0.5f)
                lineTo(cx + r * 0.35f, cy - r * 0.2f)
                lineTo(cx, cy - r * 0.75f)
                lineTo(cx - r * 0.35f, cy - r * 0.2f)
                lineTo(cx - r * 0.75f, cy - r * 0.5f)
                lineTo(cx - r * 0.45f, cy + r * 0.45f)
                close()
            }
            drawPath(queenPath, primaryColor)
            drawPath(queenPath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Crown jewels
            drawCircle(accentColor, r * 0.09f, Offset(cx - r * 0.72f, cy - r * 0.53f))
            drawCircle(accentColor, r * 0.10f, Offset(cx, cy - r * 0.78f))
            drawCircle(accentColor, r * 0.09f, Offset(cx + r * 0.72f, cy - r * 0.53f))
        }

        'k' -> { // King
            val kingPath = Path().apply {
                moveTo(cx - r * 0.7f, cy + r * 0.85f)
                lineTo(cx + r * 0.7f, cy + r * 0.85f)
                lineTo(cx + r * 0.45f, cy + r * 0.45f)
                lineTo(cx + r * 0.6f, cy - r * 0.3f)
                lineTo(cx + r * 0.25f, cy - r * 0.55f)
                lineTo(cx - r * 0.25f, cy - r * 0.55f)
                lineTo(cx - r * 0.6f, cy - r * 0.3f)
                lineTo(cx - r * 0.45f, cy + r * 0.45f)
                close()
            }
            drawPath(kingPath, primaryColor)
            drawPath(kingPath, strokeColor, style = Stroke(width = 2.dp.toPx()))

            // Cross
            drawLine(strokeColor, Offset(cx, cy - r * 0.55f), Offset(cx, cy - r * 0.9f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Square)
            drawLine(strokeColor, Offset(cx - r * 0.2f, cy - r * 0.75f), Offset(cx + r * 0.2f, cy - r * 0.75f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Square)
            drawLine(accentColor, Offset(cx, cy - r * 0.55f), Offset(cx, cy - r * 0.9f), strokeWidth = 1.5.dp.toPx())
        }
    }
}
