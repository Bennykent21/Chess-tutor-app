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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chesstutor.app.domain.LearnCategory
import com.chesstutor.app.domain.LearnCurriculumRepository
import com.chesstutor.app.domain.LearnTopic
import com.chesstutor.app.ui.components.AcademyCard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun CurriculumScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChessTutorColors.Background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Sub-tab Toggle
        TabRow(
            selectedTabIndex = state.curriculumTab,
            containerColor = ChessTutorColors.Surface,
            contentColor = ChessTutorColors.Primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.curriculumTab]),
                    color = ChessTutorColors.Primary
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = state.curriculumTab == 0,
                onClick = { viewModel.setCurriculumTab(0) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mistake Patterns",
                            style = ChessTutorTypography.labelSmall,
                            color = if (state.curriculumTab == 0) ChessTutorColors.Primary else ChessTutorColors.TextSecondary
                        )
                    }
                }
            )
            Tab(
                selected = state.curriculumTab == 1,
                onClick = { viewModel.setCurriculumTab(1) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Learn",
                            style = ChessTutorTypography.labelSmall,
                            color = if (state.curriculumTab == 1) ChessTutorColors.Primary else ChessTutorColors.TextSecondary
                        )
                    }
                }
            )
        }

        if (state.curriculumTab == 0) {
            MistakePatternsSection(state = state, viewModel = viewModel)
        } else {
            LearnSection(viewModel = viewModel)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class MistakeModule(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val fen: String,
    val recommendedMoveUci: String
)

@Composable
private fun MistakePatternsSection(
    state: AppUiState,
    viewModel: AppViewModel
) {
    val mistakeModules = remember {
        listOf(
            MistakeModule(
                id = "lesson_mate_1",
                title = "Module 1: Missed Forced Mate",
                category = "MATE-IN-1 DETECTION",
                description = "Learn to identify mating nets instantly. When a forced mate exists, any non-mating move is classified as a critical mistake.",
                fen = AppViewModel.FEN_MATE_IN_ONE,
                recommendedMoveUci = "f3f7"
            ),
            MistakeModule(
                id = "lesson_back_rank",
                title = "Module 2: Back-Rank Vulnerability",
                category = "TACTICAL DEFENSE",
                description = "Kings trapped behind their own pawns with no escape square ('luft'). Learn to identify the checkmate line before it happens.",
                fen = AppViewModel.FEN_BACK_RANK_MATE,
                recommendedMoveUci = "d1d8"
            ),
            MistakeModule(
                id = "lesson_hanging_piece",
                title = "Module 3: Hanging Pieces",
                category = "MATERIAL PRESERVATION",
                description = "A piece is hanging if it is attacked and has zero defenders, or is defended fewer times than attacked. Spot hanging targets instantly.",
                fen = AppViewModel.FEN_HANGING_PIECE,
                recommendedMoveUci = "d8d4"
            ),
            MistakeModule(
                id = "lesson_fork",
                title = "Module 4: Fork & Double Attack",
                category = "TACTICAL EXECUTION",
                description = "Delivering double threats with knights and queens. One threat cannot be defended while the other is executed.",
                fen = AppViewModel.FEN_FORK_TACTIC,
                recommendedMoveUci = "e5f7"
            )
        )
    }

    val totalModules = mistakeModules.size
    val practicedCount = state.practicedModules.count { id -> mistakeModules.any { it.id == id } }
    val progress = if (totalModules > 0) practicedCount.toFloat() / totalModules.toFloat() else 0f

    AcademyCard(sectionLabel = "Verifiable Curriculum") {
        Text(
            text = "Mistake Taxonomy",
            style = ChessTutorTypography.titleLarge
        )
        Text(
            text = "Each module targets a strictly checkable failure mode. Every pattern is verifiable by concrete engine logic.",
            style = ChessTutorTypography.bodyMedium,
            color = ChessTutorColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CURRICULUM PROGRESS: $practicedCount OF $totalModules PRACTICED",
                style = ChessTutorTypography.labelSmall,
                color = ChessTutorColors.Primary
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = ChessTutorTypography.labelSmall,
                color = ChessTutorColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ChessTutorColors.Primary,
            trackColor = ChessTutorColors.SurfaceElevated
        )
    }

    mistakeModules.forEach { module ->
        key(module.id) {
            LessonCard(
                lessonId = module.id,
                title = module.title,
                category = module.category,
                description = module.description,
                exampleFen = module.fen,
                status = getModuleStatus(module.id, state.practicedModules, state.masteredModules),
                onPractice = {
                    viewModel.practiceCurriculumLesson(
                        lessonId = module.id,
                        fen = module.fen,
                        title = module.title,
                        category = module.category,
                        description = module.description,
                        recommendedMoveUci = module.recommendedMoveUci
                    )
                }
            )
        }
    }
}

@Composable
private fun LearnSection(viewModel: AppViewModel) {
    AcademyCard(sectionLabel = "Instructional Reference") {
        Text(
            text = "Chess Fundamentals & Theory",
            style = ChessTutorTypography.titleLarge
        )
        Text(
            text = "Structured masterclasses across Openings, Middlegame, and Endgame. Tap any topic to expand key principles and explore the exact position interactively on the board.",
            style = ChessTutorTypography.bodyMedium,
            color = ChessTutorColors.TextSecondary
        )
    }

    val phases = listOf(
        Pair(LearnCategory.OPENING, "Phase 1: Opening Principles & Repertoire"),
        Pair(LearnCategory.MIDDLEGAME, "Phase 2: Middlegame Strategy & Outposts"),
        Pair(LearnCategory.ENDGAME, "Phase 3: Endgame Technique & Precision")
    )

    phases.forEach { (category, phaseTitle) ->
        key(category.name) {
            val topicsForCategory = LearnCurriculumRepository.topics.filter { it.category == category }
            LearnPhaseCard(
                title = phaseTitle,
                subtitle = category.displayName,
                topics = topicsForCategory,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun LearnPhaseCard(
    title: String,
    subtitle: String,
    topics: List<LearnTopic>,
    viewModel: AppViewModel
) {
    AcademyCard(sectionLabel = subtitle.uppercase()) {
        Text(text = title, style = ChessTutorTypography.titleLarge)
        Text(text = subtitle, style = ChessTutorTypography.bodyMedium, color = ChessTutorColors.TextSecondary)

        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            topics.forEachIndexed { index, topic ->
                key(topic.id) {
                    LearnTopicCard(
                        topic = topic,
                        initiallyExpanded = index == 0,
                        onExplore = { viewModel.exploreLearnTopic(topic) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnTopicCard(
    topic: LearnTopic,
    initiallyExpanded: Boolean,
    onExplore: () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChessTutorColors.SurfaceElevated)
            .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            // Header row with tap-to-expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.title,
                        style = ChessTutorTypography.titleMedium,
                        color = ChessTutorColors.TextPrimary
                    )
                    Text(
                        text = topic.subtitle,
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.TextSecondary
                    )
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = ChessTutorColors.Primary
                    )
                }
            }

            // Progressive Disclosure content
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = topic.summary,
                        style = ChessTutorTypography.bodyMedium,
                        color = ChessTutorColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "KEY PRINCIPLES:",
                        style = ChessTutorTypography.labelSmall,
                        color = ChessTutorColors.Primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    topic.keyPrinciples.forEach { principle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ChessTutorColors.Primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = principle,
                                style = ChessTutorTypography.bodyMedium,
                                color = ChessTutorColors.TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChessTutorColors.Surface)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = ChessTutorColors.Primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = topic.moveExplanation,
                                style = ChessTutorTypography.bodyMedium,
                                color = ChessTutorColors.TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Secondary action button (visually distinct from primary practice buttons)
                    Button(
                        onClick = onExplore,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChessTutorColors.Surface,
                            contentColor = ChessTutorColors.Primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Explore Position on Board",
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.Primary
                        )
                    }
                }
            }
        }
    }
}

enum class ModuleProgressStatus {
    NOT_STARTED,
    PRACTICED,
    MASTERED
}

private fun getModuleStatus(
    lessonId: String,
    practiced: Set<String>,
    mastered: Set<String>
): ModuleProgressStatus {
    return when {
        mastered.contains(lessonId) -> ModuleProgressStatus.MASTERED
        practiced.contains(lessonId) -> ModuleProgressStatus.PRACTICED
        else -> ModuleProgressStatus.NOT_STARTED
    }
}

@Composable
private fun LessonCard(
    lessonId: String,
    title: String,
    category: String,
    description: String,
    exampleFen: String,
    status: ModuleProgressStatus,
    onPractice: () -> Unit
) {
    AcademyCard(sectionLabel = category) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = ChessTutorTypography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(status = status)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = ChessTutorTypography.bodyMedium,
            color = ChessTutorColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        // Primary action button (bold primary container)
        Button(
            onClick = onPractice,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChessTutorColors.Primary,
                contentColor = ChessTutorColors.Background
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (status == ModuleProgressStatus.NOT_STARTED) "Start Practice" else "Practice Again",
                style = ChessTutorTypography.titleMedium,
                color = ChessTutorColors.Background
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = ChessTutorColors.Background)
        }
    }
}

@Composable
private fun StatusBadge(status: ModuleProgressStatus) {
    val (label, bg, textCol, icon) = when (status) {
        ModuleProgressStatus.MASTERED -> Quad(
            "Mastered",
            Color(0x2210B981),
            ChessTutorColors.Success,
            Icons.Default.CheckCircle
        )
        ModuleProgressStatus.PRACTICED -> Quad(
            "Practiced",
            Color(0x33F59E0B),
            ChessTutorColors.Primary,
            Icons.Default.HourglassTop
        )
        ModuleProgressStatus.NOT_STARTED -> Quad(
            "Not Started",
            Color(0x15FFFFFF),
            ChessTutorColors.TextSecondary,
            Icons.Default.RadioButtonUnchecked
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, textCol.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textCol,
                modifier = Modifier.height(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = ChessTutorTypography.labelSmall,
                color = textCol
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
