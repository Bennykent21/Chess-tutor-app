package com.chesstutor.app.feature.puzzles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardHost
import com.chesstutor.app.board.MarkKind
import com.chesstutor.app.board.SquareRing
import com.chesstutor.app.board.formatClock
import com.chesstutor.app.board.whiteAtBottom
import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.AppSettings
import com.chesstutor.app.design.CtButton
import com.chesstutor.app.design.CtButtonStyle
import com.chesstutor.app.design.CtCard
import com.chesstutor.app.design.CtChip
import com.chesstutor.app.design.CtCoachStrip
import com.chesstutor.app.design.CtColors
import com.chesstutor.app.design.CtEmptyState
import com.chesstutor.app.design.CtGradients
import com.chesstutor.app.design.CtScreenBackground
import com.chesstutor.app.design.CtShapes
import com.chesstutor.app.design.CtSpacing
import com.chesstutor.app.design.CtTopBar
import com.chesstutor.app.design.CtType
import com.chesstutor.app.domain.MascotMood
import com.chesstutor.app.domain.puzzle.PuzzleLevels
import com.chesstutor.app.domain.puzzle.PuzzleMode

/**
 * The puzzle player (plan section 6.3.3): goal-first coach, the board facing your side, an
 * optional timer, hints and the solution; after each puzzle the patterns, XP and rating,
 * the main line after an alternative, "Analyse", "Another move works" and "Next".
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
    CtScreenBackground(modifier) {
        if (player == null) {
            Column(Modifier.fillMaxSize()) {
                CtTopBar(title = "Puzzles", onBack = onBack)
            }
            return@CtScreenBackground
        }
        val state by player.state.collectAsState()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val count = state.plannedCount?.let { " · ${state.index + 1}/$it" } ?: ""
            CtTopBar(title = state.title + count, onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = CtSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(CtSpacing.sm),
            ) {
                when (state.phase) {
                    PlayerPhase.UNAVAILABLE -> CtEmptyState(
                        icon = Icons.Rounded.Extension,
                        title = "No puzzles here",
                        body = "There are no puzzles for this choice. Try another level or mode.",
                    )
                    PlayerPhase.FINISHED -> RunResult(state, onPlayLevel, onBack)
                    else -> Playing(player, state, settings, onFeedback, onAnalyse)
                }
                Spacer(Modifier.height(CtSpacing.lg))
            }
        }
    }
}

@Composable
private fun Playing(
    player: PuzzlePlayer,
    state: PuzzlePlayerState,
    settings: AppSettings,
    onFeedback: (BoardFeedback) -> Unit,
    onAnalyse: (String, List<String>) -> Unit,
) {
    val board = state.board ?: return
    ProgressDots(state)
    val side = if (state.solverColor == PieceColor.WHITE) "White" else "Black"
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
    CtCoachStrip(mascot = settings.mascot, text = coachText, label = "You play $side", mood = mood, modifier = Modifier.fillMaxWidth())

    val rings = listOfNotNull(
        state.hintSquare?.let { SquareRing(it, MarkKind.HINT) },
        state.mistakeSquare?.let { SquareRing(it, MarkKind.MISTAKE) },
    )
    BoardHost(
        session = board,
        theme = settings.boardTheme,
        whiteAtBottom = whiteAtBottom(settings.boardOrientation, state.solverColor),
        showCoordinates = settings.showCoordinates,
        showLegalDots = settings.showLegalDots,
        autoQueen = settings.autoQueen,
        arrows = listOfNotNull(state.hintArrow),
        rings = rings,
        onFeedback = onFeedback,
    )
    InfoRow(state)
    when (state.phase) {
        PlayerPhase.SOLVED, PlayerPhase.REVEALED -> AfterPuzzle(player, state, onAnalyse = { onAnalyse(board.solvingFen, board.puzzle.moves.drop(1)) })
        else -> Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
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
    }
}

/** One dot per puzzle of the run: lime solved cleanly, teal solved with help, coral missed. */
@Composable
private fun ProgressDots(state: PuzzlePlayerState) {
    val planned = state.plannedCount ?: return
    val solved = state.results.count { it.solved }
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Puzzle ${state.index + 1} of $planned, $solved solved"
        },
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.xs),
    ) {
        repeat(planned) { i ->
            val mark = state.results.getOrNull(i)
            val color = when {
                mark == null && i == state.index -> CtColors.Gold
                mark == null -> CtColors.SurfaceHigh
                mark.clean -> CtColors.Lime
                mark.solved -> CtColors.Teal
                else -> CtColors.Coral
            }
            Box(Modifier.size(width = 28.dp, height = 8.dp).clip(CtShapes.Pill).background(color))
        }
    }
}

@Composable
private fun InfoRow(state: PuzzlePlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CtSpacing.md),
    ) {
        // A countdown shows tenths when time is short; time taken always reads like "0:07".
        val clock = state.remainingMs?.let { "Time left ${formatClock(it)}" } ?: "Time ${formatElapsed(state.elapsedMs)}"
        Text(clock, style = CtType.Label, modifier = Modifier.weight(1f))
        state.maxStrikes?.let { max -> Text("Strikes ${state.strikes}/$max", style = CtType.Label.copy(color = CtColors.Coral)) }
        state.xpEarned?.let { Text("+$it XP", style = CtType.Label.copy(color = CtColors.Gold)) }
        state.rating?.let { rating ->
            val delta = state.ratingDelta?.let { if (it >= 0) " (+$it)" else " ($it)" } ?: ""
            Text("Rating $rating$delta", style = CtType.Label.copy(color = CtColors.Teal))
        }
    }
}

@Composable
private fun AfterPuzzle(player: PuzzlePlayer, state: PuzzlePlayerState, onAnalyse: () -> Unit) {
    if (state.revealedThemes.isNotEmpty()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm),
        ) {
            state.revealedThemes.forEach { CtChip(text = it, color = CtColors.Violet) }
        }
    }
    if (state.solvedWithAlternative) {
        CtButton(
            text = "See the main line",
            icon = Icons.Rounded.Visibility,
            style = CtButtonStyle.Secondary,
            enabled = !state.showingLine,
            onClick = player::showMainLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CtSpacing.sm)) {
        CtButton(text = "Analyse", icon = Icons.Rounded.Science, style = CtButtonStyle.Secondary, onClick = onAnalyse, modifier = Modifier.weight(1f))
        CtButton(
            text = if (state.reported) "Reported" else "Report",
            icon = Icons.Rounded.Flag,
            style = CtButtonStyle.Secondary,
            enabled = !state.reported,
            onClick = { player.reportAnotherMove(note = "Reported from the puzzle player") },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Report: another move works here" },
        )
    }
    if (state.reported) {
        Text("Thanks! Saved on your phone to improve the puzzles.", style = CtType.Caption)
    }
    CtButton(
        text = if (state.isLastOfRun) "See your result" else "Next puzzle",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        onClick = player::next,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RunResult(state: PuzzlePlayerState, onPlayLevel: (Int) -> Unit, onBack: () -> Unit) {
    val level = state.level.takeIf { state.mode == PuzzleMode.LEVEL }
    val passed = state.levelPassed == true
    CtCard(background = if (passed) CtGradients.Teal else CtGradients.Violet, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CtSpacing.md)) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = CtColors.Gold, modifier = Modifier.size(40.dp))
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
            Text("+${result.bonusXp} XP bonus", style = CtType.Label.copy(color = CtColors.Gold))
            result.worldCompleted?.let { Text("World complete! Badge earned: ${it.badge}", style = CtType.Label.copy(color = CtColors.Gold)) }
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
