package com.chesstutor.app.feature.account

import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.design.GradeColor
import com.chesstutor.app.design.CtSpeechBubble
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtBoardScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.chesstutor.app.board.AnalysisSession
import com.chesstutor.app.board.BoardArrow
import com.chesstutor.app.board.BoardEvalBar
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.BoardWithEvalBar
import com.chesstutor.app.board.EngineLinesPanel
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.NotationToken
import com.chesstutor.app.board.SquareBadge
import com.chesstutor.app.board.moveListOf
import com.chesstutor.app.board.toggleArrow
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
import com.chesstutor.app.design.CtIconBubble
import com.chesstutor.app.design.CtMascotArt
import com.chesstutor.app.design.CtProgressBar
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtSectionHeader
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.Mascot
import com.chesstutor.app.domain.account.GameReview
import com.chesstutor.app.domain.account.GameSummary
import com.chesstutor.app.domain.account.MoveQuality
import com.chesstutor.app.domain.account.ReviewCommentary
import com.chesstutor.app.domain.account.ReviewedMove
import com.chesstutor.app.engine.EngineLine
import com.chesstutor.app.engine.EngineService
import java.util.Locale

/** What the review screen can do. */
data class ReviewActions(
    val onAnalyse: () -> Unit = {},
    val onAnalyseAgain: () -> Unit = {},
    val onStop: () -> Unit = {},
    /** "Practise this mistake": adds the position to the mistakes review. */
    val onPractise: (ReviewedMove) -> Unit = {},
    val onFeedback: (BoardFeedback) -> Unit = {},
    val onBack: () -> Unit = {},
    /** "Explore from here": Explore on this position, with the rest of the game as its main line. */
    val onExplore: (fen: String, line: List<String>) -> Unit = { _, _ -> },
)

/**
 * Game review (plan 6.4), in two steps like the big sites: a summary (the coach's verdict,
 * the evaluation graph, both players' accuracy and move-quality counts, key moments), then
 * "Start review" walks through the game on the board in review mode (`BoardConfig.REVIEW`):
 * the coach comments on each move, the move is tinted by its quality, a horizontal move list
 * follows the game, and "Best" plays the engine's move as a side line.
 */
@Composable
fun GameReviewScreen(
    state: ReviewState,
    settings: AppSettings,
    engine: EngineService?,
    actions: ReviewActions,
    modifier: Modifier = Modifier,
    /** Opens straight on the board at this ply (tests and deep links); null shows the summary. */
    startPly: Int? = null,
) {
    val game = state.game
    CtScreenBackground(modifier) {
        if (game == null) {
            Column(Modifier.fillMaxSize()) { CtTopBar(title = "Game review", subtitle = "This game is no longer saved", onBack = actions.onBack) }
            return@CtScreenBackground
        }
        var boardPly by rememberSaveable { mutableStateOf(startPly ?: -1) }
        if (boardPly < 0) {
            ReviewSummary(game, state, settings.mascot, actions, onStart = { ply -> boardPly = ply })
        } else {
            ReviewBoard(game, state, settings, engine, actions, startPly = boardPly, onSummary = { boardPly = -1 })
        }
    }
}

// ------------------------------------------------------------------ summary

@Composable
private fun BoxScope.ReviewSummary(game: GameSummary, state: ReviewState, mascot: Mascot, actions: ReviewActions, onStart: (Int) -> Unit) {
    val review = state.review
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val practised = remember(game.id) { mutableStateListOf<Int>() }
    val white = if (game.userColor == PieceColor.WHITE) "You" else game.opponent
    val black = if (game.userColor == PieceColor.BLACK) "You" else game.opponent

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        CtTopBar(title = "Game review", subtitle = "vs ${game.opponent}, ${resultTitle(game)}", onBack = actions.onBack)
        Column(Modifier.padding(horizontal = CtSpacing.gutter), verticalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            AnalysisStatus(state, review, actions)
            // While the analysis runs, the graph growing across the width is the progress.
            if (review != null && (review.moves.isNotEmpty() || state.job?.running == true)) {
                CoachSays(mascot) {
                    Text(
                        if (state.job?.running == true && !review.complete) "I'm going through your game move by move. The graph fills in as I go."
                        else ReviewCommentary.summary(review, game.userColor, game.outcome),
                        style = CtType.Body.copy(color = CtColors.Text),
                    )
                }
                EvalGraph(
                    points = review.graph,
                    totalPositions = review.totalPlies + 1,
                    currentPly = selected,
                    qualities = review.moves.associate { it.ply to it.quality },
                    onSelect = { ply -> selected = ply },
                )
                GraphReadout(review, state, selected, onOpen = onStart)
                PlayersAndCounts(review, white, black)
                val moments = review.keyMoments(game.userColor)
                if (moments.isNotEmpty()) {
                    CtSectionHeader(title = "Key moments")
                    moments.forEach { moment ->
                        KeyMoment(
                            move = moment,
                            added = moment.ply in practised,
                            onShow = { onStart(moment.ply) },
                            onPractise = {
                                practised += moment.ply
                                actions.onPractise(moment)
                            },
                        )
                    }
                }
            }
            CtCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtIconBubble(icon = Icons.AutoMirrored.Rounded.MenuBook, tint = CtColors.Teal, size = 36.dp)
                    Spacer(Modifier.width(CtSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(game.openingName ?: "Opening not named", style = CtType.Label)
                        Text(bookLine(game), style = CtType.Caption)
                    }
                }
            }
        }
    }
    CtButton(
        text = "Start review",
        icon = Icons.Rounded.PlayArrow,
        onClick = { onStart(1.coerceAtMost(review?.totalPlies ?: 0)) },
        enabled = game.moves.isNotEmpty(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(CtSpacing.gutter)
            .fillMaxWidth(),
    )
}

/** What the graph shows at the tapped point: the move, its quality and the evaluation. */
@Composable
private fun GraphReadout(review: GameReview, state: ReviewState, selected: Int?, onOpen: (Int) -> Unit) {
    val ply = selected ?: return
    val move = review.move(ply)
    Row(
        modifier = Modifier.fillMaxWidth().clip(CtShapes.CardSmall).background(CtColors.Surface).padding(CtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
    ) {
        if (move != null) QualityIcon(move.quality, size = 28)
        Column(Modifier.weight(1f)) {
            Text(move?.let { "${it.numberLabel} ${it.san}: ${it.quality.label.lowercase()}" } ?: "Start of the game", style = CtType.Label)
            Text("Evaluation ${ReviewCommentary.evalLabel(state.evals[ply]).ifEmpty { "–" }}, White's chances ${percent(review.graph.getOrNull(ply)?.times(100.0))}", style = CtType.Caption)
        }
        CtChip(text = "Show", onClick = { onOpen(ply) })
    }
}

@Composable
private fun PlayersAndCounts(review: GameReview, white: String, black: String) {
    val w = review.report(PieceColor.WHITE)
    val b = review.report(PieceColor.BLACK)
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Players", style = CtType.Label, modifier = Modifier.weight(1f))
            PlayerName(white, Modifier.width(COLUMN))
            Spacer(Modifier.width(GAP))
            PlayerName(black, Modifier.width(COLUMN))
        }
        Spacer(Modifier.height(CtSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Accuracy", style = CtType.Label, modifier = Modifier.weight(1f))
            AccuracyBox(w.accuracy, light = true, modifier = Modifier.width(COLUMN))
            Spacer(Modifier.width(GAP))
            AccuracyBox(b.accuracy, light = false, modifier = Modifier.width(COLUMN))
        }
        Spacer(Modifier.height(CtSpacing.md))
        SUMMARY_ORDER.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = CtSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Text(q.label, style = CtType.BodySmall.copy(color = CtColors.Text), modifier = Modifier.weight(1f))
                // Each count sits right under its player's accuracy box, the icon in the gap between.
                CountText(w.count(q), countColor(q), Modifier.width(COLUMN))
                Box(Modifier.width(GAP), contentAlignment = Alignment.Center) { QualityIcon(q, size = 26) }
                CountText(b.count(q), countColor(q), Modifier.width(COLUMN))
            }
        }
        if (!review.complete) Text("Counting so far: the analysis is not finished.", style = CtType.Caption)
    }
}

/** The summary's rows, best first, as Chess.com lists them. */
private val SUMMARY_ORDER = listOf(
    MoveQuality.BRILLIANT, MoveQuality.GREAT, MoveQuality.BEST, MoveQuality.EXCELLENT, MoveQuality.GOOD, MoveQuality.BOOK,
    MoveQuality.FORCED, MoveQuality.INACCURACY, MoveQuality.MISS, MoveQuality.MISTAKE, MoveQuality.BLUNDER,
)

/** A count's colour: the quality's own, except Book's neutral chip colour, which would not read as text. */
@Composable
@ReadOnlyComposable
private fun countColor(quality: MoveQuality): Color = if (quality == MoveQuality.BOOK) CtColors.Text2 else qualityTextOnPage(quality)

/** The two player columns of the summary, and the gap between them that holds the quality icons. */
private val COLUMN = 88.dp
private val GAP = 36.dp

@Composable
private fun PlayerName(name: String, modifier: Modifier) {
    Text(name, style = CtType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun AccuracyBox(accuracy: Double?, light: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier.clip(CtShapes.Chip).background(if (light) CtColors.Text else CtColors.BgDeep).padding(vertical = CtSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            accuracy?.let { String.format(Locale.UK, "%.1f", it) } ?: "–",
            style = CtType.Number.copy(color = if (light) CtColors.BgDeep else CtColors.Text),
        )
    }
}

@Composable
private fun CountText(count: Int, color: Color, modifier: Modifier) {
    Text("$count", style = CtType.Label.copy(color = if (count == 0) CtColors.Text3 else color), textAlign = TextAlign.Center, modifier = modifier)
}

// ------------------------------------------------------------------ board

@Composable
private fun ReviewBoard(
    game: GameSummary,
    state: ReviewState,
    settings: AppSettings,
    engine: EngineService?,
    actions: ReviewActions,
    startPly: Int,
    onSummary: () -> Unit,
) {
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
        s.goTo(ids[startPly.coerceIn(0, ids.lastIndex)])
        s to ids.toList()
    }
    val entries = remember(game.id) {
        val start = Position.tryFromFen(startFen).getOrElse { Position.initial() }
        moveListOf(start, game.moves.mapNotNull { runCatching { Move.fromUci(it) }.getOrNull() })
    }
    val board by session.state.collectAsState()
    val view by session.view.collectAsState()
    var whiteAtBottom by rememberSaveable { mutableStateOf(game.userColor == PieceColor.WHITE) }
    var showLines by rememberSaveable { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    val drawn = remember(game.id) { mutableStateMapOf<Int, List<BoardArrow>>() }
    val ply = mainIds.indexOf(view.currentId).takeIf { it >= 0 }
    val review = state.review
    val move = ply?.takeIf { it > 0 }?.let { review?.move(it) }
    val cached = ply?.let { state.evals[it] }

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
    val tint = move?.let { qualityColor(it.quality) }
    val badge = move?.let { m ->
        runCatching { Move.fromUci(m.uci) }.getOrNull()?.let { SquareBadge(it.to, qualityColor(m.quality).toArgb().toLong() and 0xFFFFFFFFL, badgeText(m.quality), qualityTextColor(m.quality).toArgb().toLong() and 0xFFFFFFFFL) }
    }

    fun goToPly(target: Int) {
        mainIds.getOrNull(target)?.let(session::goTo)
    }

    // Play: steps through the game and stops at the next key moment (a capture, check,
    // castling or a slip), the end, or a side line.
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        while (playing) {
            delay(PLAY_STEP_MS)
            if (!session.view.value.canGoForward) break
            session.forward()
            val reached = mainIds.indexOf(session.currentId)
            if (reached <= 0 || isKeyMoment(entries.getOrNull(reached - 1)?.san.orEmpty(), review?.move(reached)?.quality)) break
        }
        playing = false
    }

    val (headline, detail) = reviewComment(game, state, ply, move)
    val san = ply?.takeIf { it > 0 }?.let { p -> entries.getOrNull(p - 1)?.let { e -> listOfNotNull(e.numberLabel, e.san).joinToString(" ") } }
    CtBoardScreen(
        title = "Game review",
        onBack = actions.onBack,
        actions = { CtChip(text = "Summary", onClick = onSummary) },
        status = {
            // The verdict on this move: its grade, what it was, and the evaluation after it.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
                if (move != null) QualityIcon(move.quality, size = 30)
                Column(Modifier.weight(1f)) {
                    Text(headline, style = CtType.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (san != null) Text(san, style = CtType.Caption, maxLines = 1)
                }
                if (move != null && ply != null) {
                    Text(
                        ReviewCommentary.evalLabel(state.evals[ply]),
                        style = CtType.Label,
                        modifier = Modifier.clip(CtShapes.Chip).background(CtColors.Surface).padding(horizontal = 10.dp, vertical = CtSpacing.xs),
                    )
                }
            }
        },
        board = {
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
                    arrows = drawn[view.currentId].orEmpty(),
                    onFeedback = actions.onFeedback,
                    drawMode = drawing,
                    onDrawArrow = { from, to -> drawn[view.currentId] = toggleArrow(drawn[view.currentId].orEmpty(), BoardArrow(from, to, MarkKind.USER)) },
                    lastMoveColor = tint?.copy(alpha = 0.55f)?.toArgb()?.toLong()?.and(0xFFFFFFFFL),
                    badge = badge,
                )
            }
        },
        controls = {
            CtIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back one move",
                enabled = view.canGoBack,
                onClick = { playing = false; session.back() },
            )
            CtIconButton(
                icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play to the next key moment",
                enabled = playing || view.canGoForward,
                onClick = { playing = !playing },
            )
            CtButton(
                text = "Next",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                enabled = view.canGoForward,
                onClick = { playing = false; session.forward() },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Next move" },
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = CtSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            MoveStrip(entries, review, current = ply, onSelect = ::goToPly)
            CoachSays(settings.mascot, mood = moodFor(move)) {
                Text(detail, style = CtType.Chat)
                Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                    if (move != null && canShowBest(move)) {
                        CtChip(text = "Show best", onClick = {
                            playing = false
                            goToPly(move.ply - 1)
                            runCatching { Move.fromUci(move.bestMove) }.getOrNull()
                                ?.takeIf { best -> session.state.value.legalMoves.any { it.uci == best.uci } }
                                ?.let { actions.onFeedback(session.onMove(it)) }
                        })
                    }
                    CtChip(text = "Explore from here", onClick = {
                        playing = false
                        // On the game's line, what really happened next comes along to compare.
                        actions.onExplore(board.position.toFen(), ply?.let { game.moves.drop(it) }.orEmpty())
                    })
                    if (ply == null) {
                        CtChip(text = "Back to the game", onClick = {
                            goToPly(backToGamePly(mainIds, view.tokens.map { (it as? NotationToken.MoveToken)?.nodeId }, view.currentId))
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
                CtChip(text = "Flip", icon = Icons.Rounded.SwapVert, onClick = { whiteAtBottom = !whiteAtBottom })
                CtChip(text = "Engine lines", selected = showLines, onClick = { showLines = !showLines })
                CtChip(text = "Draw", selected = drawing, onClick = { drawing = !drawing })
            }
            if (showLines) {
                EngineLinesPanel(
                    position = board.position,
                    lines = lines,
                    engineName = if (cached != null) "Stockfish (saved)" else "Stockfish",
                    onPlayLine = { line ->
                        line.firstMove?.let { uci -> runCatching { Move.fromUci(uci) }.getOrNull() }
                            ?.takeIf { m -> board.legalMoves.any { it.uci == m.uci } }
                            ?.let { actions.onFeedback(session.onMove(it)) }
                    },
                )
            }
        }
    }
}

/** The game ply to return to from a side line: the last main-line node before it in the notation. */
internal fun backToGamePly(mainIds: List<Int>, notationIds: List<Int?>, current: Int?): Int {
    val upTo = notationIds.indexOf(current).takeIf { it >= 0 } ?: return 0
    return notationIds.take(upTo + 1).filterNotNull().mapNotNull { id -> mainIds.indexOf(id).takeIf { it >= 0 } }.maxOrNull() ?: 0
}

/** The verdict (one line, above the board) and the coach's explanation (below it) for a ply. */
@Composable
private fun reviewComment(game: GameSummary, state: ReviewState, ply: Int?, move: ReviewedMove?): Pair<String, String> = when {
    ply == null -> "A side line" to "Try any moves: the engine follows along. Go back to the game when you're done."
    ply == 0 -> "The starting position" to "Step forward to hear about each move."
    move == null -> "Not analysed yet" to "Run the analysis from the summary to see what each move was worth."
    else -> {
        val positionAfter = remember(game.id, ply) { GamePositions.after(game, ply) }
        val comment = ReviewCommentary.comment(move, state.evals[ply], positionAfter, game.openingName)
        comment.headline to comment.detail
    }
}

/** The coach's face for a move: proud of the best moves, kind after a slip. */
private fun moodFor(move: ReviewedMove?): MascotMood = when (move?.quality) {
    MoveQuality.BRILLIANT, MoveQuality.GREAT, MoveQuality.BEST -> MascotMood.PROUD
    MoveQuality.INACCURACY, MoveQuality.MISS, MoveQuality.MISTAKE, MoveQuality.BLUNDER -> MascotMood.KIND
    else -> MascotMood.READY
}

/** Positions of a saved game, replayed when a comment needs one. */
internal object GamePositions {
    fun after(game: GameSummary, ply: Int): Position? =
        com.chesstutor.app.domain.account.GameReviewer.positions(game.startFen ?: Position.STARTING_FEN, game.moves).getOrNull(ply)
}

/** The game's moves in one scrolling row that keeps the current move in view (like the big sites). */
@Composable
private fun MoveStrip(entries: List<com.chesstutor.app.board.MoveListEntry>, review: GameReview?, current: Int?, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        current?.takeIf { it > 0 }?.let { listState.animateScrollToItem((it - 2).coerceAtLeast(0)) }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().clip(CtShapes.CardSmall).background(CtColors.Surface),
        contentPadding = PaddingValues(horizontal = CtSpacing.sm, vertical = CtSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(entries) { index, entry ->
            val ply = index + 1
            val quality = review?.move(ply)?.quality
            val selected = ply == current
            Row(
                modifier = Modifier
                    .clip(CtShapes.Chip)
                    .background(if (selected) CtColors.Primary else CtColors.Surface)
                    .ctPressable(onClickLabel = "Go to ${entry.san}") { onSelect(ply) }
                    .padding(horizontal = CtSpacing.xs, vertical = CtSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entry.numberLabel?.let { Text("$it ", style = CtType.Caption.copy(color = if (selected) CtColors.OnPrimary else CtColors.Text2)) }
                Text(entry.san, style = CtType.Label.copy(color = if (selected) CtColors.OnPrimary else CtColors.Text))
                quality?.symbol?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = CtType.Label.copy(color = if (selected) CtColors.OnPrimary else qualityTextOnPage(quality)))
                }
            }
        }
    }
}

/** The coach in a [mood] beside a tailed speech bubble holding [content] (D41). */
@Composable
private fun CoachSays(mascot: Mascot, mood: MascotMood = MascotMood.READY, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs)) {
        CtMascotArt(mascot = mascot, mood = mood, height = 52.dp, modifier = Modifier.size(52.dp))
        CtSpeechBubble(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) { content() }
        }
    }
}

// ------------------------------------------------------------------ shared pieces

@Composable
private fun AnalysisStatus(state: ReviewState, review: GameReview?, actions: ReviewActions) {
    val job = state.job
    when {
        job?.error != null -> CtCard(modifier = Modifier.fillMaxWidth()) {
            Text(job.error, style = CtType.BodySmall)
            Spacer(Modifier.height(CtSpacing.sm))
            CtButton(text = "Try again", icon = Icons.Rounded.Refresh, onClick = actions.onAnalyse, modifier = Modifier.fillMaxWidth())
        }
        // Running: no card, the graph filling in shows the progress (review of Phase 5).
        job != null -> Unit
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

/** A move quality's round icon, in its colour (book, star, thumbs up, "?!", "?", "??"). */
@Composable
internal fun QualityIcon(quality: MoveQuality, size: Int = 24, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CtShapes.Pill)
            .background(qualityColor(quality))
            .semantics { contentDescription = quality.label },
        contentAlignment = Alignment.Center,
    ) {
        val tint = qualityTextColor(quality)
        when (quality) {
            MoveQuality.BOOK -> Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
            MoveQuality.BEST -> Icon(Icons.Rounded.Star, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.65f).dp))
            MoveQuality.EXCELLENT -> Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
            MoveQuality.GOOD -> Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
            MoveQuality.FORCED -> Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
            MoveQuality.MISS -> Icon(Icons.Rounded.Close, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
            else -> Text(quality.symbol, style = CtType.Label.copy(color = tint))
        }
    }
}

internal fun badgeText(quality: MoveQuality): String = when (quality) {
    MoveQuality.BOOK -> "≡"
    MoveQuality.BEST -> "★"
    MoveQuality.EXCELLENT -> "✓✓"
    MoveQuality.GOOD -> "✓"
    MoveQuality.FORCED -> "→"
    MoveQuality.MISS -> "✕"
    else -> quality.symbol
}

private fun MoveQuality.grade(): GradeColor = GradeColor.valueOf(name)

/** A grade's fill: badges, the board's last-move tint, the graph's dots. */
@Composable
@ReadOnlyComposable
internal fun qualityColor(quality: MoveQuality): Color = Color(quality.grade().fill(CtColors.IsDark))

/** The symbol or icon on a grade's fill. */
@Composable
@ReadOnlyComposable
internal fun qualityTextColor(quality: MoveQuality): Color = Color(quality.grade().ink(CtColors.IsDark))

/** A grade written on the page (counts, symbols in the move list), readable on every background. */
@Composable
@ReadOnlyComposable
internal fun qualityTextOnPage(quality: MoveQuality): Color = Color(quality.grade().text(CtColors.IsDark))

private fun percent(value: Double?): String = value?.let { "${Math.round(it)}%" } ?: "–"

internal fun accuracyText(accuracy: Double?): String =
    accuracy?.let { String.format(Locale.UK, "%.1f%%", it) } ?: "–"

@Composable
private fun KeyMoment(move: ReviewedMove, added: Boolean, onShow: () -> Unit, onPractise: () -> Unit) {
    CtCard(onClick = onShow, onClickLabel = "Show this move", contentPadding = PaddingValues(CtSpacing.md), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QualityIcon(move.quality, size = 28)
            Spacer(Modifier.width(CtSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("${move.numberLabel} ${move.san}", style = CtType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(move.bestSan?.let { "Best was $it" } ?: move.quality.label, style = CtType.Caption)
            }
            CtChip(
                text = if (added) "Added" else "Practise",
                color = CtColors.Teal,
                icon = Icons.Rounded.School,
                selected = added,
                onClick = { if (!added) onPractise() },
            )
        }
    }
}

/** The coach bubble's height on the board page: enough for a headline, three lines and a chip. */
private const val PLAY_STEP_MS = 800L

/** "Show best" is offered on every move that was not the best one (book and good moves too). */
internal fun canShowBest(move: ReviewedMove): Boolean =
    !move.quality.isTop && move.bestMove.isNotEmpty() && move.bestMove != move.uci

/** Where Play stops: a capture, a check or mate, castling, or a move that cost something. */
internal fun isKeyMoment(san: String, quality: MoveQuality?): Boolean =
    'x' in san || '+' in san || '#' in san || san.startsWith("O-O") || quality?.isError == true
