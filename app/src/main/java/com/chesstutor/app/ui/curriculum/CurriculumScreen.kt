package com.chesstutor.app.ui.curriculum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        AcademyCard(sectionLabel = "Verifiable Curriculum") {
            Text(
                text = "Mistake Taxonomy",
                style = ChessTutorTypography.titleLarge
            )
            Text(
                text = "Each module targets a strictly checkable failure mode. No vague 'you weakened your position' assertions.",
                style = ChessTutorTypography.bodyMedium,
                color = ChessTutorColors.TextSecondary
            )
        }

        LessonCard(
            title = "Module 1: Missed Forced Mate",
            category = "MATE-IN-1 DETECTION",
            description = "Learn to identify mating nets instantly. When a forced mate exists, any non-mating move is classified as a critical mistake.",
            exampleFen = AppViewModel.FEN_MATE_IN_ONE,
            onPractice = {
                viewModel.practiceCurriculumLesson(
                    lessonId = "lesson_mate_1",
                    fen = AppViewModel.FEN_MATE_IN_ONE,
                    title = "Missed Forced Mate"
                )
            }
        )

        LessonCard(
            title = "Module 2: Back-Rank Vulnerability",
            category = "TACTICAL DEFENSE",
            description = "Kings trapped behind their own pawns with no escape square ('luft'). Learn to identify the checkmate line before it happens.",
            exampleFen = AppViewModel.FEN_BACK_RANK_MATE,
            onPractice = {
                viewModel.practiceCurriculumLesson(
                    lessonId = "lesson_back_rank",
                    fen = AppViewModel.FEN_BACK_RANK_MATE,
                    title = "Back-Rank Vulnerability"
                )
            }
        )

        LessonCard(
            title = "Module 3: Hanging Pieces",
            category = "MATERIAL PRESERVATION",
            description = "A piece is hanging if it is attacked and has zero defenders, or is defended fewer times than attacked. Learn to spot hanging targets instantly.",
            exampleFen = AppViewModel.FEN_HANGING_PIECE,
            onPractice = {
                viewModel.practiceCurriculumLesson(
                    lessonId = "lesson_hanging_piece",
                    fen = AppViewModel.FEN_HANGING_PIECE,
                    title = "Hanging Pieces"
                )
            }
        )

        LessonCard(
            title = "Module 4: Fork & Double Attack",
            category = "TACTICAL EXECUTION",
            description = "Delivering double threats with knights and queens. One threat cannot be defended while the other is executed.",
            exampleFen = AppViewModel.FEN_FORK_TACTIC,
            onPractice = {
                viewModel.practiceCurriculumLesson(
                    lessonId = "lesson_fork",
                    fen = AppViewModel.FEN_FORK_TACTIC,
                    title = "Fork & Double Attack"
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LessonCard(
    title: String,
    category: String,
    description: String,
    exampleFen: String,
    onPractice: () -> Unit
) {
    AcademyCard(sectionLabel = category) {
        Text(text = title, style = ChessTutorTypography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = description, style = ChessTutorTypography.bodyMedium, color = ChessTutorColors.TextSecondary)
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onPractice,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChessTutorColors.Primary,
                contentColor = ChessTutorColors.Background
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Practice in Coach Loop")
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}
