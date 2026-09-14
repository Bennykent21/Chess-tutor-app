package com.chesstutor.app.ui.curriculum

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
        // Top Navigation Sub-tab Toggle
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

@Composable
private fun MistakePatternsSection(
    state: AppUiState,
    viewModel: AppViewModel
) {
    val totalModules = 4
    val practicedCount = state.practicedModules.size.coerceAtMost(totalModules)
    val progress = practicedCount.toFloat() / totalModules.toFloat()

    AcademyCard(sectionLabel = "Verifiable Curriculum") {
        Text(
            text = "Mistake Taxonomy",
            style = ChessTutorTypography.titleLarge
        )
        Text(
            text = "Each module targets a strictly checkable failure mode. No vague assertions — every pattern is verifiable by engine logic.",
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

    LessonCard(
        lessonId = "lesson_mate_1",
        title = "Module 1: Missed Forced Mate",
        category = "MATE-IN-1 DETECTION",
        description = "Learn to identify mating nets instantly. When a forced mate exists, any non-mating move is classified as a critical mistake.",
        exampleFen = AppViewModel.FEN_MATE_IN_ONE,
        status = getModuleStatus("lesson_mate_1", state.practicedModules, state.masteredModules),
        onPractice = {
            viewModel.practiceCurriculumLesson(
                lessonId = "lesson_mate_1",
                fen = AppViewModel.FEN_MATE_IN_ONE,
                title = "Missed Forced Mate"
            )
        }
    )

    LessonCard(
        lessonId = "lesson_back_rank",
        title = "Module 2: Back-Rank Vulnerability",
        category = "TACTICAL DEFENSE",
        description = "Kings trapped behind their own pawns with no escape square ('luft'). Learn to identify the checkmate line before it happens.",
        exampleFen = AppViewModel.FEN_BACK_RANK_MATE,
        status = getModuleStatus("lesson_back_rank", state.practicedModules, state.masteredModules),
        onPractice = {
            viewModel.practiceCurriculumLesson(
                lessonId = "lesson_back_rank",
                fen = AppViewModel.FEN_BACK_RANK_MATE,
                title = "Back-Rank Vulnerability"
            )
        }
    )

    LessonCard(
        lessonId = "lesson_hanging_piece",
        title = "Module 3: Hanging Pieces",
        category = "MATERIAL PRESERVATION",
        description = "A piece is hanging if it is attacked and has zero defenders, or is defended fewer times than attacked. Spot hanging targets instantly.",
        exampleFen = AppViewModel.FEN_HANGING_PIECE,
        status = getModuleStatus("lesson_hanging_piece", state.practicedModules, state.masteredModules),
        onPractice = {
            viewModel.practiceCurriculumLesson(
                lessonId = "lesson_hanging_piece",
                fen = AppViewModel.FEN_HANGING_PIECE,
                title = "Hanging Pieces"
            )
        }
    )

    LessonCard(
        lessonId = "lesson_fork",
        title = "Module 4: Fork & Double Attack",
        category = "TACTICAL EXECUTION",
        description = "Delivering double threats with knights and queens. One threat cannot be defended while the other is executed.",
        exampleFen = AppViewModel.FEN_FORK_TACTIC,
        status = getModuleStatus("lesson_fork", state.practicedModules, state.masteredModules),
        onPractice = {
            viewModel.practiceCurriculumLesson(
                lessonId = "lesson_fork",
                fen = AppViewModel.FEN_FORK_TACTIC,
                title = "Fork & Double Attack"
            )
        }
    )
}

@Composable
private fun LearnSection(viewModel: AppViewModel) {
    AcademyCard(sectionLabel = "Instructional Reference") {
        Text(
            text = "Chess Fundamentals & Theory",
            style = ChessTutorTypography.titleLarge
        )
        Text(
            text = "Structured reference material across the three phases of chess. Use these principles to guide decisions when concrete tactical forcing lines are absent.",
            style = ChessTutorTypography.bodyMedium,
            color = ChessTutorColors.TextSecondary
        )
    }

    // Section 1: Openings
    TheorySectionCard(
        title = "Phase 1: Opening Principles & Repertoire",
        subtitle = "Development, Center Control, and King Safety",
        topics = listOf(
            TheoryTopic(
                title = "1. Central Occupation & Minor Piece Development",
                description = "Occupy the central squares (e4, d4) with pawns to restrict enemy piece mobility. Develop minor pieces toward the center (Knights before Bishops). Avoid moving the same piece multiple times before your pieces are active.",
                takeaway = "Rule of thumb: Every move in the first 8 moves must either stake out a central square, activate a piece, or protect the king.",
                exploreFen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                exploreTitle = "Italian Game Opening Development"
            ),
            TheoryTopic(
                title = "2. Early Castling & King Protection",
                description = "Clear the path between your King and Rook immediately. Castle within the first 7 to 10 moves. Never initiate an open confrontation in the center while your King remains on e1 or e8.",
                takeaway = "Rule of thumb: Keep the shield pawns (f2, g2, h2 or f7, g7, h7) unmoved unless tactically forced to prevent creating holes.",
                exploreFen = "r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQ1RK1 w - - 4 6",
                exploreTitle = "Castled King Safety"
            ),
            TheoryTopic(
                title = "3. Classical Opening Systems Overview",
                description = "• Open Games (1.e4 e5): Leads to rapid tactical skirmishes (Italian Game, Ruy Lopez).\n• Semi-Open Games (1.e4 c5): The Sicilian Defense creates asymmetrical counter-attacking play.\n• Closed Games (1.d4 d5): The Queen's Gambit offers a strategic battle over space and pawn structures.",
                takeaway = "Tip: Choose one system for White and two for Black (one vs 1.e4, one vs 1.d4) to build opening consistency.",
                exploreFen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
                exploreTitle = "Sicilian Defense Center Skirmish"
            )
        ),
        viewModel = viewModel
    )

    // Section 2: Middlegame
    TheorySectionCard(
        title = "Phase 2: Middlegame Strategy & Planning",
        subtitle = "Pawn Levers, Outposts, and Piece Coordination",
        topics = listOf(
            TheoryTopic(
                title = "1. Outposts & Structural Pawn Holes",
                description = "An outpost is a central square (usually on the 4th, 5th, or 6th rank) that can never be attacked by an opponent's pawn. Knights anchored on outposts dominate the entire board.",
                takeaway = "Strategy: When an opponent pushes a pawn, look immediately for the backward square left behind that can no longer be protected.",
                exploreFen = "r4rk1/pp1b1ppp/1qn1pn2/3p4/3P4/1PN1PN2/P3BPPP/R2Q1RK1 w - - 0 12",
                exploreTitle = "Middlegame Knight Outpost"
            ),
            TheoryTopic(
                title = "2. Pawn Breaks & Open Files",
                description = "Rooks need open files to exert influence. A pawn break is a pawn push that challenges the enemy pawn chain, forcing a pawn trade that opens a file or diagonal for your heavy pieces.",
                takeaway = "Rule of thumb: In closed positions, planning revolves entirely around preparing and executing the correct pawn break (e.g. c4, f4, or d5).",
                exploreFen = "r2q1rk1/1ppbbppp/p1np1n2/4p3/B3P3/2PP1N2/PP3PPP/RNBQR1K1 w - - 0 9",
                exploreTitle = "Pawn Break Preparation"
            ),
            TheoryTopic(
                title = "3. Piece Coordination & Battery Attacks",
                description = "Aligning complementary pieces—such as a Queen and Bishop along a long diagonal or doubling Rooks on an open file—multiplies their offensive force against weak defensive squares (f7, g7, h7).",
                takeaway = "Checklist: Before attacking, ensure all pieces participate. An attack conducted with only 1 or 2 pieces will almost always fail.",
                exploreFen = "r4rk1/1b2bppp/ppq1pn2/2p5/P2P1B2/2PB1N2/1P2QPPP/R4RK1 w - - 0 14",
                exploreTitle = "Coordinated Battery Alignment"
            )
        ),
        viewModel = viewModel
    )

    // Section 3: Endgame
    TheorySectionCard(
        title = "Phase 3: Endgame Technique & Precision",
        subtitle = "King Activity, Opposition, and Rook Endgames",
        topics = listOf(
            TheoryTopic(
                title = "1. The Active King in the Endgame",
                description = "Once queens are traded, the danger of checkmate decreases drastically. The King transforms from a vulnerable liability into an aggressive piece that must march directly to the center to support pawns.",
                takeaway = "Key rule: The player whose King reaches the center first wins a substantial positional advantage.",
                exploreFen = "8/5pk1/4p1p1/3pP3/3P1PP1/4K3/8/8 w - - 0 40",
                exploreTitle = "King March to the Center"
            ),
            TheoryTopic(
                title = "2. Opposition & Key Squares (Pawn Promotion)",
                description = "Direct opposition occurs when two Kings face each other on the same rank or file with one square between them. The player who does NOT have to move holds the opposition and can force the enemy King aside.",
                takeaway = "Calculation tip: Use the 'Square of the Pawn' rule to know whether your King can catch a passed pawn without counting move-by-move.",
                exploreFen = "8/4k3/8/4P3/8/4K3/8/8 w - - 0 1",
                exploreTitle = "King & Pawn Opposition"
            ),
            TheoryTopic(
                title = "3. Rook Endgame Essentials: Lucena & Philidor",
                description = "• Lucena Position: The winning technique where you build a 'bridge' with your Rook on the 4th rank to shield your King from checks while your pawn queens.\n• Philidor Position: The defensive technique keeping your Rook on the 6th rank to prevent the enemy King from penetrating.",
                takeaway = "Golden rule of rooks: Rooks belong behind passed pawns—both your own to push them, and your opponent's to halt them.",
                exploreFen = "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1",
                exploreTitle = "Lucena Bridge Technique"
            )
        ),
        viewModel = viewModel
    )
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
        Button(
            onClick = onPractice,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChessTutorColors.Primary,
                contentColor = ChessTutorColors.Background
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (status == ModuleProgressStatus.NOT_STARTED) "Start Practice" else "Practice Again")
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
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

private data class TheoryTopic(
    val title: String,
    val description: String,
    val takeaway: String,
    val exploreFen: String,
    val exploreTitle: String
)

@Composable
private fun TheorySectionCard(
    title: String,
    subtitle: String,
    topics: List<TheoryTopic>,
    viewModel: AppViewModel
) {
    AcademyCard(sectionLabel = "Instructional Guide") {
        Text(text = title, style = ChessTutorTypography.titleLarge)
        Text(text = subtitle, style = ChessTutorTypography.bodyMedium, color = ChessTutorColors.TextSecondary)

        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            topics.forEach { topic ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChessTutorColors.SurfaceElevated)
                        .border(1.dp, ChessTutorColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = topic.title,
                            style = ChessTutorTypography.titleMedium,
                            color = ChessTutorColors.Primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topic.description,
                            style = ChessTutorTypography.bodyMedium,
                            color = ChessTutorColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = topic.takeaway,
                            style = ChessTutorTypography.labelSmall,
                            color = ChessTutorColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.practiceCurriculumLesson(
                                    lessonId = "theory_${topic.exploreTitle.hashCode()}",
                                    fen = topic.exploreFen,
                                    title = topic.exploreTitle
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChessTutorColors.Surface,
                                contentColor = ChessTutorColors.Primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Explore in Coach")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

