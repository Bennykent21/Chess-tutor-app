package com.chesstutor.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.data.model.GameRecord
import com.chesstutor.app.data.model.GameSource
import com.chesstutor.app.data.model.ImportDepth
import com.chesstutor.app.data.model.RatingPlatform
import com.chesstutor.app.data.model.RatingTimeControl
import com.chesstutor.app.domain.OpeningBook
import com.chesstutor.app.domain.OpeningFamilyStat
import com.chesstutor.app.domain.ReviewScheduler
import com.chesstutor.app.domain.ReviewStatsCalculator
import com.chesstutor.app.navigation.OpeningMode
import com.chesstutor.app.navigation.PlayRequest
import com.chesstutor.app.navigation.Side
import com.chesstutor.app.navigation.TrainRequest
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.TabularTextStyle
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel
import java.time.Instant

@Composable
fun ReviewScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val dueReviewCount = remember(state.reviews) {
        val now = Instant.now()
        state.reviews.count { ReviewScheduler.isDue(it, now) }
    }
    val stats = remember(state.recentGames, state.reviews) {
        ReviewStatsCalculator.compute(
            games = state.recentGames,
            reviews = state.reviews,
            accountOnly = true
        )
    }

    val linked = state.linkedProfile
    var showConnectForm by remember(linked) { mutableStateOf(linked == null) }
    var selectedPlatform by remember(linked) {
        mutableStateOf(linked?.platform ?: RatingPlatform.CHESS_COM)
    }
    var selectedTimeControl by remember(linked) {
        mutableStateOf(linked?.selectedTimeControl ?: RatingTimeControl.RAPID)
    }
    var usernameInput by remember(linked) {
        mutableStateOf(linked?.username ?: "")
    }

    val accountGames = remember(state.recentGames) {
        state.recentGames.filter { it.source == GameSource.CHESS_COM || it.source == GameSource.LICHESS }
    }
    val arenaGames = remember(state.recentGames) {
        state.recentGames.filter { it.source == GameSource.ARENA }
    }
    val incompleteCount = remember(accountGames) {
        accountGames.count { it.incompleteParse }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== TOP HEADER ====================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Review & Insights",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = if (linked != null) {
                        "${linked.platform.displayName} · @${linked.username} · ${stats.accountGameCount} synced games"
                    } else {
                        "Connect Chess.com or Lichess to analyze your games and openings"
                    },
                    fontSize = 14.sp,
                    color = ChessTutorColors.TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linked != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.Surface)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                            .testTag("review_sync_button")
                            .bouncyClickable { viewModel.refreshLinkedRating() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync account games",
                            tint = ChessTutorColors.Brass,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.Surface)
                        .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                        .testTag("review_settings_button")
                        .bouncyClickable { viewModel.setSettingsVisible(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ChessTutorColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ==================== LIVE IMPORT PROGRESS (S5) ====================
        val progress = state.importProgress
        val statusMsg = state.linkingError ?: state.linkingSuccessMessage
        if (state.isLinkingLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.Brass.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
                    .testTag("review_import_progress_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ChessTutorColors.Brass,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Importing your games",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChessTutorColors.Surface)
                            .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(8.dp))
                            .bouncyClickable { viewModel.cancelAccountImport() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.Coral
                        )
                    }
                }

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = ChessTutorColors.Brass,
                    trackColor = ChessTutorColors.Surface
                )

                val progressText = if (progress != null) {
                    buildString {
                        append("Imported ${progress.importedCount}")
                        if (progress.targetCount > 0 && progress.targetCount < 5000) {
                            append(" / ~${progress.targetCount}")
                        }
                        if (progress.currentPeriod.isNotBlank()) {
                            append(" · ${progress.currentPeriod}")
                        }
                    }
                } else {
                    statusMsg ?: "Fetching archives..."
                }

                Text(
                    text = progressText,
                    fontSize = 14.sp,
                    style = TabularTextStyle,
                    color = ChessTutorColors.Brass
                )
                Text(
                    text = "You can leave this screen; we'll keep importing in the background.",
                    fontSize = 13.sp,
                    color = ChessTutorColors.TextSecondary
                )
            }
        } else if (!statusMsg.isNullOrBlank()) {
            val isError = state.linkingError != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isError) ChessTutorColors.Danger.copy(alpha = 0.12f) else ChessTutorColors.Surface)
                    .border(
                        1.dp,
                        if (isError) ChessTutorColors.Danger.copy(alpha = 0.5f) else ChessTutorColors.Line,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = if (isError) ChessTutorColors.Coral else ChessTutorColors.Brass,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusMsg,
                    fontSize = 13.sp,
                    color = if (isError) ChessTutorColors.TextPrimary else ChessTutorColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ==================== ACCOUNT CARD OR INLINE CONNECT FORM (S5) ====================
        if (linked != null && !showConnectForm) {
            ConnectedAccountSummaryCard(
                platformName = linked.platform.displayName,
                username = linked.username,
                activeRating = linked.activeRating ?: state.estimatedRating,
                rapidRating = linked.rapidRating,
                blitzRating = linked.blitzRating,
                bulletRating = linked.bulletRating,
                importedCount = stats.accountGameCount,
                incompleteCount = incompleteCount,
                onSyncNow = { viewModel.refreshLinkedRating() },
                onChangeDepthOrAccount = { showConnectForm = true },
                onUnlink = { viewModel.unlinkRatingAccount() }
            )
        } else {
            AccountConnectCard(
                selectedPlatform = selectedPlatform,
                onSelectPlatform = { selectedPlatform = it },
                selectedTimeControl = selectedTimeControl,
                onSelectTimeControl = { selectedTimeControl = it },
                selectedDepth = state.selectedImportDepth,
                onSelectDepth = { viewModel.setImportDepth(it) },
                usernameInput = usernameInput,
                onUsernameChange = { usernameInput = it },
                isLoading = state.isLinkingLoading,
                canCancel = linked != null,
                onCancel = { showConnectForm = false },
                onConnect = {
                    focusManager.clearFocus()
                    viewModel.linkRatingAccount(
                        platform = selectedPlatform,
                        username = usernameInput,
                        timeControl = selectedTimeControl,
                        importDepth = state.selectedImportDepth
                    )
                    showConnectForm = false
                }
            )
        }

        // ==================== DUE REVIEWS QUEUE -> TRAIN (S6) ====================
        if (dueReviewCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChessTutorColors.BrassFaint)
                    .border(1.dp, ChessTutorColors.Brass.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$dueReviewCount Spaced-Repetition Reviews Due",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = "Replay positions where you slipped up in previous games or drills.",
                        fontSize = 14.sp,
                        color = ChessTutorColors.TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.Brass)
                        .testTag("review_start_due_session_button")
                        .bouncyClickable { viewModel.startTrain(TrainRequest.ReviewDue) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start in Train",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.BrassInk
                    )
                }
            }
        }

        // ==================== ACCOUNT RESULTS DASHBOARD (S5 & S6) ====================
        val drillAttempts = state.tacticalAttempts
        val drillAccuracy = if (drillAttempts > 0) {
            ((state.tacticalCorrect * 100) / drillAttempts).coerceIn(0, 100)
        } else 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ChessTutorColors.Surface)
                .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .testTag("review_results_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Account Performance",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "${stats.totalGames} account games",
                    fontSize = 13.sp,
                    style = TabularTextStyle,
                    color = ChessTutorColors.TextSecondary
                )
            }

            if (stats.totalGames == 0) {
                Text(
                    text = "No imported account games yet. Connect your Chess.com or Lichess account above to populate your win rate, opening performance, and personalized study plan.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ChessTutorColors.TextSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricTile(
                        label = "Score Rate",
                        value = "${stats.scorePercent}%",
                        caption = "${stats.totalGames} games",
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetricTile(
                        label = "Record (W·D·L)",
                        value = "${stats.wins}·${stats.draws}·${stats.losses}",
                        caption = "Account only",
                        modifier = Modifier.weight(1f)
                    )
                    if (drillAttempts >= 3) {
                        DashboardMetricTile(
                            label = "Drill Accuracy",
                            value = "$drillAccuracy%",
                            caption = "$drillAttempts attempts",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ==================== WHAT TO IMPROVE / INSIGHTS (S6) ====================
        if (stats.insights.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChessTutorColors.Surface)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What to Improve",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )

                stats.insights.forEach { insight ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChessTutorColors.SurfaceRaised)
                            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = insight.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )

                        Text(
                            text = insight.detail,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = ChessTutorColors.TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightActionButton(
                                label = insight.actionLabel,
                                isPrimary = true,
                                onClick = { viewModel.navigate(insight.command) }
                            )
                        }
                    }
                }
            }
        }

        // ==================== OPENINGS YOU PLAY vs OPENINGS YOU FACE (S6 / §2.8) ====================
        if (stats.openingsPlayed.isNotEmpty() || stats.openingsFaced.isNotEmpty()) {
            if (stats.openingsPlayed.isNotEmpty()) {
                OpeningsBreakdownSection(
                    title = "Openings You Play",
                    subtitle = "Lines where your side defines the opening system",
                    openings = stats.openingsPlayed,
                    onLearnOpening = { stat ->
                        val topicId = stat.relatedTopicId ?: "opening_fundamentals"
                        viewModel.startTrain(TrainRequest.Lesson(topicId))
                    },
                    onPlayOpening = { stat ->
                        val lineId = stat.representativeLineId ?: OpeningBook.lines.first().id
                        val side = if (stat.color.equals("Black", ignoreCase = true)) Side.BLACK else Side.WHITE
                        viewModel.startPlay(
                            PlayRequest.Opening(
                                lineId = lineId,
                                userSide = side,
                                mode = OpeningMode.LEARN_LINE
                            )
                        )
                    }
                )
            }

            if (stats.openingsFaced.isNotEmpty()) {
                OpeningsBreakdownSection(
                    title = "Openings You Face",
                    subtitle = "Opponent defenses and systems you encounter",
                    openings = stats.openingsFaced,
                    onLearnOpening = { stat ->
                        val topicId = stat.relatedTopicId ?: "opening_fundamentals"
                        viewModel.startTrain(TrainRequest.Lesson(topicId))
                    },
                    onPlayOpening = { stat ->
                        val lineId = stat.representativeLineId ?: OpeningBook.lines.first().id
                        val side = if (stat.color.equals("Black", ignoreCase = true)) Side.BLACK else Side.WHITE
                        viewModel.startPlay(
                            PlayRequest.Opening(
                                lineId = lineId,
                                userSide = side,
                                mode = OpeningMode.LEARN_LINE
                            )
                        )
                    }
                )
            }
        }

        // ==================== IMPORTED ACCOUNT GAMES (S5) ====================
        if (accountGames.isNotEmpty()) {
            GamesListSection(
                title = "Imported Account Games (${accountGames.size})",
                subtitle = "Tap any game to inspect its PGN move list",
                games = accountGames.take(25),
                onSelectGame = { viewModel.selectGameForPgn(it) }
            )
        }

        // ==================== SAVED ARENA (BOT) GAMES — SEPARATED FROM ACCOUNT STATS (S5) ====================
        if (arenaGames.isNotEmpty()) {
            GamesListSection(
                title = "Saved Play vs Bot Games (${arenaGames.size})",
                subtitle = "Tracked separately so bot games never skew your account statistics",
                games = arenaGames.take(15),
                onSelectGame = { viewModel.selectGameForPgn(it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ConnectedAccountSummaryCard(
    platformName: String,
    username: String,
    activeRating: Int,
    rapidRating: Int?,
    blitzRating: Int?,
    bulletRating: Int?,
    importedCount: Int,
    incompleteCount: Int,
    onSyncNow: () -> Unit,
    onChangeDepthOrAccount: () -> Unit,
    onUnlink: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChessTutorColors.BrassFaint)
                        .border(1.dp, ChessTutorColors.Brass.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ChessTutorColors.Brass,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "$platformName · @$username",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = buildString {
                            append("$importedCount games synced")
                            if (incompleteCount > 0) {
                                append(" · $incompleteCount incomplete PGNs")
                            }
                        },
                        fontSize = 13.sp,
                        style = TabularTextStyle,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            Text(
                text = "$activeRating",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = TabularTextStyle,
                color = ChessTutorColors.Brass
            )
        }

        // Ratings breakdown row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingPill(label = "Rapid", rating = rapidRating, modifier = Modifier.weight(1f))
            RatingPill(label = "Blitz", rating = blitzRating, modifier = Modifier.weight(1f))
            RatingPill(label = "Bullet", rating = bulletRating, modifier = Modifier.weight(1f))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.Brass)
                    .bouncyClickable(onClick = onSyncNow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sync Games",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.BrassInk
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(10.dp))
                    .bouncyClickable(onClick = onChangeDepthOrAccount),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Import Depth",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChessTutorColors.TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(10.dp))
                    .testTag("review_unlink_button")
                    .bouncyClickable(onClick = onUnlink)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = "Unlink account",
                    tint = ChessTutorColors.Coral,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingPill(
    label: String,
    rating: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ChessTutorColors.SurfaceRaised)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = rating?.toString() ?: "—",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            style = TabularTextStyle,
            color = ChessTutorColors.TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChessTutorColors.TextSecondary
        )
    }
}

@Composable
private fun AccountConnectCard(
    selectedPlatform: RatingPlatform,
    onSelectPlatform: (RatingPlatform) -> Unit,
    selectedTimeControl: RatingTimeControl,
    onSelectTimeControl: (RatingTimeControl) -> Unit,
    selectedDepth: ImportDepth,
    onSelectDepth: (ImportDepth) -> Unit,
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    isLoading: Boolean,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
            .padding(16.dp)
            .testTag("review_connect_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = ChessTutorColors.Brass,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Connect Chess Account",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
        }

        Text(
            text = "Sync your public games from Chess.com or Lichess to unlock real opening statistics and targeted training.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ChessTutorColors.TextSecondary
        )

        // Platform selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingPlatform.entries.forEach { platform ->
                val isSelected = selectedPlatform == platform
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ChessTutorColors.Brass else ChessTutorColors.SurfaceRaised)
                        .border(
                            1.dp,
                            if (isSelected) ChessTutorColors.Brass else ChessTutorColors.LineStrong,
                            RoundedCornerShape(10.dp)
                        )
                        .testTag("platform_${platform.name.lowercase()}")
                        .bouncyClickable { onSelectPlatform(platform) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platform.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) ChessTutorColors.BrassInk else ChessTutorColors.TextPrimary
                    )
                }
            }
        }

        // Username field
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Username",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ChessTutorColors.TextSecondary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (usernameInput.isEmpty()) {
                    Text(
                        text = "Enter your ${selectedPlatform.displayName} username",
                        fontSize = 14.sp,
                        color = ChessTutorColors.TextTertiary
                    )
                }
                BasicTextField(
                    value = usernameInput,
                    onValueChange = onUsernameChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = ChessTutorColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(ChessTutorColors.Brass),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_username_input")
                )
            }
        }

        // History Depth Chooser (S5: Last 100 / Last 500 / All available)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "History Depth",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ChessTutorColors.TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImportDepth.entries.forEach { depth ->
                    val isSelected = selectedDepth == depth
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) ChessTutorColors.BrassFaint else ChessTutorColors.SurfaceRaised)
                            .border(
                                1.dp,
                                if (isSelected) ChessTutorColors.Brass else ChessTutorColors.Line,
                                RoundedCornerShape(9.dp)
                            )
                            .testTag("import_depth_${depth.name.lowercase()}")
                            .bouncyClickable { onSelectDepth(depth) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = depth.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.TextPrimary
                        )
                    }
                }
            }
        }

        // Preferred rating time control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RatingTimeControl.entries.forEach { tc ->
                val isSelected = selectedTimeControl == tc
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ChessTutorColors.SurfaceRaised else ChessTutorColors.Background)
                        .border(
                            1.dp,
                            if (isSelected) ChessTutorColors.Brass else ChessTutorColors.Line,
                            RoundedCornerShape(8.dp)
                        )
                        .bouncyClickable { onSelectTimeControl(tc) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tc.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) ChessTutorColors.Brass else ChessTutorColors.TextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canCancel) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.SurfaceRaised)
                        .border(1.dp, ChessTutorColors.LineStrong, RoundedCornerShape(12.dp))
                        .bouncyClickable(onClick = onCancel)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChessTutorColors.TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.Brass)
                    .testTag("connect_and_import_button")
                    .bouncyClickable(enabled = !isLoading) { onConnect() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoading) "Importing games..." else "Connect & Import Games",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.BrassInk
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.SurfaceRaised)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            style = TabularTextStyle,
            color = ChessTutorColors.TextPrimary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ChessTutorColors.TextSecondary
        )
        Text(
            text = caption,
            fontSize = 12.sp,
            style = TabularTextStyle,
            color = ChessTutorColors.TextTertiary
        )
    }
}

@Composable
private fun InsightActionButton(
    label: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPrimary) ChessTutorColors.Brass else ChessTutorColors.Surface)
            .border(
                width = 1.dp,
                color = if (isPrimary) ChessTutorColors.Brass else ChessTutorColors.LineStrong,
                shape = RoundedCornerShape(8.dp)
            )
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPrimary) ChessTutorColors.BrassInk else ChessTutorColors.TextPrimary
        )
    }
}

@Composable
private fun OpeningsBreakdownSection(
    title: String,
    subtitle: String,
    openings: List<OpeningFamilyStat>,
    onLearnOpening: (OpeningFamilyStat) -> Unit,
    onPlayOpening: (OpeningFamilyStat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = ChessTutorColors.TextSecondary
            )
        }

        openings.forEach { stat ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildString {
                                if (stat.eco.isNotBlank()) append("${stat.eco} · ")
                                append(stat.displayLabel)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = "${stat.games} games · W ${stat.wins} · D ${stat.draws} · L ${stat.losses}",
                            fontSize = 13.sp,
                            style = TabularTextStyle,
                            color = ChessTutorColors.TextSecondary
                        )
                    }

                    Text(
                        text = "${stat.scorePercent}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularTextStyle,
                        color = when {
                            stat.scorePercent >= 55 -> ChessTutorColors.Sage
                            stat.scorePercent <= 45 -> ChessTutorColors.Coral
                            else -> ChessTutorColors.Brass
                        }
                    )
                }

                LinearProgressIndicator(
                    progress = { (stat.scorePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = when {
                        stat.scorePercent >= 55 -> ChessTutorColors.Sage
                        stat.scorePercent <= 45 -> ChessTutorColors.Coral
                        else -> ChessTutorColors.Brass
                    },
                    trackColor = ChessTutorColors.Surface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InsightActionButton(
                        label = "Learn & Drill",
                        onClick = { onLearnOpening(stat) }
                    )
                    InsightActionButton(
                        label = "Practise in Play",
                        isPrimary = true,
                        onClick = { onPlayOpening(stat) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GamesListSection(
    title: String,
    subtitle: String,
    games: List<GameRecord>,
    onSelectGame: (GameRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChessTutorColors.Surface)
            .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = ChessTutorColors.TextSecondary
            )
        }

        games.forEach { game ->
            val outcome = game.userOutcomeLabel
            val outcomeColor = when (outcome) {
                "Win" -> ChessTutorColors.Sage
                "Loss" -> ChessTutorColors.Coral
                else -> ChessTutorColors.Brass
            }
            val resolvedOpening = remember(game.id, game.uciMoves, game.pgn) {
                ReviewStatsCalculator.resolveOpeningForGame(game).name
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChessTutorColors.SurfaceRaised)
                    .border(1.dp, ChessTutorColors.Line, RoundedCornerShape(10.dp))
                    .clickable { onSelectGame(game) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(outcomeColor.copy(alpha = 0.16f))
                            .border(1.dp, outcomeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = outcome,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = outcomeColor
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "vs ${game.botName} (${game.botRating})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("As ${game.userColor.replaceFirstChar { it.uppercase() }}")
                                if (resolvedOpening.isNotBlank()) {
                                    append(" · $resolvedOpening")
                                }
                                append(" · ${game.moveCount} moves")
                            },
                            fontSize = 13.sp,
                            style = TabularTextStyle,
                            color = ChessTutorColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "PGN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.Brass,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
