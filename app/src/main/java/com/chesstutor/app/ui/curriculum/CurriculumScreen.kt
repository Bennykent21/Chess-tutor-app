package com.chesstutor.app.ui.curriculum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.LearnTopic
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTopicForDetail by remember { mutableStateOf<LearnTopic?>(null) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allTopics = LearnCurriculumRepository.topics
    val totalCount = allTopics.size
    val completedCount = allTopics.count { it.id in state.practicedModules || it.id in state.masteredModules }
    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Clean Title Header (§1.1: 28sp Semibold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Learn",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = "$completedCount of $totalCount completed",
                    fontSize = 13.sp,
                    color = ChessTutorColors.TextSecondary
                )
            }

            // Compact Progress Badge
            Text(
                text = "${(progressRatio * 100).toInt()}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChessTutorColors.Accent
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = { progressRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = ChessTutorColors.Accent,
            trackColor = ChessTutorColors.SurfaceElevated
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Grouped Sections: Openings, Tactics, Middlegame, Endgame, Blunder Patterns (§4.3)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LearnCategory.entries.forEach { category ->
                val categoryTopics = allTopics.filter { it.category == category }
                if (categoryTopics.isNotEmpty()) {
                    item(key = "header_${category.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ChessTutorColors.TextPrimary
                            )
                            Text(
                                text = "${categoryTopics.count { it.id in state.practicedModules }}/${categoryTopics.size}",
                                fontSize = 13.sp,
                                color = ChessTutorColors.TextTertiary
                            )
                        }
                    }

                    items(
                        items = categoryTopics,
                        key = { it.id }
                    ) { topic ->
                        val isCompleted = topic.id in state.practicedModules || topic.id in state.masteredModules

                        // §4.3: One row per lesson, one line of text. Title only.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChessTutorColors.Surface)
                                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                                .bouncyClickable { selectedTopicForDetail = topic }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Status Indicator (Checked or Hollow Dot)
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCompleted) ChessTutorColors.Accent.copy(alpha = 0.2f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isCompleted) ChessTutorColors.Accent else ChessTutorColors.TextTertiary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ChessTutorColors.Accent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = topic.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ChessTutorColors.TextPrimary,
                                    maxLines = 1
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = ChessTutorColors.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 3. Lesson Detail View (§4.3: Tapping a lesson opens a detail sheet with board, 2-3 bullets, and Practice button)
    selectedTopicForDetail?.let { topic ->
        ModalBottomSheet(
            onDismissRequest = { selectedTopicForDetail = null },
            sheetState = detailSheetState,
            containerColor = ChessTutorColors.Surface,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = topic.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ChessTutorColors.TextPrimary
                        )
                        Text(
                            text = topic.subtitle,
                            fontSize = 13.sp,
                            color = ChessTutorColors.TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { selectedTopicForDetail = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ChessTutorColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Board showing the key position (~220dp height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(8.dp))
                ) {
                    val arrow = if (topic.recommendedMoveUci.length >= 4) {
                        Pair(topic.recommendedMoveUci.take(2), topic.recommendedMoveUci.substring(2, 4))
                    } else null

                    ChessBoard(
                        fen = topic.demoFen,
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        lastMove = null,
                        recommendedArrow = arrow,
                        flipped = false,
                        onSquareTapped = {}
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // §4.3: Maximum 3 bullets, maximum 12 words each
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topic.keyPrinciples.take(3).forEach { principle ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ChessTutorColors.Accent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = principle,
                                fontSize = 14.sp,
                                color = ChessTutorColors.TextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Single "Practice" Action Button
                Button(
                    onClick = {
                        val chosen = topic
                        selectedTopicForDetail = null
                        viewModel.exploreLearnTopic(chosen)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Accent,
                        contentColor = ChessTutorColors.Background
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Practice",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
