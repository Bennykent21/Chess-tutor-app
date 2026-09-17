package com.chesstutor.app.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var selectedLesson by remember { mutableStateOf<LearnTopic?>(null) }
    val lessonSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allTopics = LearnCurriculumRepository.topics
    val totalCount = allTopics.size
    val completedCount = allTopics.count { it.id in state.practicedModules || it.id in state.masteredModules }
    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    // Accordion state: Openings open by default
    val openSections = remember {
        mutableStateMapOf(
            LearnCategory.OPENING to true,
            LearnCategory.TACTICS to false,
            LearnCategory.MIDDLEGAME to false,
            LearnCategory.ENDGAME to false,
            LearnCategory.BLUNDER_PATTERNS to false
        )
    }

    val categories = listOf(
        Pair(LearnCategory.OPENING, "Openings"),
        Pair(LearnCategory.TACTICS, "Tactics"),
        Pair(LearnCategory.MIDDLEGAME, "Middlegame"),
        Pair(LearnCategory.ENDGAME, "Endgame"),
        Pair(LearnCategory.BLUNDER_PATTERNS, "Blunder patterns")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
    ) {
        // ==================== HEAD ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
        ) {
            Text(
                text = "Learn",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.015).sp,
                color = ChessTutorColors.TextPrimary
            )
            Text(
                text = "Principles, patterns and technique",
                fontSize = 12.5.sp,
                letterSpacing = (-0.005).sp,
                color = ChessTutorColors.TextSecondary,
                modifier = Modifier.padding(top = 3.dp)
            )

            // Progress Bar (.prog)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ChessTutorColors.Surface2)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressRatio.coerceIn(0.04f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ChessTutorColors.Brass)
                    )
                }

                Text(
                    text = "$completedCount / $totalCount",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChessTutorColors.TextTertiary
                )
            }
        }

        // ==================== ACCORDION LIST (#learn-list) ====================
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            categories.forEach { (category, title) ->
                val topicsForCategory = allTopics.filter { it.category == category }
                val doneInCategory = topicsForCategory.count { it.id in state.practicedModules || it.id in state.masteredModules }
                val isOpen = openSections[category] ?: false

                item(key = category.name) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        // Section Header (.sec-h)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .bouncyClickable {
                                    openSections[category] = !isOpen
                                }
                                .padding(vertical = 11.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isOpen) "Collapse" else "Expand",
                                tint = ChessTutorColors.TextTertiary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .rotate(if (isOpen) 0f else -90f)
                            )

                            Spacer(modifier = Modifier.width(9.dp))

                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.005).sp,
                                color = ChessTutorColors.TextSecondary
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = "$doneInCategory/${topicsForCategory.size}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ChessTutorColors.TextTertiary
                            )
                        }

                        // Rows (.rows)
                        AnimatedVisibility(
                            visible = isOpen,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ChessTutorColors.LineSoft)
                            ) {
                                topicsForCategory.forEachIndexed { index, topic ->
                                    val isDone = topic.id in state.masteredModules || topic.id in state.practicedModules
                                    val isDoing = !isDone && index == 0

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ChessTutorColors.Surface)
                                            .bouncyClickable { selectedLesson = topic }
                                            .padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Status dot (.st)
                                        Box(
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isDone) ChessTutorColors.Sage
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    1.5.dp,
                                                    if (isDone) Color.Transparent
                                                    else if (isDoing) ChessTutorColors.Brass
                                                    else ChessTutorColors.Surface3,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDone) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Completed",
                                                    tint = Color(0xFF11261B),
                                                    modifier = Modifier.size(9.dp)
                                                )
                                            } else if (isDoing) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(ChessTutorColors.Brass)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(11.dp))

                                        Text(
                                            text = topic.title,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-0.01).sp,
                                            color = if (isDone) ChessTutorColors.TextSecondary else ChessTutorColors.TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open",
                                            tint = ChessTutorColors.TextTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    if (index < topicsForCategory.size - 1) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Puzzle library drawn from the Lichess open puzzle database, CC0",
                    fontSize = 11.5.sp,
                    color = ChessTutorColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    }

    // ==================== LESSON BOTTOM SHEET (#sheet-lesson) ====================
    if (selectedLesson != null) {
        val topic = selectedLesson!!

        ModalBottomSheet(
            onDismissRequest = { selectedLesson = null },
            sheetState = lessonSheetState,
            containerColor = ChessTutorColors.Surface,
            contentColor = ChessTutorColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ChessTutorColors.Surface3)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = topic.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.015).sp,
                    color = ChessTutorColors.TextPrimary
                )
                Text(
                    text = topic.subtitle,
                    fontSize = 12.5.sp,
                    color = ChessTutorColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Demo Board
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(0.dp))
                ) {
                    ChessBoard(
                        fen = topic.demoFen,
                        flipped = false,
                        onSquareTapped = {}
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Key Principles Bullet List (.keys)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    topic.keyPrinciples.forEach { principle ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(ChessTutorColors.Brass)
                            )
                            Text(
                                text = principle,
                                fontSize = 14.sp,
                                letterSpacing = (-0.008).sp,
                                lineHeight = 20.sp,
                                color = ChessTutorColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Button: Practise this
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ChessTutorColors.Brass)
                        .bouncyClickable {
                            viewModel.practiceLesson(topic)
                            selectedLesson = null
                            viewModel.selectTab(0) // Switch to Train tab!
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Practise this",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.008).sp,
                        color = ChessTutorColors.BrassInk
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
