package com.chesstutor.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.AnalysisMoveList
import com.chesstutor.app.board.AnalysisSession
import com.chesstutor.app.board.AnalysisToolbar
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.EngineLinesPanel
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.MoveMark
import com.chesstutor.app.board.toggleArrow
import com.chesstutor.app.core.Move
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.Position
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBadge
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.MoveQuality
import com.chesstutor.app.domain.account.ReviewedMove
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import java.util.Locale
import kotlin.math.roundToInt

/** What the review screen can do. */
data class ReviewActions(
    val onAnalyse: () -> Unit = {},
    val onAnalyseAgain: () -> Unit = {},
    val onStop: () -> Unit = {},
    /** "Practise this mistake": adds the position to the mistakes review. */
    val onPractise: (ReviewedMove) -> Unit = {},
    val onFeedback: (BoardFeedback) -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * Game review (plan 6.4) on the board in review mode (`BoardConfig.REVIEW`): eval bar,
 * best-move arrows, the move list with each move's quality, the evaluation graph, engine
 * lines, accuracy and move counts for both sides, and the player's key moments with
 * "Practise this mistake". Any move can be tried on the board as a side line.
 */
@Composable
fun GameReviewScreen(
    state: ReviewState,
    settings: AppSettings,
    engine: EngineService?,
    actions: ReviewActions,
    modifier: Modifier = Modifier,
) {
    val game = state.game
    CtScreenBackground(modifier) {
        if (game == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Game review", subtitle = "This game is no longer saved", onBack = actions.onBack) }
            return@CtScreenBackground
        }
        ReviewContent(game, state, settings, engine, actions)
    }
}

@Composable
private fun ReviewContent(game: GameSummary, state: ReviewState, settings: AppSettings, engine: EngineService?, actions: ReviewActions) {
    val startFen = game.startFen ?: Position.STARTING_FEN
    // The game is the main line; the node id of each of its positions is kept to match plies.
    val (session, mainIds) = remember(game.id) {
        val start = Position.tryFromFen(startFen).getOrElse { Position.initial() }
        val s = AnalysisSession(start)
        val ids = mutableListOf(s.currentId)
        for (uci in game.moves) {
            val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: break
            if (s.state.value.legalMoves.none { it.uci == move.uci }) break
            s.onMove(move)
            ids += s.currentId
        }
        s.toStart()
        s to ids.toList()
    }
    val board by session.state.collectAsState()
    val view by session.view.collectAsState()
    var whiteAtBottom by rememberSaveable { mutableStateOf(game.userColor == PieceColor.WHITE) }
    val ply = mainIds.indexOf(view.currentId).takeIf { it >= 0 }
    val review = state.review
    val cached = ply?.let { state.evals[it] }
    val practised = remember(game.id) { mutableStateListOf<Int>() }
    var drawing by remember { mutableStateOf(false) }
    val drawn = remember(game.id) { mutableStateMapOf<Int, List<BoardArrow>>() }

    // Live engine lines for side lines and positions not analysed yet (not while the analysis runs: it needs the engine).
    var live by remember { mutableStateOf<List<EngineLine>>(emptyList()) }
    LaunchedEffect(board.version, cached == null, state.job == null) {
        live = emptyList()
        if (engine == null || cached != null || state.job != null || board.legalMoves.isEmpty()) return@LaunchedEffect
        val fen = board.position.toFen()
        for (movetime in listOf(300, 1200)) {
            val result = engine.analyse(fen, lines = 3, movetimeMs = movetime)
            if (result.isNotEmpty()) live = result
        }
    }
    val lines = if (cached != null && cached.bestMove.isNotEmpty()) {
        listOf(EngineLine(rank = 1, moves = cached.line.ifEmpty { listOf(cached.bestMove) }, centipawns = cached.centipawns, mateInMoves = cached.mate, depth = cached.depth))
    } else {
        live
    }
    val evalCp = cached?.centipawns ?: lines.firstOrNull()?.centipawns
    val evalMate = cached?.mate ?: lines.firstOrNull()?.mateInMoves
    val bestArrow = lines.firstOrNull()?.firstMove?.let { runCatching { Move.fromUci(it) }.getOrNull() }?.let { BoardArrow(it.from, it.to, MarkKind.BEST) }
    val marks = remember(review, mainIds) { review?.moves?.associate { mainIds[it.ply] to markOf(it.quality) }.orEmpty() }

    fun goToPly(target: Int) {
        mainIds.getOrNull(target)?.let(session::goTo)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CtTopBar(title = "Game review", subtitle = "vs ${game.opponent} · ${resultTitle(game)}", onBack = actions.onBack)
        Column(
            modifier = Modifier.padding(horizontal = CtSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            AnalysisStatus(state, review, actions)
            BoardWithEvalBar(
                evalBar = { barModifier -> BoardEvalBar(evalCp, evalMate, whiteAtBottom, barModifier) },
            ) { boardModifier ->
                BoardHost(
                    session = session,
                    theme = settings.boardTheme,
                    modifier = boardModifier,
                    whiteAtBottom = whiteAtBottom,
                    showCoordinates = settings.showCoordinates,
                    showLegalDots = settings.showLegalDots,
                    autoQueen = settings.autoQueen,
                    arrows = listOfNotNull(bestArrow) + drawn[view.currentId].orEmpty(),
                    onFeedback = actions.onFeedback,
                    drawMode = drawing,
                    onDrawArrow = { from, to ->
                        drawn[view.currentId] = toggleArrow(drawn[view.currentId].orEmpty(), BoardArrow(from, to, MarkKind.USER))
                    },
                )
            }
            MoveVerdict(ply, review, mainIds.size - 1)
            AnalysisToolbar(
                view = view,
                drawing = drawing,
                onStart = session::toStart,
                onBack = session::back,
                onForward = session::forward,
                onEnd = session::toEnd,
                onToggleDrawing = { drawing = !drawing },
            )
            if (review != null && review.graph.isNotEmpty()) {
                EvalGraph(
                    points = review.graph,
                    totalPositions = mainIds.size,
                    currentPly = ply,
                    qualities = review.moves.associate { it.ply to it.quality },
                    onSelect = ::goToPly,
                )
            }
            AnalysisMoveList(view = view, onSelect = session::goTo, marks = marks)
            if (lines.isNotEmpty() || board.legalMoves.isNotEmpty()) {
                EngineLinesPanel(
                    position = board.position,
                    lines = lines,
                    engineName = if (cached != null) "Stockfish (saved)" else "Stockfish",
                    onPlayLine = { line ->
                        line.firstMove?.let { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
                            ?.takeIf { move -> board.legalMoves.any { it.uci == move.uci } }
                            ?.let { actions.onFeedback(session.onMove(it)) }
                    },
                )
            }
            CtButton(text = "Flip board", icon = Icons.Rounded.SwapVert, style = CtButtonStyle.Secondary, onClick = { whiteAtBottom = !whiteAtBottom })

            if (review != null && review.moves.isNotEmpty()) {
                CtSectionHeader(title = "Summary")
                SummaryCard(review, game.userColor)
                val moments = review.keyMoments(game.userColor)
                CtSectionHeader(title = "Key moments")
                if (moments.isEmpty()) {
                    Text(
                        if (review.complete) "No mistakes or blunders from you in this game. Well played!" else "Your mistakes show here as the analysis finds them.",
                        style = CtType.BodySmall,
                    )
                }
                moments.forEach { moment ->
                    KeyMoment(
                        move = moment,
                        added = moment.ply in practised,
                        onShow = { goToPly(moment.ply) },
                        onPractise = {
                            practised += moment.ply
                            actions.onPractise(moment)
                        },
                    )
                }
            }
            CtSectionHeader(title = "Opening")
            CtCard(modifier = Modifier.fillMaxWidth()) {
                Text(game.openingName ?: "Not named", style = CtType.TitleSmall)
                Text(bookLine(game), style = CtType.BodySmall)
            }
            Spacer(Modifier.height(CtSpacing.lg))
        }
    }
}

@Composable
private fun AnalysisStatus(state: ReviewState, review: GameReview?, actions: ReviewActions) {
    val job = state.job
    when {
        job?.error != null -> CtCard(modifier = Modifier.fillMaxWidth()) {
            Text(job.error, style = CtType.BodySmall)
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = "Try again", icon = Icons.Rounded.Refresh, onClick = actions.onAnalyse, modifier = Modifier.fillMaxWidth())
        }
        job != null -> CtCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (job.waiting) "Waiting for another analysis to finish…" else "Analysing position ${job.done} of ${job.total}",
                style = CtType.Label,
            )
            Spacer(Modifier.height(CtSpacing.sm))
            CtProgressBar(progress = job.progress, height = 8.dp)
            Spacer(Modifier.height(CtSpacing.sm))
            Text("It carries on if you go back to your games.", style = CtType.Caption)
            CtButton(text = "Stop", style = CtButtonStyle.Secondary, onClick = actions.onStop, modifier = Modifier.fillMaxWidth())
        }
        review == null || !review.complete -> CtCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtIconBubble(icon = Icons.Rounded.Insights, tint = CtColors.Teal, size = 40.dp)
                Spacer(Modifier.width(CtSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(if (review?.moves.isNullOrEmpty()) "Analyse this game" else "Finish the analysis", style = CtType.TitleSmall)
                    Text("Stockfish checks every position and marks each move from best to blunder.", style = CtType.Caption)
                }
            }
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = "Analyse", icon = Icons.Rounded.Insights, onClick = actions.onAnalyse, modifier = Modifier.fillMaxWidth())
        }
        state.summary?.lite == true -> CtCard(modifier = Modifier.fillMaxWidth()) {
            CtBadge(text = "Checked by the Lite engine", icon = Icons.Rounded.Warning)
            Spacer(Modifier.height(CtSpacing.xs))
            Text("Stockfish was not running, so some verdicts may be wrong.", style = CtType.Caption)
            CtButton(text = "Analyse again", icon = Icons.Rounded.Refresh, style = CtButtonStyle.Secondary, onClick = actions.onAnalyseAgain, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** What the move that led here was worth, or a hint at the start and in side lines. */
@Composable
private fun MoveVerdict(ply: Int?, review: GameReview?, lastPly: Int) {
    val move = ply?.takeIf { it > 0 }?.let { review?.move(it) }
    CtCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(CtSpacing.md)) {
        when {
            ply == null -> Text("A side line: the engine follows your moves. Tap a move of the game to go back.", style = CtType.BodySmall)
            ply == 0 -> Text("The starting position. Step forward, or tap the graph to jump.", style = CtType.BodySmall)
            move == null -> Text(if (ply <= lastPly) "This move is not analysed yet." else "The end of the game.", style = CtType.BodySmall)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                QualityBadge(move.quality)
                Spacer(Modifier.width(CtSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text("${move.numberLabel} ${move.san}", style = CtType.TitleSmall)
                    Text(verdictText(move), style = CtType.BodySmall)
                }
            }
        }
    }
}

internal fun verdictText(move: ReviewedMove): String {
    val best = move.bestSan
    val chances = "Winning chances ${percent(move.winBefore)} → ${percent(move.winAfter)}."
    return when (move.quality) {
        MoveQuality.BOOK -> "A known opening move."
        MoveQuality.BEST -> "The best move."
        MoveQuality.GOOD -> if (best != null) "A good move. $best was a little better." else "A good move."
        else -> listOfNotNull(best?.let { "Best was $it." }, chances).joinToString(" ")
    }
}

private fun percent(value: Double): String = "${value.roundToInt()}%"

@Composable
internal fun QualityBadge(quality: MoveQuality, modifier: Modifier = Modifier) {
    val (bg, fg) = qualityColors(quality)
    CtBadge(text = quality.label, color = bg, contentColor = fg, modifier = modifier)
}

private fun qualityColors(quality: MoveQuality): Pair<Color, Color> = when (quality) {
    MoveQuality.BOOK -> CtColors.SurfaceHigh to CtColors.Text
    MoveQuality.BEST -> CtColors.Lime to CtColors.OnLime
    MoveQuality.GOOD -> CtColors.Teal to CtColors.OnTeal
    MoveQuality.INACCURACY -> CtColors.Gold to CtColors.OnGold
    MoveQuality.MISTAKE, MoveQuality.BLUNDER -> CtColors.Coral to CtColors.OnCoral
}

private fun markOf(quality: MoveQuality): MoveMark = MoveMark(
    symbol = quality.symbol,
    color = when (quality) {
        MoveQuality.BEST -> CtColors.Lime
        MoveQuality.INACCURACY -> CtColors.Gold
        MoveQuality.MISTAKE, MoveQuality.BLUNDER -> CtColors.Coral
        else -> CtColors.Text2
    },
    spoken = quality.label,
)

@Composable
private fun SummaryCard(review: GameReview, player: PieceColor) {
    val mine = review.report(player)
    val theirs = review.report(player.opposite())
    CtCard(modifier = Modifier.fillMaxWidth()) {
        SummaryRow("", "You", "Opponent", header = true)
        SummaryRow("Accuracy", accuracyText(mine.accuracy), accuracyText(theirs.accuracy), header = true)
        MoveQuality.entries.forEach { q ->
            SummaryRow(q.label, "${mine.count(q)}", "${theirs.count(q)}")
        }
        if (!review.complete) {
            Text("Counting so far: the analysis is not finished.", style = CtType.Caption)
        }
    }
}

@Composable
private fun SummaryRow(label: String, mine: String, theirs: String, header: Boolean = false) {
    val style = if (header) CtType.Label else CtType.BodySmall
    Row(Modifier.fillMaxWidth().padding(vertical = CtSpacing.xxs)) {
        Text(label, style = style, modifier = Modifier.weight(1.4f))
        Text(mine, style = style, modifier = Modifier.weight(1f))
        Text(theirs, style = style, modifier = Modifier.weight(1f))
    }
}

internal fun accuracyText(accuracy: Double?): String =
    accuracy?.let { String.format(Locale.UK, "%.1f%%", it) } ?: "–"

@Composable
private fun KeyMoment(move: ReviewedMove, added: Boolean, onShow: () -> Unit, onPractise: () -> Unit) {
    CtCard(onClick = onShow, onClickLabel = "Show this move", modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QualityBadge(move.quality)
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("${move.numberLabel} ${move.san}", style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(verdictText(move), style = CtType.BodySmall)
            }
        }
        Spacer(Modifier.height(CtSpacing.sm))
        CtButton(
            text = if (added) "Added to Mistakes review" else "Practise this mistake",
            icon = Icons.Rounded.School,
            style = CtButtonStyle.Secondary,
            enabled = !added,
            onClick = onPractise,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
