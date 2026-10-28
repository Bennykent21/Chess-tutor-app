package com.chesstutor.app.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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
    val arrows: List<BoardArrow> = emptyList(),
    /** Ringed squares, for example the squares a lesson talks about. */
    val rings: List<SquareRing> = emptyList(),
    /** Tints the last move instead of the usual gold (a game review colours it by quality). */
    val lastMoveColor: Long? = null,
    /** A small round label on a square's corner, such as a move's quality ("??"). */
    val badge: SquareBadge? = null,
)

/** A round label drawn on the top-right corner of [square]: [text] on [color] (ARGB). */
data class SquareBadge(val square: Square, val color: Long, val text: String, val textColor: Long = 0xFFFFFFFFL)

/** What an arrow or ring means, which sets its colour. */
enum class MarkKind { BEST, HINT, USER, ALTERNATIVE, MISTAKE }

data class BoardArrow(val from: Square, val to: Square, val kind: MarkKind = MarkKind.BEST)

data class SquareRing(val square: Square, val kind: MarkKind = MarkKind.USER)

internal fun MarkKind.argb(): Long = when (this) {
    MarkKind.BEST -> BoardMarkColors.ARROW_BEST
    MarkKind.HINT -> BoardMarkColors.ARROW_HINT
    MarkKind.USER -> BoardMarkColors.ARROW_USER
    MarkKind.ALTERNATIVE -> BoardMarkColors.ARROW_ALTERNATIVE
    MarkKind.MISTAKE -> BoardMarkColors.MISTAKE
}

/** A piece held under the finger, at [position] in board pixels. */
data class DraggedPiece(val piece: Piece, val from: Square, val position: Offset)

/** The moving parts [BoardHost] animates. A static board leaves it empty. */
data class BoardMotion(
    val slides: List<PieceSlide> = emptyList(),
    /** 0 at the start of a move, 1 when every slide has landed. */
    val slideProgress: Float = 1f,
    /** The piece being taken; it fades out while the slides run. */
    val captured: Pair<Square, Piece>? = null,
    val dragged: DraggedPiece? = null,
    /** The king in check shakes sideways by this fraction of a square. */
    val shake: Pair<Square, Float>? = null,
) {
    val isSliding: Boolean get() = slides.isNotEmpty() && slideProgress < 1f
}

/**
 * The board renderer (plan section 4): squares, marks, coordinates and pieces in one
 * Canvas, in the player's [theme]. This is the static board; [BoardHost] adds input and
 * motion on top of the same drawing code.
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
    BoardCanvas(position, theme, modifier, whiteAtBottom, showCoordinates, showLegalDots, marks, BoardMotion())
}

@Composable
internal fun BoardCanvas(
    position: Position,
    theme: BoardTheme,
    modifier: Modifier,
    whiteAtBottom: Boolean,
    showCoordinates: Boolean,
    showLegalDots: Boolean,
    marks: BoardMarks,
    motion: BoardMotion,
) {
    val painters = rememberPiecePainters(theme.pieceSet)
    val textMeasurer = rememberTextMeasurer()
    val badgeStyle = CtType.Label
    val coordinateStyle = CtType.Caption
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
        if (showCoordinates) drawCoordinates(theme.colors, whiteAtBottom, cell, textMeasurer, coordinateStyle)
        if (showLegalDots) drawLegalTargets(position, marks.legalTargets, whiteAtBottom, cell)
        motion.dragged?.let { drawHoverOutline(it, whiteAtBottom, cell) }
        drawPieces(position, painters, whiteAtBottom, cell, motion)
        marks.rings.forEach { drawRing(it, whiteAtBottom, cell) }
        marks.arrows.forEach { drawArrow(it, whiteAtBottom, cell) }
        marks.badge?.let { drawBadge(it, whiteAtBottom, cell, textMeasurer, badgeStyle) }
    }
}

/** A move's quality badge, half over the corner of its square like a sticker. */
private fun DrawScope.drawBadge(badge: SquareBadge, whiteAtBottom: Boolean, cell: Float, textMeasurer: TextMeasurer, textStyle: TextStyle) {
    val radius = cell * 0.2f
    val topLeft = cellTopLeft(badge.square, whiteAtBottom, cell)
    val center = Offset(
        (topLeft.x + cell - radius * 0.8f).coerceAtMost(size.width - radius),
        (topLeft.y + radius * 0.8f).coerceAtLeast(radius),
    )
    drawCircle(Color(BoardMarkColors.BADGE_SHADOW), radius = radius * 1.08f, center = center + Offset(0f, radius * 0.1f))
    drawCircle(Color(badge.color), radius = radius, center = center)
    if (badge.text.isNotEmpty()) {
        val style = textStyle.copy(fontSize = (radius * 0.95f).toSp(), lineHeight = (radius * 1.1f).toSp(), fontWeight = FontWeight.Black, color = Color(badge.textColor))
        val layout = textMeasurer.measure(badge.text, style)
        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** What TalkBack reads for the whole board; [BoardHost] adds a node per square. */
internal fun boardDescription(position: Position, whiteAtBottom: Boolean): String {
    val toMove = if (position.sideToMove == PieceColor.WHITE) "White to move" else "Black to move"
    val bottom = if (whiteAtBottom) "White at the bottom" else "Black at the bottom"
    return "Chess board. $toMove. $bottom."
}

private fun cellTopLeft(square: Square, whiteAtBottom: Boolean, cell: Float): Offset {
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
            drawRect(Color(marks.lastMoveColor ?: BoardMarkColors.LAST_MOVE), topLeft, cellSize)
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

/** A thick ring inside a square. */
private fun DrawScope.drawRing(ring: SquareRing, whiteAtBottom: Boolean, cell: Float) {
    val center = cellTopLeft(ring.square, whiteAtBottom, cell) + Offset(cell / 2f, cell / 2f)
    drawCircle(Color(ring.kind.argb()), radius = cell * 0.44f, center = center, style = Stroke(width = cell * 0.07f))
}

/** A straight arrow from centre to centre, with its head ending at the target centre. */
private fun DrawScope.drawArrow(arrow: BoardArrow, whiteAtBottom: Boolean, cell: Float) {
    if (arrow.from == arrow.to) return
    val half = Offset(cell / 2f, cell / 2f)
    val start = cellTopLeft(arrow.from, whiteAtBottom, cell) + half
    val end = cellTopLeft(arrow.to, whiteAtBottom, cell) + half
    val direction = end - start
    val length = direction.getDistance()
    val unit = direction / length
    val normal = Offset(-unit.y, unit.x)
    val headLength = cell * 0.42f
    val headHalfWidth = cell * 0.26f
    val shaftHalfWidth = cell * 0.085f
    val shaftStart = start + unit * (cell * 0.2f)
    val headBase = end - unit * headLength
    val path = Path().apply {
        moveTo(shaftStart.x + normal.x * shaftHalfWidth, shaftStart.y + normal.y * shaftHalfWidth)
        lineTo(headBase.x + normal.x * shaftHalfWidth, headBase.y + normal.y * shaftHalfWidth)
        lineTo(headBase.x + normal.x * headHalfWidth, headBase.y + normal.y * headHalfWidth)
        lineTo(end.x, end.y)
        lineTo(headBase.x - normal.x * headHalfWidth, headBase.y - normal.y * headHalfWidth)
        lineTo(headBase.x - normal.x * shaftHalfWidth, headBase.y - normal.y * shaftHalfWidth)
        lineTo(shaftStart.x - normal.x * shaftHalfWidth, shaftStart.y - normal.y * shaftHalfWidth)
        close()
    }
    drawPath(path, Color(arrow.kind.argb()))
}

/** Outlines the square under a dragged piece, since the lifted piece hides it. */
private fun DrawScope.drawHoverOutline(dragged: DraggedPiece, whiteAtBottom: Boolean, cell: Float) {
    val square = BoardGeometry.squareAt(dragged.position.x, dragged.position.y, size.minDimension, whiteAtBottom) ?: return
    val stroke = cell * 0.06f
    drawRect(
        color = Color(BoardMarkColors.SELECTED),
        topLeft = cellTopLeft(square, whiteAtBottom, cell) + Offset(stroke / 2f, stroke / 2f),
        size = Size(cell - stroke, cell - stroke),
        style = Stroke(width = stroke),
    )
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

private fun DrawScope.drawCoordinates(colors: BoardColors, whiteAtBottom: Boolean, cell: Float, textMeasurer: TextMeasurer, textStyle: TextStyle) {
    // Coordinates scale with the board rather than the type scale, so they always fit a square.
    val baseStyle = textStyle.copy(fontSize = (cell * 0.2f).toSp(), lineHeight = (cell * 0.24f).toSp(), fontWeight = FontWeight.Bold)
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

/** How much bigger a dragged piece is drawn. */
private const val LIFT_SCALE = 1.25f

private fun DrawScope.drawPiece(painter: Painter, topLeft: Offset, size: Float, alpha: Float = 1f) {
    translate(topLeft.x, topLeft.y) {
        with(painter) { draw(Size(size, size), alpha = alpha) }
    }
}

private fun DrawScope.drawPieces(position: Position, painters: Map<Piece, Painter>, whiteAtBottom: Boolean, cell: Float, motion: BoardMotion) {
    val sliding = motion.isSliding
    // Squares whose piece is drawn by the motion layer instead of in place.
    val landing = if (sliding) motion.slides.map { it.to }.toSet() else emptySet()

    if (sliding) {
        motion.captured?.let { (square, piece) ->
            painters[piece]?.let { drawPiece(it, cellTopLeft(square, whiteAtBottom, cell), cell, alpha = 1f - motion.slideProgress) }
        }
    }
    for (index in 0 until 64) {
        val square = Square(index)
        if (square in landing) continue
        val piece = position.pieceAt(square) ?: continue
        val painter = painters[piece] ?: continue
        var topLeft = cellTopLeft(square, whiteAtBottom, cell)
        val shake = motion.shake
        if (shake != null && shake.first == square) topLeft += Offset(shake.second * cell, 0f)
        // The dragged piece leaves a faint ghost on its square.
        val alpha = if (motion.dragged?.from == square) 0.3f else 1f
        drawPiece(painter, topLeft, cell, alpha)
    }
    if (sliding) {
        motion.slides.forEach { slide ->
            val painter = painters[slide.piece] ?: return@forEach
            val start = cellTopLeft(slide.from, whiteAtBottom, cell)
            val end = cellTopLeft(slide.to, whiteAtBottom, cell)
            drawPiece(painter, lerp(start, end, motion.slideProgress), cell)
        }
    }
    motion.dragged?.let { dragged ->
        val painter = painters[dragged.piece] ?: return@let
        // Lifted: a little larger and above the finger, so the finger does not hide it. The
        // canvas is scaled rather than the painter asked for a bigger size: a vector painter
        // caches its last size, and every other piece of that kind would draw enlarged.
        val lifted = cell * LIFT_SCALE
        val topLeft = dragged.position - Offset(lifted / 2f, lifted * 0.75f)
        translate(topLeft.x, topLeft.y) {
            scale(LIFT_SCALE, pivot = Offset.Zero) { with(painter) { draw(Size(cell, cell)) } }
        }
    }
}
