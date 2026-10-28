package com.chesstutor.app.feature.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.PieceArt
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.formatClock
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.Piece
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.core.PieceType
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtBoardScreen
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtIconButton
import com.chesstutor.app.design.CtStatTile
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.design.ctPressable
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleMode

/**
 * The puzzle player (plan section 6.3.3, Phase 7D, canvas `Puzzle.dc.html`). While a puzzle is
 * on, it is a board screen: who is to move (or "Solved") in the fixed row above the board, the
 * coach below it, Hint and Solution, then Analyse and Next puzzle, at the bottom. A run's
 * result and "no puzzles here" are ordinary pages.
 */
@Composable
fun PuzzlePlayerScreen(
    player: PuzzlePlayer?,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    /** Opens analysis on [fen] with [line] (the solution) as the main line. */
    onAnalyse: (fen: String, line: List<String>) -> Unit,
    onPlayLevel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System back leaves the run the same way as the back button (and stops its clock).
    BackHandler(onBack = onBack)
    if (player == null) {
        CtScreenBackground(modifier) { CtTopBar(title = "Puzzles", onBack = onBack) }
        return
    }
    val state by player.state.collectAsState()
    when (state.phase) {
        PlayerPhase.UNAVAILABLE, PlayerPhase.FINISHED -> CtScreenBackground(modifier) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CtTopBar(title = state.title, onBack = onBack)
                Column(
                    modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
                ) {
                    if (state.phase == PlayerPhase.UNAVAILABLE) {
                        CtEmptyState(
                            icon = Icons.Rounded.Extension,
                            title = "No puzzles here",
                            body = "There are no puzzles for this choice. Try another level or mode.",
                        )
                    } else {
                        RunResult(state, onPlayLevel, onBack)
                    }
                    Spacer(Modifier.height(CtSpacing.lg))
                }
            }
        }
        else -> Playing(player, state, settings, onFeedback, onAnalyse, onBack, modifier)
    }
}

@Composable
private fun Playing(
    player: PuzzlePlayer,
    state: PuzzlePlayerState,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onAnalyse: (String, List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val board = state.board ?: return
    val after = state.phase == PlayerPhase.SOLVED || state.phase == PlayerPhase.REVEALED
    val coachText = when (state.phase) {
        PlayerPhase.SOLVED -> if (state.solvedWithAlternative) "Solved: a great alternative! That move wins too." else "Correct! Well found."
        PlayerPhase.REVEALED -> if (state.showingLine) "Here is the solution." else "Not this time. Have a look at the solution."
        PlayerPhase.SETUP -> "Watch your opponent's move…"
        else -> state.prompt
    }
    val mood = when {
        state.phase == PlayerPhase.SOLVED -> MascotMood.PROUD
        state.phase == PlayerPhase.REVEALED -> MascotMood.KIND
        state.hintSquare != null -> MascotMood.THINKING
        else -> MascotMood.READY
    }
    CtBoardScreen(
        title = state.title,
        onBack = onBack,
        modifier = modifier,
        actions = { ReportMenu(enabled = after && !state.reported, reported = state.reported) { player.reportAnotherMove(note = "Reported from the puzzle player") } },
        status = { PuzzleStatus(state, settings) },
        board = {
            BoardHost(
                session = board,
                theme = settings.boardTheme,
                whiteAtBottom = whiteAtBottom(settings.boardOrientation, state.solverColor),
                showCoordinates = settings.showCoordinates,
                showLegalDots = settings.showLegalDots,
                autoQueen = settings.autoQueen,
                arrows = listOfNotNull(state.hintArrow),
                rings = listOfNotNull(
                    state.hintSquare?.let { SquareRing(it, MarkKind.HINT) },
                    state.mistakeSquare?.let { SquareRing(it, MarkKind.MISTAKE) },
                ),
                onFeedback = onFeedback,
            )
        },
        controls = {
            if (after) {
                CtIconButton(
                    icon = Icons.Rounded.Science,
                    contentDescription = "Analyse",
                    onClick = { onAnalyse(board.solvingFen, board.puzzle.moves.drop(1)) },
                )
                CtButton(
                    text = if (state.isLastOfRun) "See your result" else "Next puzzle",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = player::next,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CtButton(
                    text = "Hint",
                    icon = Icons.Rounded.Lightbulb,
                    style = CtButtonStyle.Secondary,
                    enabled = state.phase == PlayerPhase.SOLVING,
                    onClick = player::hint,
                    modifier = Modifier.weight(1f),
                )
                CtButton(
                    text = "Solution",
                    icon = Icons.Rounded.Visibility,
                    style = CtButtonStyle.Secondary,
                    enabled = state.phase == PlayerPhase.SOLVING,
                    onClick = player::reveal,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = CtSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            CtCoachStrip(mascot = settings.mascot, text = coachText, mood = mood, modifier = Modifier.fillMaxWidth())
            if (after) AfterPuzzle(player, state)
        }
    }
}

/**
 * The fixed row above the board: a small king of the side to move (or a tick once solved), what
 * is happening, the clock and, in a run, which puzzle this is and how the run is going.
 */
@Composable
private fun PuzzleStatus(state: PuzzlePlayerState, settings: AppSettings) {
    val side = if (state.solverColor == PieceColor.WHITE) "White" else "Black"
    val headline = when (state.phase) {
        PlayerPhase.SOLVED -> if (state.solvedWithAlternative) "Solved another way" else "Solved"
        PlayerPhase.REVEALED -> "Solution"
        else -> "$side to move"
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            when (state.phase) {
                PlayerPhase.SOLVED -> Box(
                    modifier = Modifier.size(28.dp).clip(CtShapes.Pill).background(CtColors.Success),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = CtColors.OnSuccess, modifier = Modifier.size(18.dp)) }
                PlayerPhase.REVEALED -> Box(
                    modifier = Modifier.size(28.dp).clip(CtShapes.Pill).background(CtColors.Surface),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Visibility, contentDescription = null, tint = CtColors.Text2, modifier = Modifier.size(16.dp)) }
                else -> Image(
                    painter = painterResource(PieceArt.drawable(settings.boardTheme.pieceSet, Piece(PieceType.KING, state.solverColor))),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(headline, style = CtType.TitleSmall, maxLines = 1, modifier = Modifier.weight(1f))
            state.maxStrikes?.let { max -> Text("Strikes ${state.strikes}/$max", style = CtType.Label.copy(color = CtColors.Error)) }
            state.plannedCount?.let { planned -> Text("${state.index + 1} of $planned", style = CtType.Caption) }
            // A countdown shows tenths when time is short; time taken always reads like "0:07".
            Text(state.remainingMs?.let { formatClock(it) } ?: formatElapsed(state.elapsedMs), style = CtType.Label.copy(color = CtColors.Text2))
        }
        RunProgress(state)
    }
}

/** A thin bar per puzzle of the run: success solved cleanly, accent solved with help, error missed. */
@Composable
private fun RunProgress(state: PuzzlePlayerState) {
    val planned = state.plannedCount ?: return
    val solved = state.results.count { it.solved }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Puzzle ${state.index + 1} of $planned, $solved solved" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(planned) { i ->
            val mark = state.results.getOrNull(i)
            val color = when {
                mark == null && i == state.index -> CtColors.Text2
                mark == null -> CtColors.Track
                mark.clean -> CtColors.Success
                mark.solved -> CtColors.Accent
                else -> CtColors.Error
            }
            Box(Modifier.weight(1f).height(4.dp).clip(CtShapes.Pill).background(color))
        }
    }
}

/** "Another move works here": the one extra action, kept in the top bar's menu. */
@Composable
private fun ReportMenu(enabled: Boolean, reported: Boolean, onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(CtSpacing.touchTarget)
                .clip(CtShapes.Pill)
                .ctPressable(onClickLabel = "More options") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = CtColors.Text2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = CtColors.Raised) {
            DropdownMenuItem(
                text = { Text(if (reported) "Reported, thank you" else "Another move works here", style = CtType.Body.copy(color = CtColors.Text)) },
                enabled = enabled,
                onClick = {
                    open = false
                    onReport()
                },
            )
        }
    }
}

/** After a puzzle: the main line when you found another way, and thanks for a report. What you earned waits for the run's end. */
@Composable
private fun AfterPuzzle(player: PuzzlePlayer, state: PuzzlePlayerState) {
    if (state.solvedWithAlternative) {
        CtButton(
            text = "See the main line",
            icon = Icons.Rounded.Visibility,
            style = CtButtonStyle.Ghost,
            enabled = !state.showingLine,
            onClick = player::showMainLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.reported) {
        Text("Thanks! Saved on your phone to improve the puzzles.", style = CtType.Caption)
    }
}

@Composable
private fun RunResult(state: PuzzlePlayerState, onPlayLevel: (Int) -> Unit, onBack: () -> Unit) {
    val level = state.level.takeIf { state.mode == PuzzleMode.LEVEL }
    val passed = state.levelPassed == true
    CtCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = CtColors.Accent, modifier = Modifier.size(40.dp))
            Column {
                val heading = when {
                    level == null -> "Run complete"
                    passed -> "Level $level passed!"
                    else -> "So close!"
                }
                Text(heading, style = CtType.Headline)
                val solved = state.results.count { it.solved }
                val body = when {
                    level == null -> "You solved $solved puzzle${if (solved == 1) "" else "s"}."
                    passed -> "You solved $solved of ${state.results.size}."
                    else -> "You solved $solved of ${state.results.size}. Solve ${PuzzleLevels.TO_PASS} to pass."
                }
                Text(body, style = CtType.Body.copy(color = CtColors.Text))
            }
        }
        state.levelResult?.takeIf { it.newlyPassed }?.let { result ->
            Spacer(Modifier.height(CtSpacing.sm))
            result.worldCompleted?.let { Text("World complete! Badge earned: ${it.badge}", style = CtType.Label.copy(color = CtColors.Accent)) }
        }
    }
    // Everything the run earned, in one place at the end.
    val bonus = state.levelResult?.takeIf { it.newlyPassed }?.bonusXp ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
        CtStatTile(
            value = "+${state.runXp + bonus}",
            label = if (bonus > 0) "XP earned, with a $bonus bonus" else "XP earned",
            icon = Icons.Rounded.Star,
            accent = CtColors.Accent,
            modifier = Modifier.weight(1f),
        )
        state.rating?.let { rating ->
            val delta = state.runRatingDelta
            CtStatTile(
                value = "$rating",
                label = "Rating, " + if (delta >= 0) "+$delta" else "$delta",
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                accent = CtColors.Accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (level != null) {
        if (passed && level < PuzzleLevels.COUNT) {
            CtButton(text = "Next level", icon = Icons.AutoMirrored.Rounded.ArrowForward, onClick = { onPlayLevel(level + 1) }, modifier = Modifier.fillMaxWidth())
        }
        CtButton(
            text = "Play level $level again",
            icon = Icons.Rounded.Refresh,
            style = if (passed) CtButtonStyle.Secondary else CtButtonStyle.Primary,
            onClick = { onPlayLevel(level) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CtButton(text = "Back to puzzles", style = CtButtonStyle.Ghost, onClick = onBack, modifier = Modifier.fillMaxWidth())
}

/** Time taken on a puzzle: "0:07", "1:23". */
internal fun formatElapsed(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
