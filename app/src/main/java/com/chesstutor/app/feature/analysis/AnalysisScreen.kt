package com.chesstutor.app.feature.analysis

import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtGroupLabel
import com.chesstutor.app.design.CtSegmented
import com.chesstutor.app.design.CtSheet
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtOverflowMenu
import com.chesstutor.app.design.CtMenuItem
import com.chesstutor.app.design.CtBoardScreen
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

    var engineSheet by remember { mutableStateOf(false) }
    val tryAgain: (() -> Unit)? = if (quality?.isError == true) session::back else null
    val menu = buildList {
        add(CtMenuItem("Flip board") { whiteAtBottom = !whiteAtBottom })
        add(CtMenuItem(if (showLines) "Hide engine lines" else "Show engine lines") { showLines = !showLines })
        add(CtMenuItem("Play the engine") { engineSheet = true })
        add(CtMenuItem(if (drawing) "Stop drawing" else "Draw arrows") { drawing = !drawing })
        if (view.inVariation) {
            add(CtMenuItem("Make main line", onClick = session::promoteCurrent))
            add(CtMenuItem("Delete line", destructive = true, onClick = session::deleteCurrent))
        }
    }

    CtBoardScreen(
        title = "Explore",
        onBack = onBack,
        modifier = modifier,
        actions = { CtOverflowMenu(menu) },
        status = { Verdict(verdict = shown, hasMove = state.lastMove != null, thinking = thinking, evalCp = best?.centipawns, evalMate = best?.mateInMoves) },
        board = {
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
        },
        controls = {
            if (tryAgain != null) {
                CtIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous move", onClick = session::back, enabled = view.canGoBack)
                CtIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next move", onClick = session::forward, enabled = view.canGoForward)
                CtButton(text = "Try again", style = CtButtonStyle.Secondary, onClick = tryAgain, modifier = Modifier.weight(1f))
            } else {
                CtIconButton(icon = Icons.Rounded.FirstPage, contentDescription = "First move", onClick = session::toStart, enabled = view.canGoBack)
                CtIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous move", onClick = session::back, enabled = view.canGoBack)
                CtIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next move", onClick = session::forward, enabled = view.canGoForward)
                CtIconButton(icon = Icons.AutoMirrored.Rounded.LastPage, contentDescription = "Last move", onClick = session::toEnd, enabled = view.canGoForward)
            }
        },
        overlay = {
            CtSheet(visible = engineSheet, onDismiss = { engineSheet = false }) {
                Text("Play the engine", style = CtType.Headline)
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
                CtButton(text = "Done", onClick = { engineSheet = false }, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
            if (engineStatus.isDegraded) {
                CtBadge(text = "Lite engine: Stockfish is not answering", icon = Icons.Rounded.Warning)
            }
            AnalysisMoveList(view = view, onSelect = session::goTo, marks = marks)
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
        }
    }
}

/** The verdict on the move just played (or what to do next), in the fixed row above the board. */
@Composable
private fun Verdict(verdict: JudgedMove?, hasMove: Boolean, thinking: Boolean, evalCp: Int?, evalMate: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        if (verdict != null) QualityIcon(verdict.move.quality, size = 32)
        Column(Modifier.weight(1f)) {
            when {
                thinking -> Text("The engine is thinking…", style = CtType.TitleSmall)
                verdict != null -> {
                    Text(verdict.comment.headline, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(verdict.comment.detail, style = CtType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                hasMove -> Text("Checking that move…", style = CtType.TitleSmall)
                else -> Text("Play a move and I'll tell you how good it was.", style = CtType.BodySmall.copy(color = CtColors.Text), maxLines = 2)
            }
        }
        val eval = evalText(evalCp, evalMate)
        if (eval != null) {
            Text(eval, style = CtType.Label, modifier = Modifier.clip(CtShapes.Chip).background(CtColors.Surface).padding(horizontal = 10.dp, vertical = CtSpacing.xs))
        }
    }
}

/** "+0.9", "−2.3" or "M3" from White's side; null before the engine answers. */
internal fun evalText(cp: Int?, mate: Int?): String? = when {
    mate != null && mate != 0 -> "M${kotlin.math.abs(mate)}"
    cp != null -> (if (cp >= 0) "+" else "−") + String.format(java.util.Locale.UK, "%.1f", kotlin.math.abs(cp) / 100.0)
    else -> null
}

/** Who the engine plays, and how strongly. */
@Composable
private fun OpponentPicker(opponent: PieceColor?, strength: Int?, onOpponent: (PieceColor?) -> Unit, onStrength: (Int?) -> Unit) {
    Column {
        CtGroupLabel("The engine plays")
        CtSegmented(options = listOf(null, PieceColor.WHITE, PieceColor.BLACK), selected = opponent, label = { it?.let { c -> if (c == PieceColor.WHITE) "White" else "Black" } ?: "Nobody" }, onSelect = onOpponent)
        if (opponent != null) {
            CtGroupLabel("Strength")
            CtSegmented(options = EXPLORE_STRENGTHS, selected = strength, label = { it?.toString() ?: "Full" }, onSelect = onStrength)
        }
    }
}
