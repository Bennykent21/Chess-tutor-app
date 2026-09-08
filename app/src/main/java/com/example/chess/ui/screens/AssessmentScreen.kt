package com.example.chess.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.assessment.AssessmentPosition
import com.example.chess.assessment.AssessmentRepository
import com.example.chess.core.Move
import com.example.chess.core.Position
import com.example.chess.core.Square
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.theme.CanvasBackground
import com.example.chess.ui.theme.CanvasCard
import com.example.chess.ui.theme.CanvasCardBorder
import com.example.chess.ui.theme.CanvasCardElevated
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.StatusExcellent
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

@Composable
fun AssessmentScreen(
  onAssessmentCompleted: (Int) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  var currentIndex by remember { mutableStateOf(0) }
  val questions = AssessmentRepository.placementQuestions
  val currentQuestion = questions.getOrNull(currentIndex)
  val correctSet = remember { mutableSetOf<Int>() }

  var selectedMoveUci by remember { mutableStateOf<String?>(null) }
  var isAnswerSubmitted by remember { mutableStateOf(false) }
  var isFinished by remember { mutableStateOf(false) }

  if (isFinished || currentQuestion == null) {
    val estimatedRating = AssessmentRepository.calculateRating(correctSet)
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(CanvasBackground)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(CanvasCard)
          .border(1.dp, CoachPrimary, RoundedCornerShape(20.dp))
          .padding(24.dp)
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(CoachPrimary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Stars,
            contentDescription = null,
            tint = CoachAccentGold,
            modifier = Modifier.size(36.dp)
          )
        }

        Text(
          text = "Placement Complete!",
          color = TextTitle,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold
        )

        Text(
          text = "Estimated Skill Rating",
          color = TextMuted,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )

        Text(
          text = "$estimatedRating ELO",
          color = CoachAccentGold,
          fontSize = 36.sp,
          fontWeight = FontWeight.ExtraBold
        )

        Text(
          text = "You solved ${correctSet.size} of ${questions.size} positions correctly. Your sparring bot baseline and curriculum recommendations have been calibrated to your skill tier.",
          color = TextBody,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )

        Button(
          onClick = { onAssessmentCompleted(estimatedRating) },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("apply_rating_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CoachPrimary,
            contentColor = Color(0xFF0F1115)
          )
        ) {
          Text("Begin Training at $estimatedRating", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
      }
    }
    return
  }

  val pos = Position.fromFen(currentQuestion.fen)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(CanvasBackground)
      .padding(horizontal = 16.dp)
      .padding(top = 12.dp, bottom = 90.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Header Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = CoachPrimary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "SKILL PLACEMENT TEST",
            color = CoachPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
        }

        Text(
          text = "Question ${currentIndex + 1} of ${questions.size}",
          color = CoachAccentGold,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Question topic & text
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(CanvasCard)
          .border(1.dp, CanvasCardBorder, RoundedCornerShape(14.dp))
          .padding(14.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = currentQuestion.topic,
            color = CoachAccentGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = currentQuestion.question,
            color = TextTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp
          )
        }
      }
    }

    // Chessboard view
    item {
      InteractiveChessBoard(
        position = pos,
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp)
      )
    }

    // Two choices: Best Move vs Plausible Alternative
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val optionA = currentQuestion.bestMoveUci
        val optionB = currentQuestion.plausibleAlternativeUci

        val isACorrect = optionA == currentQuestion.bestMoveUci
        val isBCorrect = optionB == currentQuestion.bestMoveUci

        OptionCard(
          moveUci = optionA,
          isSelected = selectedMoveUci == optionA,
          isSubmitted = isAnswerSubmitted,
          isCorrect = isACorrect,
          onClick = {
            if (!isAnswerSubmitted) selectedMoveUci = optionA
          }
        )

        OptionCard(
          moveUci = optionB,
          isSelected = selectedMoveUci == optionB,
          isSubmitted = isAnswerSubmitted,
          isCorrect = isBCorrect,
          onClick = {
            if (!isAnswerSubmitted) selectedMoveUci = optionB
          }
        )
      }
    }

    // Feedback and Next Button
    item {
      if (isAnswerSubmitted) {
        val isCorrect = selectedMoveUci == currentQuestion.bestMoveUci
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CanvasCardElevated)
            .border(
              1.dp,
              if (isCorrect) StatusExcellent else StatusBlunder,
              RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (isCorrect) StatusExcellent else StatusBlunder,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = if (isCorrect) "Correct Move!" else "Suboptimal Move",
                color = if (isCorrect) StatusExcellent else StatusBlunder,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Text(
              text = if (isCorrect) currentQuestion.bestMoveConcept else currentQuestion.alternativeRefutation,
              color = TextBody,
              fontSize = 12.5.sp,
              lineHeight = 17.sp
            )

            Button(
              onClick = {
                if (isCorrect) correctSet.add(currentIndex)
                if (currentIndex + 1 < questions.size) {
                  currentIndex++
                  selectedMoveUci = null
                  isAnswerSubmitted = false
                } else {
                  isFinished = true
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(40.dp)
                .testTag("next_assessment_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = CoachPrimary,
                contentColor = Color(0xFF0F1115)
              )
            ) {
              Text(
                text = if (currentIndex + 1 < questions.size) "Next Diagnostic" else "See Result",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      } else {
        Button(
          onClick = {
            if (selectedMoveUci != null) {
              isAnswerSubmitted = true
            }
          },
          enabled = selectedMoveUci != null,
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("submit_assessment_answer"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CoachPrimary,
            contentColor = Color(0xFF0F1115),
            disabledContainerColor = CanvasCardBorder
          )
        ) {
          Text("Submit Move Choice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
private fun OptionCard(
  moveUci: String,
  isSelected: Boolean,
  isSubmitted: Boolean,
  isCorrect: Boolean,
  onClick: () -> Unit
) {
  val borderColor = when {
    isSubmitted && isCorrect -> StatusExcellent
    isSubmitted && isSelected && !isCorrect -> StatusBlunder
    isSelected -> CoachPrimary
    else -> CanvasCardBorder
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) CanvasCardElevated else CanvasCard)
      .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
      .clickable(enabled = !isSubmitted) { onClick() }
      .padding(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Play move: $moveUci",
        color = TextTitle,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )

      if (isSubmitted && isCorrect) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = "Correct",
          tint = StatusExcellent,
          modifier = Modifier.size(18.dp)
        )
      } else if (isSubmitted && isSelected && !isCorrect) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Incorrect",
          tint = StatusBlunder,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}
