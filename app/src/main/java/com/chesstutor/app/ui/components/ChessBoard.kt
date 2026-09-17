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

/**
 * Clean vector piece drawing with mockup colors:
 * White pieces: #F7F3EA with #2A3138 outline
 * Black pieces: #232B33 with #0C1014 outline
 */
private fun DrawScope.drawVectorPiece(
    piece: Char,
    isWhite: Boolean,
    topLeft: Offset,
    squareSize: Float
) {
    val primaryColor = if (isWhite) ChessTutorColors.PcWhite else ChessTutorColors.PcBlack
    val strokeColor = if (isWhite) ChessTutorColors.PcWhiteInk else ChessTutorColors.PcBlackInk

    val cx = topLeft.x + squareSize / 2f
    val cy = topLeft.y + squareSize / 2f
    val r = squareSize * 0.36f

    when (piece) {
        'p' -> { // Pawn
            val basePath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.45f, cy + r * 0.5f)
                lineTo(cx - r * 0.45f, cy + r * 0.5f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val bodyPath = Path().apply {
                moveTo(cx - r * 0.35f, cy + r * 0.5f)
                lineTo(cx + r * 0.35f, cy + r * 0.5f)
                lineTo(cx + r * 0.18f, cy - r * 0.1f)
                lineTo(cx - r * 0.18f, cy - r * 0.1f)
                close()
            }
            drawPath(bodyPath, primaryColor)
            drawPath(bodyPath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            drawCircle(primaryColor, r * 0.36f, Offset(cx, cy - r * 0.38f))
            drawCircle(strokeColor, r * 0.36f, Offset(cx, cy - r * 0.38f), style = Stroke(width = 1.8.dp.toPx()))
        }
        'r' -> { // Rook
            val basePath = Path().apply {
                moveTo(cx - r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.6f, cy + r * 0.5f)
                lineTo(cx - r * 0.6f, cy + r * 0.5f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val towerPath = Path().apply {
                moveTo(cx - r * 0.48f, cy + r * 0.5f)
                lineTo(cx + r * 0.48f, cy + r * 0.5f)
                lineTo(cx + r * 0.42f, cy - r * 0.4f)
                lineTo(cx - r * 0.42f, cy - r * 0.4f)
                close()
            }
            drawPath(towerPath, primaryColor)
            drawPath(towerPath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val battlementsPath = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.4f)
                lineTo(cx + r * 0.55f, cy - r * 0.4f)
                lineTo(cx + r * 0.55f, cy - r * 0.75f)
                lineTo(cx + r * 0.32f, cy - r * 0.75f)
                lineTo(cx + r * 0.32f, cy - r * 0.58f)
                lineTo(cx + r * 0.12f, cy - r * 0.58f)
                lineTo(cx + r * 0.12f, cy - r * 0.75f)
                lineTo(cx - r * 0.12f, cy - r * 0.75f)
                lineTo(cx - r * 0.12f, cy - r * 0.58f)
                lineTo(cx - r * 0.32f, cy - r * 0.58f)
                lineTo(cx - r * 0.32f, cy - r * 0.75f)
                lineTo(cx - r * 0.55f, cy - r * 0.75f)
                close()
            }
            drawPath(battlementsPath, primaryColor)
            drawPath(battlementsPath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))
        }
        'n' -> { // Knight
            val basePath = Path().apply {
                moveTo(cx - r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.55f, cy + r * 0.55f)
                lineTo(cx - r * 0.55f, cy + r * 0.55f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val horsePath = Path().apply {
                moveTo(cx + r * 0.5f, cy + r * 0.55f)
                lineTo(cx + r * 0.5f, cy - r * 0.15f)
                cubicTo(cx + r * 0.5f, cy - r * 0.7f, cx + r * 0.15f, cy - r * 0.85f, cx - r * 0.2f, cy - r * 0.85f)
                lineTo(cx - r * 0.55f, cy - r * 0.55f)
                lineTo(cx - r * 0.65f, cy - r * 0.2f)
                lineTo(cx - r * 0.4f, cy - r * 0.2f)
                lineTo(cx - r * 0.25f, cy - r * 0.05f)
                cubicTo(cx - r * 0.35f, cy + r * 0.2f, cx - r * 0.5f, cy + r * 0.4f, cx - r * 0.5f, cy + r * 0.55f)
                close()
            }
            drawPath(horsePath, primaryColor)
            drawPath(horsePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val eyeColor = if (isWhite) strokeColor else Color(0xFFF7F3EA)
            drawCircle(eyeColor, r * 0.08f, Offset(cx - r * 0.15f, cy - r * 0.48f))
        }
        'b' -> { // Bishop
            val basePath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.65f, cy + r * 0.85f)
                lineTo(cx + r * 0.45f, cy + r * 0.55f)
                lineTo(cx - r * 0.45f, cy + r * 0.55f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val mitrePath = Path().apply {
                moveTo(cx, cy - r * 0.75f)
                cubicTo(cx + r * 0.58f, cy - r * 0.5f, cx + r * 0.58f, cy + r * 0.4f, cx, cy + r * 0.55f)
                cubicTo(cx - r * 0.58f, cy + r * 0.4f, cx - r * 0.58f, cy - r * 0.5f, cx, cy - r * 0.75f)
                close()
            }
            drawPath(mitrePath, primaryColor)
            drawPath(mitrePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            drawCircle(primaryColor, r * 0.13f, Offset(cx, cy - r * 0.83f))
            drawCircle(strokeColor, r * 0.13f, Offset(cx, cy - r * 0.83f), style = Stroke(width = 1.8.dp.toPx()))

            // Cross slit
            drawLine(
                strokeColor,
                Offset(cx - r * 0.18f, cy - r * 0.15f),
                Offset(cx + r * 0.22f, cy + r * 0.05f),
                strokeWidth = 1.8.dp.toPx()
            )
        }
        'q' -> { // Queen
            val basePath = Path().apply {
                moveTo(cx - r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.55f, cy + r * 0.55f)
                lineTo(cx - r * 0.55f, cy + r * 0.55f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val crownPath = Path().apply {
                moveTo(cx - r * 0.65f, cy + r * 0.55f)
                lineTo(cx + r * 0.65f, cy + r * 0.55f)
                lineTo(cx + r * 0.8f, cy - r * 0.45f)
                lineTo(cx + r * 0.4f, cy - r * 0.15f)
                lineTo(cx, cy - r * 0.65f)
                lineTo(cx - r * 0.4f, cy - r * 0.15f)
                lineTo(cx - r * 0.8f, cy - r * 0.45f)
                close()
            }
            drawPath(crownPath, primaryColor)
            drawPath(crownPath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            drawCircle(primaryColor, r * 0.11f, Offset(cx - r * 0.8f, cy - r * 0.48f))
            drawCircle(strokeColor, r * 0.11f, Offset(cx - r * 0.8f, cy - r * 0.48f), style = Stroke(width = 1.5.dp.toPx()))

            drawCircle(primaryColor, r * 0.11f, Offset(cx, cy - r * 0.68f))
            drawCircle(strokeColor, r * 0.11f, Offset(cx, cy - r * 0.68f), style = Stroke(width = 1.5.dp.toPx()))

            drawCircle(primaryColor, r * 0.11f, Offset(cx + r * 0.8f, cy - r * 0.48f))
            drawCircle(strokeColor, r * 0.11f, Offset(cx + r * 0.8f, cy - r * 0.48f), style = Stroke(width = 1.5.dp.toPx()))
        }
        'k' -> { // King
            val basePath = Path().apply {
                moveTo(cx - r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.75f, cy + r * 0.85f)
                lineTo(cx + r * 0.55f, cy + r * 0.55f)
                lineTo(cx - r * 0.55f, cy + r * 0.55f)
                close()
            }
            drawPath(basePath, primaryColor)
            drawPath(basePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            val robePath = Path().apply {
                moveTo(cx - r * 0.55f, cy + r * 0.55f)
                lineTo(cx + r * 0.55f, cy + r * 0.55f)
                cubicTo(cx + r * 0.7f, cy + r * 0.2f, cx + r * 0.7f, cy - r * 0.4f, cx + r * 0.4f, cy - r * 0.45f)
                lineTo(cx - r * 0.4f, cy - r * 0.45f)
                cubicTo(cx - r * 0.7f, cy - r * 0.4f, cx - r * 0.7f, cy + r * 0.2f, cx - r * 0.55f, cy + r * 0.55f)
                close()
            }
            drawPath(robePath, primaryColor)
            drawPath(robePath, strokeColor, style = Stroke(width = 1.8.dp.toPx()))

            // Cross on top
            drawLine(
                strokeColor,
                Offset(cx, cy - r * 0.45f),
                Offset(cx, cy - r * 0.85f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                strokeColor,
                Offset(cx - r * 0.2f, cy - r * 0.68f),
                Offset(cx + r * 0.2f, cy - r * 0.68f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
