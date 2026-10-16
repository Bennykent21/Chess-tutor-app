package com.chesstutor.app.ui.puzzles

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chesstutor.app.domain.puzzle.PuzzlePhase
import com.chesstutor.app.domain.puzzle.PuzzleScreenState
import com.chesstutor.app.domain.puzzle.PuzzleThemes
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.components.PromotionDialog
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.TabularTextStyle
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.PromotionRequest
import com.chesstutor.app.viewmodel.PuzzleViewModel

/**
 * The Puzzles tab. [seedRating] is only used the first time, as the starting puzzle rating.
 */
@Composable
fun PuzzleScreen(
    seedRating: Int,
    showCoordinates: Boolean,
    showLegalDots: Boolean,
    modifier: Modifier = Modifier,
    viewModel: PuzzleViewModel = viewModel()
) {
    LaunchedEffect(seedRating) { viewModel.start(seedRating) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    PuzzleContent(
        state = state,
        showCoordinates = showCoordinates,
        showLegalDots = showLegalDots,
        modifier = modifier,
        onSquareTapped = viewModel::onSquareTapped,
        onChoosePromotion = viewModel::choosePromotion,
        onCancelPromotion = viewModel::cancelPromotion,
        onHint = viewModel::hint,
        onReveal = viewModel::reveal,
        onSkip = viewModel::skip,
        onNext = viewModel::next,
        onTheme = viewModel::setTheme
    )
}

@Composable
private fun PuzzleContent(
    state: PuzzleScreenState,
    showCoordinates: Boolean,
    showLegalDots: Boolean,
    modifier: Modifier,
    onSquareTapped: (String) -> Unit,
    onChoosePromotion: (Char) -> Unit,
    onCancelPromotion: () -> Unit,
    onHint: () -> Unit,
    onReveal: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onTheme: (String?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.phase) {
        if (state.phase == PuzzlePhase.WRONG || state.phase == PuzzlePhase.SOLVED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(rememberScrollState())
            .testTag("puzzle_screen")
            .padding(bottom = 20.dp)
    ) {
        PuzzleHeader(state)
        if (state.themeOptions.isNotEmpty()) {
            ThemeChips(state, onTheme)
            Spacer(Modifier.height(14.dp))
        }
        BoardCard(state, showCoordinates, showLegalDots, onSquareTapped)
        Spacer(Modifier.height(14.dp))
        StatusCard(state)
        Spacer(Modifier.height(14.dp))
        ActionRow(state, onHint, onReveal, onSkip, onNext)
    }

    state.pendingPromotion?.let { promotion ->
        PromotionDialog(
            request = PromotionRequest(promotion.from, promotion.to, promotion.choices),
            onChoose = onChoosePromotion,
            onDismiss = onCancelPromotion
        )
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun PuzzleHeader(state: PuzzleScreenState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Puzzles",
                style = MaterialTheme.typography.headlineMedium,
                color = ChessTutorColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.streak > 0) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = ChessTutorColors.Amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${state.streak}",
                        style = TabularTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = ChessTutorColors.Amber
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "${state.solvedCount} solved · best streak ${state.bestStreak}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChessTutorColors.TextSecondary
                )
            }
        }
        RatingBadge(rating = state.rating, delta = state.ratingDelta)
    }
}

@Composable
private fun RatingBadge(rating: Int, delta: Int?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "RATING",
            style = MaterialTheme.typography.labelSmall,
            color = ChessTutorColors.TextTertiary
        )
        Text(
            text = "$rating",
            style = TabularTextStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            color = ChessTutorColors.Brass
        )
        if (delta != null) {
            Text(
                text = formatDelta(delta),
                style = TabularTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (delta >= 0) ChessTutorColors.Sage else ChessTutorColors.Coral
            )
        }
    }
}

// ---------------------------------------------------------------- themes

@Composable
private fun ThemeChips(state: PuzzleScreenState, onTheme: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = state.themeOptions, key = { it.id ?: "mixed" }) { option ->
            val selected = option.id == state.theme
            val shape = RoundedCornerShape(50)
            Text(
                text = option.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) ChessTutorColors.BrassInk else ChessTutorColors.TextSecondary,
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) ChessTutorColors.Brass else ChessTutorColors.Surface2)
                    .border(1.dp, if (selected) ChessTutorColors.Brass else ChessTutorColors.Line, shape)
                    .bouncyClickable { onTheme(option.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- board

@Composable
private fun BoardCard(
    state: PuzzleScreenState,
    showCoordinates: Boolean,
    showLegalDots: Boolean,
    onSquareTapped: (String) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when (state.phase) {
            PuzzlePhase.SOLVED -> ChessTutorColors.Sage
            PuzzlePhase.WRONG -> ChessTutorColors.Coral
            PuzzlePhase.REVEALED -> ChessTutorColors.Amber
            else -> ChessTutorColors.Line
        },
        label = "puzzleBoardBorder"
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(2.dp, borderColor, shape)
    ) {
        if (state.phase == PuzzlePhase.LOADING || state.phase == PuzzlePhase.UNAVAILABLE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(ChessTutorColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (state.phase == PuzzlePhase.LOADING) {
                    CircularProgressIndicator(color = ChessTutorColors.Brass)
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ChessTutorColors.TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        } else {
            ChessBoard(
                fen = state.fen,
                selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets,
                lastMove = state.lastMove,
                badSquare = state.badSquare,
                recommendedArrow = state.hintArrow,
                flipped = state.flipped,
                showCoordinates = showCoordinates,
                showLegalDots = showLegalDots,
                onSquareTapped = onSquareTapped
            )
        }
    }
}

// ---------------------------------------------------------------- status

private data class StatusInfo(
    val accent: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val footnote: String? = null
)

private fun statusInfo(state: PuzzleScreenState): StatusInfo {
    val side = if (state.sideToMove == 'w') "White" else "Black"
    return when (state.phase) {
        PuzzlePhase.LOADING -> StatusInfo(
            ChessTutorColors.Brass, Icons.Default.TrackChanges,
            "Loading puzzles", "Getting your next position ready."
        )
        PuzzlePhase.UNAVAILABLE -> StatusInfo(
            ChessTutorColors.Coral, Icons.Default.Warning,
            "Puzzles unavailable",
            "The puzzle pack (puzzles.tsv.gz) wasn't found in this build. Add it to app/src/main/assets and rebuild."
        )
        PuzzlePhase.SETUP -> StatusInfo(
            ChessTutorColors.Brass, Icons.Default.TrackChanges,
            "Get ready", "Watch your opponent's last move."
        )
        PuzzlePhase.SOLVING -> StatusInfo(
            ChessTutorColors.Brass, Icons.Default.TrackChanges,
            "Find the best move",
            buildString {
                append("$side to move")
                if (state.totalSteps > 1) append(" · move ${state.step} of ${state.totalSteps}")
            }
        )
        PuzzlePhase.REPLYING -> StatusInfo(
            ChessTutorColors.Sage, Icons.Default.Check,
            "Correct!", "Your opponent replies…"
        )
        PuzzlePhase.WRONG -> StatusInfo(
            ChessTutorColors.Coral, Icons.Default.Close,
            "Not quite", "That isn't it. Look again and try another move."
        )
        PuzzlePhase.SOLVED -> StatusInfo(
            ChessTutorColors.Sage, Icons.Default.CheckCircle,
            if (state.mistakes == 0 && state.hintsUsed == 0) "Solved!" else "Solved, with a little help",
            state.ratingDelta?.let { "Rating ${formatDelta(it)}" } ?: "Nice work.",
            themesFootnote(state)
        )
        PuzzlePhase.REVEALED -> StatusInfo(
            ChessTutorColors.Amber, Icons.Default.Visibility,
            "Here's the solution",
            state.ratingDelta?.let { "Watch the winning line · rating ${formatDelta(it)}" }
                ?: "Watch the winning line.",
            themesFootnote(state)
        )
    }
}

private fun themesFootnote(state: PuzzleScreenState): String? {
    val names = state.puzzle?.themes.orEmpty()
        .filter { PuzzleThemes.isKnown(it) }
        .sortedBy { PuzzleThemes.order(it) }
        .take(3)
        .map { PuzzleThemes.displayName(it) }
    return if (names.isEmpty()) null else "Theme: " + names.joinToString(" · ")
}

private fun formatDelta(delta: Int): String = if (delta >= 0) "+$delta" else "−${-delta}"

@Composable
private fun StatusCard(state: PuzzleScreenState) {
    val info = statusInfo(state)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ChessTutorColors.Surface)
            .border(1.dp, info.accent.copy(alpha = 0.45f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(info.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = info.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = info.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ChessTutorColors.TextSecondary
            )
            if (info.footnote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = info.footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChessTutorColors.TextTertiary
                )
            }
        }
    }
}

// ---------------------------------------------------------------- actions

@Composable
private fun ActionRow(
    state: PuzzleScreenState,
    onHint: () -> Unit,
    onReveal: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    if (state.phase == PuzzlePhase.UNAVAILABLE || state.phase == PuzzlePhase.LOADING) return

    val finished = state.phase == PuzzlePhase.SOLVED || state.phase == PuzzlePhase.REVEALED
    if (finished) {
        PrimaryButton(
            label = "Next puzzle",
            icon = Icons.Default.SkipNext,
            onClick = onNext,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        return
    }

    val canHint = state.phase == PuzzlePhase.SOLVING
    val canGiveUp = state.phase == PuzzlePhase.SOLVING || state.phase == PuzzlePhase.WRONG
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SecondaryButton(
            label = if (state.hintsUsed > 0) "Hint (${state.hintsUsed})" else "Hint",
            icon = Icons.Default.Lightbulb,
            enabled = canHint,
            onClick = onHint,
            modifier = Modifier.weight(1f)
        )
        SecondaryButton(
            label = "Solution",
            icon = Icons.Default.Visibility,
            enabled = canGiveUp,
            onClick = onReveal,
            modifier = Modifier.weight(1f)
        )
        SecondaryButton(
            label = "Skip",
            icon = Icons.Default.SkipNext,
            enabled = canGiveUp,
            onClick = onSkip,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(ChessTutorColors.Brass)
            .bouncyClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChessTutorColors.BrassInk
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChessTutorColors.BrassInk,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val content = if (enabled) ChessTutorColors.TextPrimary else ChessTutorColors.TextTertiary.copy(alpha = 0.55f)
    Row(
        modifier = modifier
            .clip(shape)
            .background(ChessTutorColors.Surface2)
            .border(1.dp, ChessTutorColors.Line, shape)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) ChessTutorColors.Brass else content,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = content
        )
    }
}
