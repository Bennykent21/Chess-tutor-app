package com.chesstutor.app.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.core.LegalMoveGenerator
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.core.Position
import com.chesstutor.app.core.Square
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.LocalReduceMotion
import com.chesstutor.app.design.ctPressable
import kotlin.math.PI
import kotlin.math.sin

object BoardMotionSpec {
    /** Fade-through flip (decision D2): each half, so about 250 ms in all. */
    const val FLIP_HALF_MS = 125
    const val FLIP_DIM_ALPHA = 0.12f
    const val SHAKE_MS = 360
    /** Largest sideways shake of a king in check, as a fraction of a square. */
    const val SHAKE_AMPLITUDE = 0.07f
}

/**
 * The interactive board (plan section 4.1, step 1B): draws [session]'s position in the
 * player's [theme], and turns taps and drags into moves. Both tap-tap and drag-and-drop
 * work; pieces slide, captures fade, a checked king shakes and orientation changes fade
 * through. With "Reduce motion" on, everything snaps instead.
 *
 * [onFeedback] receives the sound and vibration for each event; the caller plays it,
 * respecting the player's sound and haptics settings.
 */
@Composable
fun BoardHost(
    session: BoardSession,
    theme: BoardTheme,
    modifier: Modifier = Modifier,
    whiteAtBottom: Boolean = true,
    showCoordinates: Boolean = true,
    showLegalDots: Boolean = true,
    autoQueen: Boolean = false,
    arrows: List<BoardArrow> = emptyList(),
    rings: List<SquareRing> = emptyList(),
    onFeedback: (BoardFeedback) -> Unit = {},
    /** In draw mode (analysis), drags draw arrows and taps ring squares instead of moving. */
    drawMode: Boolean = false,
    onDrawArrow: (from: Square, to: Square) -> Unit = { _, _ -> },
    onDrawRing: (Square) -> Unit = {},
) {
    val state by session.state.collectAsState()
    var drawPreview by remember { mutableStateOf<Pair<Square, Square>?>(null) }
    val reduceMotion = LocalReduceMotion.current

    var selected by remember { mutableStateOf<Square?>(null) }
    var dragged by remember { mutableStateOf<DraggedPiece?>(null) }
    var pendingPromotion by remember { mutableStateOf<Pair<Square, Square>?>(null) }
    // A move made by dragging lands where it was dropped, so its piece does not slide.
    var droppedMove by remember { mutableStateOf<Move?>(null) }

    // ---- slide, capture fade and check shake
    val slide = remember { Animatable(1f) }
    val shake = remember { Animatable(1f) }
    var animatedVersion by remember { mutableIntStateOf(state.version) }
    val plan = remember(state.version) {
        val move = state.lastMove
        val previous = state.previous
        if (!state.animateLastMove || move == null || previous == null) null
        else MoveEffects.animationPlan(previous, move).let { plan ->
            if (move == droppedMove) plan.copy(slides = plan.slides.drop(1)) else plan
        }
    }
    val checkSquare = remember(state.version) { checkedKing(state.position) }

    LaunchedEffect(state.version) {
        selected = null
        pendingPromotion = null
        if (plan != null && !reduceMotion) {
            slide.snapTo(0f)
            animatedVersion = state.version
            slide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
        } else {
            slide.snapTo(1f)
            animatedVersion = state.version
        }
        droppedMove = null
        if (checkSquare != null && state.animateLastMove && !reduceMotion) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(BoardMotionSpec.SHAKE_MS, easing = LinearEasing))
        }
    }
    // Until the effect starts, draw the new move at its start so it never flashes at the end.
    val progress = if (state.version != animatedVersion && plan != null && !reduceMotion) 0f else slide.value

    // ---- fade-through flip
    var shownWhiteAtBottom by remember { mutableStateOf(whiteAtBottom) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(whiteAtBottom) {
        if (whiteAtBottom == shownWhiteAtBottom) return@LaunchedEffect
        if (reduceMotion) {
            shownWhiteAtBottom = whiteAtBottom
            return@LaunchedEffect
        }
        fade.animateTo(BoardMotionSpec.FLIP_DIM_ALPHA, tween(BoardMotionSpec.FLIP_HALF_MS))
        shownWhiteAtBottom = whiteAtBottom
        fade.animateTo(1f, tween(BoardMotionSpec.FLIP_HALF_MS))
    }

    // The gesture loop is created once, so it must reach the latest session and callback.
    val currentSession by rememberUpdatedState(session)
    val currentOnFeedback by rememberUpdatedState(onFeedback)

    fun handle(intent: BoardIntent) {
        when (intent) {
            is BoardIntent.Select -> selected = intent.square
            BoardIntent.Deselect -> selected = null
            is BoardIntent.Play -> {
                selected = null
                currentOnFeedback(currentSession.onMove(intent.move))
            }
            is BoardIntent.ChoosePromotion -> {
                selected = null
                pendingPromotion = intent.from to intent.to
            }
            BoardIntent.Rejected -> {
                selected = null
                currentOnFeedback(MoveEffects.ILLEGAL)
            }
        }
    }

    val currentWhiteAtBottom by rememberUpdatedState(shownWhiteAtBottom)
    val currentAutoQueen by rememberUpdatedState(autoQueen)
    val currentPromotion by rememberUpdatedState(pendingPromotion)
    val currentDrawMode by rememberUpdatedState(drawMode)
    val currentOnDrawArrow by rememberUpdatedState(onDrawArrow)
    val currentOnDrawRing by rememberUpdatedState(onDrawRing)

    val marks = BoardMarks(
        lastMove = state.lastMove?.let { it.from to it.to },
        selected = selected ?: dragged?.from,
        legalTargets = (selected ?: dragged?.from)?.let { BoardInput.targetsFrom(it, state.legalMoves) } ?: emptySet(),
        check = checkSquare,
        // Arrows wait until the move has landed, so they never point from a moving piece.
        arrows = (if (progress < 1f) emptyList() else arrows) +
            listOfNotNull(drawPreview?.let { (from, to) -> BoardArrow(from, to, MarkKind.USER) }),
        rings = rings,
    )
    val shakeOffset = if (checkSquare != null && shake.value < 1f) {
        val t = shake.value
        (sin(t * PI * 6) * (1f - t) * BoardMotionSpec.SHAKE_AMPLITUDE).toFloat()
    } else 0f
    val motion = BoardMotion(
        slides = plan?.slides.orEmpty(),
        slideProgress = progress,
        captured = plan?.captured,
        dragged = dragged,
        shake = checkSquare?.let { it to shakeOffset },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val cellDp = maxWidth / 8
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (currentPromotion != null) return@awaitEachGesture
                        val boardSize = size.width.toFloat()
                        val start = BoardGeometry.squareAt(down.position.x, down.position.y, boardSize, currentWhiteAtBottom)
                            ?: return@awaitEachGesture
                        if (currentDrawMode) {
                            var target = start
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                BoardGeometry.squareAt(change.position.x, change.position.y, boardSize, currentWhiteAtBottom)?.let { target = it }
                                if (!change.pressed) {
                                    if (target == start) currentOnDrawRing(start) else currentOnDrawArrow(start, target)
                                    break
                                }
                                drawPreview = if (target != start) start to target else null
                            }
                            drawPreview = null
                            return@awaitEachGesture
                        }
                        // Read live state, not the last composition: two quick taps can arrive between frames.
                        val now = currentSession.state.value
                        val pickable = BoardInput.canPick(now.position, start, now.movable)
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (dragging) {
                                    dragged = null
                                    val target = BoardGeometry.squareAt(change.position.x, change.position.y, boardSize, currentWhiteAtBottom)
                                    val intent = BoardInput.drop(start, target, now.legalMoves, currentAutoQueen)
                                    if (intent is BoardIntent.Play) droppedMove = intent.move
                                    handle(intent)
                                } else {
                                    // A tap counts only where the finger lifts: pressing a square and
                                    // sliding off it is a change of mind, not a move.
                                    val released = BoardGeometry.squareAt(change.position.x, change.position.y, boardSize, currentWhiteAtBottom)
                                    if (released == start) {
                                        handle(BoardInput.tap(selected, start, now.position, now.legalMoves, now.movable, currentAutoQueen))
                                    }
                                }
                                break
                            }
                            if (!dragging && pickable && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                selected = null
                            }
                            if (dragging) {
                                change.consume()
                                now.position.pieceAt(start)?.let { dragged = DraggedPiece(it, start, change.position) }
                            }
                        }
                        // A cancelled gesture (for example the system took it) drops nothing.
                        if (dragging && dragged != null) dragged = null
                    }
                },
        ) {
            BoardCanvas(
                position = state.position,
                theme = theme,
                modifier = Modifier.fillMaxSize(),
                whiteAtBottom = shownWhiteAtBottom,
                showCoordinates = showCoordinates,
                showLegalDots = showLegalDots,
                marks = marks,
                motion = motion,
            )
            SquareSemantics(state, selected, shownWhiteAtBottom, cellDp.value) { square ->
                handle(BoardInput.tap(selected, square, state.position, state.legalMoves, state.movable, autoQueen))
            }
        }

        pendingPromotion?.let { (from, to) ->
            val color = state.position.pieceAt(from)?.color ?: PieceColor.WHITE
            PromotionPicker(
                color = color,
                pieceSet = theme.pieceSet,
                cell = BoardGeometry.cellOf(to, shownWhiteAtBottom),
                cellSizeDp = cellDp.value,
                onChoose = { type ->
                    val move = BoardInput.promotionMove(from, to, type, state.legalMoves)
                    pendingPromotion = null
                    if (move != null) handle(BoardIntent.Play(move))
                },
                onCancel = { pendingPromotion = null },
            )
        }
    }
}

/** The king of the side to move, if it is in check. */
internal fun checkedKing(position: Position): Square? =
    if (LegalMoveGenerator.isKingInCheck(position, position.sideToMove)) position.findKing(position.sideToMove) else null

/** What TalkBack says for one square, for example "e4, white pawn" or "d5, empty". */
internal fun squareDescription(position: Position, square: Square): String {
    val piece = position.pieceAt(square) ?: return "${square.algebraic}, empty"
    val color = if (piece.color == PieceColor.WHITE) "white" else "black"
    return "${square.algebraic}, $color ${piece.type.name.lowercase()}"
}

/**
 * One invisible node per square, so TalkBack users can explore the board and play by
 * double-tapping a piece and then its target. Touch input still goes to the board.
 */
@Composable
private fun SquareSemantics(
    state: BoardSessionState,
    selected: Square?,
    whiteAtBottom: Boolean,
    cellSizeDp: Float,
    onActivate: (Square) -> Unit,
) {
    val targets = selected?.let { BoardInput.targetsFrom(it, state.legalMoves) } ?: emptySet()
    for (index in 0 until 64) {
        val square = Square(index)
        val cell = BoardGeometry.cellOf(square, whiteAtBottom)
        val base = squareDescription(state.position, square)
        val description = if (square in targets) "$base, legal move" else base
        val canAct = state.movable != null
        Box(
            modifier = Modifier
                .offset(x = (cell.col * cellSizeDp).dp, y = (cell.row * cellSizeDp).dp)
                .size(cellSizeDp.dp)
                .semantics {
                    contentDescription = description
                    this.selected = square == selected
                    if (canAct) {
                        role = Role.Button
                        onClick(label = if (square in targets) "Move here" else "Select") {
                            onActivate(square)
                            true
                        }
                    }
                },
        )
    }
}

/** Queen, knight, rook, bishop in a column over the promotion square (towards the centre). */
@Composable
private fun PromotionPicker(
    color: PieceColor,
    pieceSet: PieceSet,
    cell: BoardCell,
    cellSizeDp: Float,
    onChoose: (PieceType) -> Unit,
    onCancel: () -> Unit,
) {
    val types = listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP)
    val downwards = cell.row < 4
    val firstRow = if (downwards) cell.row else cell.row - (types.size - 1)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CtColors.BgDeep.copy(alpha = 0.55f))
            .ctPressable(onClickLabel = "Cancel promotion", onClick = onCancel),
    ) {
        Column(
            modifier = Modifier
                .offset(x = (cell.col * cellSizeDp).dp, y = (firstRow * cellSizeDp).dp)
                .clip(CtShapes.Chip)
                .background(CtColors.SurfaceRaised)
                .border(1.dp, CtColors.Line, CtShapes.Chip),
        ) {
            val ordered = if (downwards) types else types.reversed()
            ordered.forEach { type ->
                val piece = Piece(type, color)
                Image(
                    painter = painterResource(PieceArt.drawable(pieceSet, piece)),
                    contentDescription = "Promote to ${type.name.lowercase()}",
                    modifier = Modifier
                        .size(cellSizeDp.dp)
                        .ctPressable(onClickLabel = "Promote to ${type.name.lowercase()}") { onChoose(type) }
                        .padding(2.dp),
                )
            }
        }
    }
}
