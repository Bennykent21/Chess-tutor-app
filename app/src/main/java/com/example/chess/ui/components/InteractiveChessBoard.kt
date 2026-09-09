package com.example.chess.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Move
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.ui.theme.BoardCheckSquare
import com.example.chess.ui.theme.BoardDarkSquare
import com.example.chess.ui.theme.BoardHighlightSquare
import com.example.chess.ui.theme.BoardLastMoveSquare
import com.example.chess.ui.theme.BoardLightSquare
import com.example.chess.ui.theme.CanvasCardBorder
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBlunder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ChessBoardTheme(
  val label: String,
  val lightSquare: Color,
  val darkSquare: Color,
  val lastMoveHighlight: Color = Color(0x66F59E0B),
  val boardBorder: Color = Color(0x334E5D6C)
) {
  CLASSIC_TOURNAMENT(
    label = "Tournament Green",
    lightSquare = Color(0xFFEEEED2),
    darkSquare = Color(0xFF769656),
    lastMoveHighlight = Color(0x77BACA44),
    boardBorder = Color(0xFF53693D)
  ),
  WARM_WOOD(
    label = "Walnut & Maple",
    lightSquare = Color(0xFFF0D9B5),
    darkSquare = Color(0xFFB58863),
    lastMoveHighlight = Color(0x77CDA869),
    boardBorder = Color(0xFF8B6447)
  ),
  SLATE_GLASS(
    label = "Modern Slate",
    lightSquare = Color(0xFFE2E8F0),
    darkSquare = Color(0xFF475569),
    lastMoveHighlight = Color(0x6638BDF8),
    boardBorder = Color(0xFF334155)
  ),
  CYBER_NEON(
    label = "Cyber Midnight",
    lightSquare = Color(0xFF1E293B),
    darkSquare = Color(0xFF0F172A),
    lastMoveHighlight = Color(0x6606B6D4),
    boardBorder = Color(0xFF06B6D4)
  )
}

/**
 * High-Contrast, Tournament-Grade Chessboard.
 * Designed so that white and black pieces have distinct outlines and remain
 * perfectly visible against both light sandstone and dark forest squares.
 */
@Composable
fun InteractiveChessBoard(
  position: Position,
  modifier: Modifier = Modifier,
  flipped: Boolean = false,
  boardTheme: ChessBoardTheme = ChessBoardTheme.CLASSIC_TOURNAMENT,
  selectedSquare: Square? = null,
  onSquareTapped: (Square) -> Unit = {},
  legalTargetSquares: Set<Square> = emptySet(),
  recommendedArrow: Pair<Square, Square>? = null,
  threatArrow: Pair<Square, Square>? = null,
  highlightedSquares: Set<Square> = emptySet(),
  lastMove: Move? = null
) {
  val inCheck = LegalMoveGenerator.isKingInCheck(position, position.sideToMove)
  val kingInCheckSquare = if (inCheck) position.findKing(position.sideToMove) else null

  BoxWithConstraints(
    modifier = modifier.testTag("interactive_chess_board"),
    contentAlignment = Alignment.Center
  ) {
    val boardSize = min(maxWidth, maxHeight)

    Box(
      modifier = Modifier
        .size(boardSize)
        .aspectRatio(1f)
        .clip(RoundedCornerShape(14.dp))
        .border(2.dp, boardTheme.boardBorder, RoundedCornerShape(14.dp))
    ) {
      val squareSize = boardSize / 8

      // Draw squares and pieces
      for (rank in 0..7) {
        for (file in 0..7) {
          val displayRank = if (flipped) rank else 7 - rank
          val displayFile = if (flipped) 7 - file else file
          val square = Square.of(displayFile, displayRank)
          val piece = position.pieceAt(square)

          val isLight = square.isLightSquare
          val isSelected = selectedSquare == square
          val isLegalTarget = square in legalTargetSquares
          val isHighlighted = square in highlightedSquares
          val isLastMoveSquare = lastMove != null && (lastMove.from == square || lastMove.to == square)
          val isKingChecked = kingInCheckSquare == square

          val squareBg = when {
            isKingChecked -> BoardCheckSquare
            isSelected -> BoardHighlightSquare
            isHighlighted -> Color(0x55F59E0B)
            isLastMoveSquare -> boardTheme.lastMoveHighlight
            isLight -> boardTheme.lightSquare
            else -> boardTheme.darkSquare
          }

          Box(
            modifier = Modifier
              .size(squareSize)
              .align(Alignment.TopStart)
              .offset(
                x = squareSize * file,
                y = squareSize * rank
              )
              .background(squareBg)
              .clickable { onSquareTapped(square) }
              .testTag("square_${square.algebraic}"),
            contentAlignment = Alignment.Center
          ) {
            // Rank and File coordinate indicators on edges (high readability contrast)
            if (file == 0) {
              Text(
                text = "${displayRank + 1}",
                color = if (isLight) Color(0xFF5E6D5D) else Color(0xFFD3C7AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .offset(x = 3.dp, y = 2.dp)
              )
            }
            if (rank == 7) {
              Text(
                text = "${'a' + displayFile}",
                color = if (isLight) Color(0xFF5E6D5D) else Color(0xFFD3C7AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .offset(x = (-3).dp, y = (-2).dp)
              )
            }

            // Piece Rendering with 100% Guaranteed High Contrast (Shadow/Outline)
            if (piece != null) {
              ChessPieceView(
                piece = piece,
                modifier = Modifier.size(squareSize * 0.88f)
              )
            }

            // Legal move indicator pip (crisp dot for empty square, vivid target ring for captures)
            if (isLegalTarget) {
              if (piece == null) {
                Box(
                  modifier = Modifier
                    .size(squareSize * 0.30f)
                    .clip(CircleShape)
                    .background(Color(0xCC0EA5E9))
                    .border(1.5.dp, Color(0xEEFFFFFF), CircleShape)
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(squareSize * 0.88f)
                    .border(3.5.dp, Color(0xEEEF4444), CircleShape)
                )
              }
            }
          }
        }
      }

      // Draw Vector Overlay Arrows (Coach Recommended Plan & Threat Arrows)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val sqPx = size.width / 8f

        // 1. Threat Arrow (Crimson Dashed/Solid)
        threatArrow?.let { (from, to) ->
          drawCoachArrow(
            from = from,
            to = to,
            sqPx = sqPx,
            flipped = flipped,
            color = StatusBlunder.copy(alpha = 0.90f),
            strokeWidth = 4.5.dp.toPx()
          )
        }

        // 2. Recommended Arrow (Amber Solid)
        recommendedArrow?.let { (from, to) ->
          drawCoachArrow(
            from = from,
            to = to,
            sqPx = sqPx,
            flipped = flipped,
            color = CoachPrimary.copy(alpha = 0.95f),
            strokeWidth = 5.dp.toPx()
          )
        }
      }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoachArrow(
  from: Square,
  to: Square,
  sqPx: Float,
  flipped: Boolean,
  color: Color,
  strokeWidth: Float
) {
  val fromCol = if (flipped) 7 - from.file else from.file
  val fromRow = if (flipped) from.rank else 7 - from.rank
  val toCol = if (flipped) 7 - to.file else to.file
  val toRow = if (flipped) to.rank else 7 - to.rank

  val start = Offset((fromCol + 0.5f) * sqPx, (fromRow + 0.5f) * sqPx)
  val end = Offset((toCol + 0.5f) * sqPx, (toRow + 0.5f) * sqPx)

  drawLine(
    color = color,
    start = start,
    end = end,
    strokeWidth = strokeWidth,
    cap = StrokeCap.Round
  )

  // Arrowhead calculation
  val angle = atan2(end.y - start.y, end.x - start.x)
  val arrowHeadLength = sqPx * 0.28f
  val arrowAngle = Math.toRadians(28.0).toFloat()

  val p1 = Offset(
    end.x - arrowHeadLength * cos(angle - arrowAngle),
    end.y - arrowHeadLength * sin(angle - arrowAngle)
  )
  val p2 = Offset(
    end.x - arrowHeadLength * cos(angle + arrowAngle),
    end.y - arrowHeadLength * sin(angle + arrowAngle)
  )

  val arrowPath = Path().apply {
    moveTo(end.x, end.y)
    lineTo(p1.x, p1.y)
    lineTo(p2.x, p2.y)
    close()
  }

  drawPath(path = arrowPath, color = color)
}

/**
 * Double-Pass Stroke Rendered Chess Piece.
 * Renders an outer stroke/shadow outline followed by the core fill, ensuring
 * that White pieces on Light Sandstone squares have an unmistakable, bold dark boundary,
 * and Black pieces on Dark squares have a clean light boundary.
 */
@Composable
fun ChessPieceView(
  piece: Piece,
  modifier: Modifier = Modifier
) {
  val symbol = when (piece.type) {
    PieceType.KING -> "♚"
    PieceType.QUEEN -> "♛"
    PieceType.ROOK -> "♜"
    PieceType.BISHOP -> "♝"
    PieceType.KNIGHT -> "♞"
    PieceType.PAWN -> "♟"
  }

  Canvas(modifier = modifier) {
    val textSize = size.width * 0.86f
    val x = size.width / 2f
    val y = size.height * 0.79f

    val native = drawContext.canvas.nativeCanvas
    val boldTypeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)

    if (piece.color == PieceColor.WHITE) {
      // 1. Dark outline for White Piece (Bold charcoal border)
      val strokePaint = Paint().apply {
        isAntiAlias = true
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
        this.textSize = textSize
        style = Paint.Style.STROKE
        strokeWidth = textSize * 0.11f
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.rgb(18, 22, 28)
      }
      native.drawText(symbol, x, y, strokePaint)

      // 2. Pure White Solid Fill
      val fillPaint = Paint().apply {
        isAntiAlias = true
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
        this.textSize = textSize
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
      }
      native.drawText(symbol, x, y, fillPaint)
    } else {
      // 1. Soft Light Ivory outline for Black Piece
      val strokePaint = Paint().apply {
        isAntiAlias = true
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
        this.textSize = textSize
        style = Paint.Style.STROKE
        strokeWidth = textSize * 0.10f
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.rgb(238, 242, 248)
      }
      native.drawText(symbol, x, y, strokePaint)

      // 2. Deep Obsidian Black Solid Fill
      val fillPaint = Paint().apply {
        isAntiAlias = true
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
        this.textSize = textSize
        style = Paint.Style.FILL
        color = android.graphics.Color.rgb(22, 25, 32)
      }
      native.drawText(symbol, x, y, fillPaint)
    }
  }
}
