package com.chesstutor.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.data.model.PlacementAssessment
import com.chesstutor.app.ui.components.ChessBoard
import com.chesstutor.app.ui.theme.ChessTutorColors
import com.chesstutor.app.ui.theme.bouncyClickable
import com.chesstutor.app.viewmodel.AppUiState
import com.chesstutor.app.viewmodel.AppViewModel

@Composable
fun OnboardingScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(ChessTutorColors.Background).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text("CHESS TUTOR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = ChessTutorColors.Brass)
        Spacer(Modifier.height(22.dp))
        when (state.assessmentState) {
            "IN_PROGRESS" -> Assessment(state, viewModel)
            "COMPLETE" -> Results(state)
            else -> GoalSelection(state, viewModel)
        }
    }
}

@Composable
private fun GoalSelection(state: AppUiState, viewModel: AppViewModel) {
    Text("Let’s build your training plan.", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.TextPrimary)
    Spacer(Modifier.height(8.dp))
    Text("First, tell us what you want to improve. We’ll use this to shape your training.", fontSize = 14.sp, color = ChessTutorColors.TextSecondary)
    Spacer(Modifier.height(28.dp))
    val goals = listOf(
        "GENERAL_IMPROVEMENT" to ("Improve overall" to "A balanced mix of tactics, strategy and play"),
        "TACTICS" to ("Tactics" to "Spot combinations, threats and winning moves"),
        "GAME_ANALYSIS" to ("Understand my games" to "Turn your mistakes into targeted practice"),
        "ENDGAMES" to ("Endgames" to "Build reliable technique when pieces come off")
    )
    goals.forEach { (key, label) ->
        val selected = state.learningGoal == key
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected) ChessTutorColors.Surface3 else ChessTutorColors.Surface2)
                .border(1.dp, if (selected) ChessTutorColors.Brass else ChessTutorColors.Line, RoundedCornerShape(14.dp))
                .bouncyClickable { viewModel.setLearningGoal(key) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label.first, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(label.second, fontSize = 12.sp, color = ChessTutorColors.TextSecondary)
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = ChessTutorColors.Brass, modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    ActionButton("Find my starting level") { viewModel.startPlacementAssessment() }
}

@Composable
private fun Assessment(state: AppUiState, viewModel: AppViewModel) {
    val total = PlacementAssessment.questions.size
    val current = (state.assessmentPositionIndex + 1).coerceIn(1, total)
    val question = PlacementAssessment.questions[state.assessmentPositionIndex.coerceIn(0, total - 1)]
    Text("Find your starting level", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.TextPrimary)
    Spacer(Modifier.height(7.dp))
    Text("Solve " + total + " positions. They get progressively harder. This is a training estimate, not an official rating.", fontSize = 13.sp, color = ChessTutorColors.TextSecondary)
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(ChessTutorColors.Surface2)) {
            Box(Modifier.fillMaxWidth(current.toFloat() / total).height(4.dp).clip(RoundedCornerShape(3.dp)).background(ChessTutorColors.Brass))
        }
        Text(current.toString() + "/" + total, Modifier.padding(start = 10.dp), fontSize = 12.sp, color = ChessTutorColors.TextTertiary)
    }
    Text(question.skill.replaceFirstChar { it.uppercase() }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
    ChessBoard(
        fen = state.fen,
        selectedSquare = state.selectedSquare,
        legalTargets = state.legalTargets,
        lastMove = state.lastMove,
        recommendedArrow = null,
        flipped = false,
        onSquareTapped = viewModel::onSquareTapped,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    )
    Spacer(Modifier.height(12.dp))
    Text(state.message.ifBlank { "Find the best move." }, fontSize = 13.sp, color = ChessTutorColors.TextSecondary)
}

@Composable
private fun Results(state: AppUiState) {
    Text("Your starting level", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.TextPrimary)
    Spacer(Modifier.height(8.dp))
    Text("Your placement gives us a starting training band. You can improve it through actual practice.", fontSize = 14.sp, color = ChessTutorColors.TextSecondary)
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ChessTutorColors.Surface2).border(1.dp, ChessTutorColors.Line, RoundedCornerShape(18.dp)).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(state.estimatedRating.toString(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = ChessTutorColors.Brass)
            Text("estimated training rating", fontSize = 12.sp, color = ChessTutorColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(state.assessmentCorrect.toString() + "/" + state.assessmentTotal + " positions solved", fontSize = 14.sp, color = ChessTutorColors.TextPrimary)
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        when (state.learningGoal) {
            "TACTICS" -> "Your first training focus will emphasize tactical recognition."
            "GAME_ANALYSIS" -> "Your first training focus will emphasize mistakes and game review."
            "ENDGAMES" -> "Your first training focus will emphasize endgame technique."
            else -> "Your first training plan will balance tactics, principles and practical play."
        },
        fontSize = 14.sp,
        color = ChessTutorColors.TextSecondary
    )
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(13.dp))
            .background(ChessTutorColors.Brass).bouncyClickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChessTutorColors.BrassInk)
    }
}
