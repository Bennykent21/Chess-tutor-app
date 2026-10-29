package com.chesstutor.app.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.chesstutor.app.board.AnalysisMoveList
import com.chesstutor.app.board.AnalysisSession
import com.chesstutor.app.board.AnalysisToolbar
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardSession
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.EngineLinesPanel
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.MoveMark
import com.chesstutor.app.board.SquareBadge
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.toggleArrow
import com.chesstutor.app.board.toggleRing
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.ExploreJudge
import com.chesstutor.app.domain.account.JudgedMove
import com.chesstutor.app.domain.account.PlyEval
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import com.chesstutor.app.engine.EngineStatus
import com.chesstutor.app.feature.account.QualityIcon
import com.chesstutor.app.feature.account.qualityBadge
import com.chesstutor.app.feature.account.qualityIcon
import com.chesstutor.app.feature.account.qualityColor
import com.chesstutor.app.feature.account.qualityTextColor
import com.chesstutor.app.feature.home.EXPLORE_STRENGTHS

/** A quick first look, then a deeper one, so lines appear fast and then firm up. */
private val ANALYSIS_STEPS_MS = listOf(300, 1500)

/** A position skipped too fast to be analysed is looked at this long when its move is judged. */
private const val CATCH_UP_MS = 1000

/** How long the engine thinks over its reply at full strength. */
private const val REPLY_MS = 800

private data class Drawn(val arrows: List<BoardArrow> = emptyList(), val rings: List<SquareRing> = emptyList())

/**
 * Explore (D32): a board for any position. Both sides move, or the engine answers for one
 * side ([engineSide], at [engineElo] or full strength when null). Every move is judged like a
 * move in a game review: its quality on the board and in the move list, and the coach's
 * comment. Side lines, take-backs, engine lines and drawn arrows work as in free analysis
 * (plan section 4.2). From a puzzle's "Analyse" it opens with the solution as the main line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    startFen: String,
    settings: AppSettings,
    line: List<String> = emptyList(),
    engine: EngineService?,
    onFeedback: (BoardFeedback) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    engineSide: PieceColor? = null,
    engineElo: Int? = null,
) {
    val parsed = remember(startFen) { Position.tryFromFen(startFen).getOrNull() }
    if (parsed == null) {
        CtScreenBackground(modifier) {
            Column(Modifier.fillMaxSize()) {
                CtTopBar(title = "Explore", onBack = onBack)
                CtEmptyState(icon = Icons.Rounded.Warning, title = "This position could not be opened", body = "The position was not valid, so there is nothing to explore.")
            }
        }
        return
    }
    val start: Position = parsed
    val session = remember(start, line) {
        AnalysisSession(start).apply {
            // Load the line as the main line, then go back to the start to step through it.
            line.forEach { uci -> runCatching { Move.fromUci(uci) }.getOrNull()?.let(::onMove) }
            toStart()
        }
    }
    var opponent by remember { mutableStateOf(engineSide) }
    var strength by remember { mutableStateOf(engineElo) }
    // The engine answers right after the player's own move (never while stepping through a line).
    var replyDue by remember { mutableStateOf(if (line.isEmpty() && engineSide == start.sideToMove) session.currentId else null) }
    val board = remember(session) {
        object : BoardSession by session {
            override fun onMove(move: Move): BoardFeedback = session.onMove(move).also { replyDue = session.currentId }
        }
    }
    val state by session.state.collectAsState()
    val view by session.view.collectAsState()
    var whiteAtBottom by remember { mutableStateOf(if (engineSide != null) engineSide == PieceColor.BLACK else start.sideToMove == PieceColor.WHITE) }
    var drawing by remember { mutableStateOf(false) }
    // Engine lines give the answer away in a game against the engine, so they start hidden there.
    var showLines by remember { mutableStateOf(engineSide == null) }
    var thinking by remember { mutableStateOf(false) }
    val drawn = remember { mutableStateMapOf<Int, Drawn>() }
    val evals = remember { mutableStateMapOf<Int, PlyEval>() }
    val judged = remember { mutableStateMapOf<Int, JudgedMove>() }
    val engineStatus by (engine?.status?.collectAsState() ?: remember { mutableStateOf(EngineStatus()) })

    var lines by remember { mutableStateOf<List<EngineLine>>(emptyList()) }
    LaunchedEffect(state.version, opponent, strength) {
        lines = emptyList()
        val node = session.currentId
        val parentId = session.parentId
        val previous = state.previous
        val position = state.position
        if (engine == null) return@LaunchedEffect
        val lite = { engine.status.value.isDegraded }
        // The position before the last move needs the engine's view too, to judge that move.
        if (parentId != null && previous != null && evals[parentId] == null) {
            ExploreJudge.evalOf(previous, engine.analyse(previous.toFen(), lines = 3, movetimeMs = CATCH_UP_MS), lite())?.let { evals[parentId] = it }
        }
        if (state.legalMoves.isEmpty()) {
            ExploreJudge.evalOf(position, emptyList())?.let { evals[node] = it }
            return@LaunchedEffect
        }
        val fen = position.toFen()
        for (movetime in ANALYSIS_STEPS_MS) {
            val result = engine.analyse(fen, lines = 3, movetimeMs = movetime)
            if (result.isNotEmpty()) {
                lines = result
                ExploreJudge.evalOf(position, result, lite())?.let { evals[node] = it }
            }
        }
        if (opponent == position.sideToMove && replyDue == node) {
            thinking = true
            val choice = strength?.let { engine.botMove(fen, it) } ?: engine.bestMove(fen, REPLY_MS)
            thinking = false
            replyDue = null
            state.legalMoves.firstOrNull { it.uci == choice?.uci }?.let { onFeedback(session.onMove(it)) }
        }
    }

    // The move on the board, judged once both positions have the engine's view.
    val current = session.currentId
    val parentId = session.parentId
    val verdict = remember(state.version, evals[parentId], evals[current]) {
        val before = parentId?.let { evals[it] }
        val after = evals[current]
        val previous = state.previous
        val move = state.lastMove
        if (before == null || after == null || previous == null || move == null) null
        else ExploreJudge.judge(previous, move.uci, before, after)
    }
    LaunchedEffect(verdict) { verdict?.let { judged[current] = it } }

    val best = lines.firstOrNull()
    val engineArrows = if (!showLines) emptyList() else lines.mapIndexedNotNull { i, line ->
        val move = line.firstMove?.let { runCatching { Move.fromUci(it) }.getOrNull() } ?: return@mapIndexedNotNull null
        BoardArrow(move.from, move.to, if (i == 0) MarkKind.BEST else MarkKind.ALTERNATIVE)
    }
    val mine = drawn[current] ?: Drawn()
    val shown = verdict ?: judged[current]
    val quality = shown?.move?.quality
    val badge = shown?.let { j ->
        state.lastMove?.let { qualityBadge(it.to, j.move.quality) }
    }
    val marks = judged.mapValues { (_, j) -> MoveMark(j.move.quality.symbol, qualityColor(j.move.quality), j.move.quality.label, icon = qualityIcon(j.move.quality)) }

    CtScreenBackground(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CtTopBar(
                title = "Explore",
                subtitle = when {
                    line.isNotEmpty() -> "Step through the line, or try your own moves"
                    opponent != null -> "Play the engine; every move is judged"
                    else -> "Play any moves; every move is judged"
                },
                onBack = onBack,
            )
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                BoardWithEvalBar(
                    evalBar = { barModifier -> BoardEvalBar(best?.centipawns, best?.mateInMoves, whiteAtBottom, barModifier) },
                ) { boardModifier ->
                    BoardHost(
                        session = board,
                        theme = settings.boardTheme,
                        modifier = boardModifier,
                        whiteAtBottom = whiteAtBottom,
                        showCoordinates = settings.showCoordinates,
                        showLegalDots = settings.showLegalDots,
                        autoQueen = settings.autoQueen,
                        arrows = engineArrows + mine.arrows,
                        rings = mine.rings,
                        onFeedback = onFeedback,
                        drawMode = drawing,
                        onDrawArrow = { from, to ->
                            val now = drawn[current] ?: Drawn()
                            drawn[current] = now.copy(arrows = toggleArrow(now.arrows, BoardArrow(from, to, MarkKind.USER)))
                        },
                        onDrawRing = { square ->
                            val now = drawn[current] ?: Drawn()
                            drawn[current] = now.copy(rings = toggleRing(now.rings, SquareRing(square)))
                        },
                        lastMoveColor = quality?.let { qualityColor(it).copy(alpha = 0.55f).toArgb().toLong() and 0xFFFFFFFFL },
                        badge = badge,
                    )
                }
                if (engineStatus.isDegraded) {
                    CtBadge(text = "Lite engine: Stockfish is not answering", icon = Icons.Rounded.Warning)
                }
                VerdictCard(
                    verdict = shown,
                    hasMove = state.lastMove != null,
                    thinking = thinking,
                    onTryAgain = if (quality?.isError == true) session::back else null,
                )
                OpponentPicker(
                    opponent = opponent,
                    strength = strength,
                    onOpponent = { side ->
                        opponent = side
                        // Choosing the side to move hands the engine the next move at once.
                        replyDue = if (side == state.position.sideToMove) session.currentId else null
                        if (side != null) whiteAtBottom = side == PieceColor.BLACK
                    },
                    onStrength = { strength = it },
                )
                CtChip(text = if (showLines) "Hide engine lines" else "Show engine lines", color = CtColors.Teal, selected = showLines, onClick = { showLines = !showLines })
                if (showLines) {
                    EngineLinesPanel(
                        position = state.position,
                        lines = lines,
                        engineName = if (engineStatus.isDegraded) "Lite engine" else "Stockfish",
                        onPlayLine = { line ->
                            line.firstMove?.let { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
                                ?.takeIf { move -> state.legalMoves.any { it.uci == move.uci } }
                                ?.let { onFeedback(board.onMove(it)) }
                        },
                    )
                }
                AnalysisToolbar(
                    view = view,
                    drawing = drawing,
                    onStart = session::toStart,
                    onBack = session::back,
                    onForward = session::forward,
                    onEnd = session::toEnd,
                    onToggleDrawing = { drawing = !drawing },
                )
                AnalysisMoveList(view = view, onSelect = session::goTo, marks = marks)
                if (view.inVariation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                        CtButton("Make main line", onClick = session::promoteCurrent, style = CtButtonStyle.Secondary, modifier = Modifier.weight(1f))
                        CtButton("Delete line", onClick = session::deleteCurrent, style = CtButtonStyle.Ghost, modifier = Modifier.weight(1f))
                    }
                }
                CtButton(text = "Flip board", icon = Icons.Rounded.SwapVert, style = CtButtonStyle.Secondary, onClick = { whiteAtBottom = !whiteAtBottom })
                if (drawing) {
                    Text("Drag between squares to draw an arrow, tap a square to ring it. Draw it again to erase.", style = CtType.Caption)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

/** The coach's verdict on the move just played, or what to do next. */
@Composable
private fun VerdictCard(verdict: JudgedMove?, hasMove: Boolean, thinking: Boolean, onTryAgain: (() -> Unit)?) {
    CtCard(
        contentPadding = PaddingValues(CtSpacing.md),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (verdict != null) {
                QualityIcon(verdict.move.quality, size = 32)
                Spacer(Modifier.width(CtSpacing.md))
            }
            Column(Modifier.weight(1f)) {
                when {
                    thinking -> Text("The engine is thinking…", style = CtType.Body)
                    verdict != null -> {
                        Text(verdict.comment.headline, style = CtType.Label)
                        Text(verdict.comment.detail, style = CtType.BodySmall)
                    }
                    hasMove -> Text("Checking that move…", style = CtType.Body)
                    else -> Text("Play a move and I'll tell you how good it was.", style = CtType.Body)
                }
            }
        }
        if (onTryAgain != null) {
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = "Try again", icon = Icons.AutoMirrored.Rounded.Undo, style = CtButtonStyle.Secondary, onClick = onTryAgain, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Who the engine plays, and how strongly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpponentPicker(opponent: PieceColor?, strength: Int?, onOpponent: (PieceColor?) -> Unit, onStrength: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        Text("Engine plays", style = CtType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
            CtChip(text = "Nobody", color = CtColors.Gold, selected = opponent == null, onClick = { onOpponent(null) })
            CtChip(text = "White", color = CtColors.Gold, selected = opponent == PieceColor.WHITE, onClick = { onOpponent(PieceColor.WHITE) })
            CtChip(text = "Black", color = CtColors.Gold, selected = opponent == PieceColor.BLACK, onClick = { onOpponent(PieceColor.BLACK) })
        }
        if (opponent != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm), verticalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
                EXPLORE_STRENGTHS.forEach { elo ->
                    CtChip(text = elo?.toString() ?: "Full strength", color = CtColors.Violet, selected = strength == elo, onClick = { onStrength(elo) })
                }
            }
        }
    }
}
