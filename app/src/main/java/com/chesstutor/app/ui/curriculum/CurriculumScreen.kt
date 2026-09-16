package com.chesstutor.app.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

private data class MistakePattern(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val fen: String,
    val recommendedMoveUci: String? = null
)

private val MISTAKE_MODULES = listOf(
    MistakePattern(
        id = "lesson_mate_1",
        title = "Missed Mate in 1",
        category = "TACTICAL VERIFICATION",
        description = "A forced mate exists right now. Failing to play it allows the opponent to escape.",
        fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 4",
        recommendedMoveUci = "f3f7"
    ),
    MistakePattern(
        id = "lesson_hanging_piece",
        title = "Hanging Major Pieces",
        category = "BOARD AWARENESS",
        description = "Leaving valuable pieces undefended on active files is the #1 rating sink below 1500.",
        fen = "r1bqk2r/pppp1ppp/2n2n2/4p3/1b2P3/2NP1N2/PPP2PPP/R1BQKB1R w KQkq - 2 5",
        recommendedMoveUci = "c1d2"
    ),
    MistakePattern(
        id = "lesson_back_rank",
        title = "Back-Rank Checkmate & Luft",
        category = "KING SAFETY",
        description = "When pawns block the king's escape (no luft), an infiltrating rook delivers an instant decisive mate.",
        fen = "3r2k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
        recommendedMoveUci = "e1e8"
    ),
    MistakePattern(
        id = "lesson_overworked_piece",
        title = "Overworked Defenders",
        category = "TACTICAL PRESSURE",
        description = "A piece trying to defend two separate critical squares or pieces simultaneously collapses under dual pressure.",
        fen = "2r3k1/5ppp/8/3q4/8/8/5PPP/2R3K1 w - - 0 1",
        recommendedMoveUci = "c1c8"
    ),
    MistakePattern(
        id = "lesson_pawn_fork",
        title = "Central Pawn Forks",
        category = "TACTICAL MOTIFS",
        description = "A humble pawn advancing between two minor pieces forces an unequal piece trade.",
        fen = "r1bqk2r/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
        recommendedMoveUci = "d2d4"
    ),
    MistakePattern(
        id = "lesson_discovered_check",
        title = "Discovered Attack & Check",
        category = "COMBINATIVE TACTICS",
        description = "Moving one piece unmasks a devastating line attack from a queen or rook behind it.",
        fen = "r1b1k2r/pppp1ppp/8/8/1b1q4/2N5/PP1QPPPP/R3KB1R w KQkq - 0 10",
        recommendedMoveUci = "d2d4"
    )
)

@Composable
fun CurriculumScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedCategoryFilter by remember { mutableStateOf<LearnCategory?>(null) }

    val totalTopics = LearnCurriculumRepository.topics.size + MISTAKE_MODULES.size
    val masteredCount = state.masteredModules.size
    val progress = if (totalTopics > 0) masteredCount.toFloat() / totalTopics else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Progress Overview (Chess.com Academy Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF312E2B))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ACADEMY MASTERY",
                    color = ChessTutorColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$masteredCount of $totalTopics Lessons Mastered",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ChessTutorColors.Primary,
                    trackColor = Color(0xFF3D3A34)
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3D3A34)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Primary Tabs: Lessons vs Mistake Patterns
        TabRow(
            selectedTabIndex = state.curriculumTab,
            containerColor = Color(0xFF262522),
            contentColor = ChessTutorColors.Primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.curriculumTab]),
                    color = ChessTutorColors.Primary
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = state.curriculumTab == 0,
                onClick = { viewModel.setCurriculumTab(0) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lessons & Repertoire", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = state.curriculumTab == 1,
                onClick = { viewModel.setCurriculumTab(1) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Blunder Patterns", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // 3. Tab Content
        if (state.curriculumTab == 0) {
            // Filter Pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("All", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChessTutorColors.Primary,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF312E2B),
                            labelColor = ChessTutorColors.TextSecondary
                        )
                    )
                }
                items(LearnCategory.values()) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = {
                            val shortName = when (cat) {
                                LearnCategory.OPENING -> "Openings"
                                LearnCategory.TACTICS -> "Tactics"
                                LearnCategory.MIDDLEGAME -> "Middlegame"
                                LearnCategory.ENDGAME -> "Endgames"
                            }
                            Text(shortName, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChessTutorColors.Primary,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF312E2B),
                            labelColor = ChessTutorColors.TextSecondary
                        )
                    )
                }
            }

            // High-density Lesson Rows
            val filteredTopics = if (selectedCategoryFilter != null) {
                LearnCurriculumRepository.topics.filter { it.category == selectedCategoryFilter }
            } else {
                LearnCurriculumRepository.topics
            }

            filteredTopics.forEach { topic ->
                HighDensityTopicRow(
                    topic = topic,
                    isMastered = state.masteredModules.contains(topic.id),
                    onPractice = {
                        viewModel.practiceCurriculumLesson(
                            lessonId = topic.id,
                            fen = topic.demoFen,
                            title = topic.title,
                            category = topic.category.displayName,
                            description = topic.summary,
                            recommendedMoveUci = topic.recommendedMoveUci
                        )
                    },
                    onToggleMastered = {
                        viewModel.markModuleMastered(topic.id)
                    }
                )
            }
        } else {
            // Mistake Patterns Tab
            MISTAKE_MODULES.forEach { module ->
                HighDensityMistakeRow(
                    module = module,
                    isMastered = state.masteredModules.contains(module.id),
                    onPractice = {
                        viewModel.practiceCurriculumLesson(
                            lessonId = module.id,
                            fen = module.fen,
                            title = module.title,
                            category = module.category,
                            description = module.description,
                            recommendedMoveUci = module.recommendedMoveUci
                        )
                    },
                    onToggleMastered = {
                        viewModel.markModuleMastered(module.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun HighDensityTopicRow(
    topic: LearnTopic,
    isMastered: Boolean,
    onPractice: () -> Unit,
    onToggleMastered: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val categoryColor = when (topic.category) {
        LearnCategory.OPENING -> Color(0xFF5C8BB0)
        LearnCategory.TACTICS -> Color(0xFFFFA459)
        LearnCategory.MIDDLEGAME -> Color(0xFFA855F7)
        LearnCategory.ENDGAME -> Color(0xFF81B64C)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF312E2B))
            .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Color Accent Bar
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = topic.subtitle,
                    color = ChessTutorColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // Mastered Check Icon
            IconButton(
                onClick = onToggleMastered,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isMastered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Mastered",
                    tint = if (isMastered) ChessTutorColors.Primary else Color(0xFF6B6966),
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(20.dp)
            )
        }

        // Expandable Principles & Practice Action
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2926))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = topic.summary,
                    color = Color(0xFFDCDAD5),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "CORE GRANDMASTER PRINCIPLES",
                    color = ChessTutorColors.Primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                topic.keyPrinciples.forEach { principle ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = ChessTutorColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = principle,
                            color = ChessTutorColors.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onPractice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChessTutorColors.Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Practice on Interactive Board (${topic.recommendedMoveUci})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HighDensityMistakeRow(
    module: MistakePattern,
    isMastered: Boolean,
    onPractice: () -> Unit,
    onToggleMastered: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF312E2B))
            .border(1.dp, Color(0xFF3D3A34), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ChessTutorColors.Blunder)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = module.description,
                color = ChessTutorColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onPractice,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChessTutorColors.Primary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Practice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
