package com.example.chess.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
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
import com.example.chess.coaching.CurriculumLesson
import com.example.chess.coaching.CurriculumRepository
import com.example.chess.core.LegalMoveGenerator
import com.example.chess.core.Position
import com.example.chess.ui.components.ChessBoardTheme
import com.example.chess.ui.components.InteractiveChessBoard
import com.example.chess.ui.theme.CoachPrimary

@Composable
fun LearnScreen(
  onPracticeLessonInPlay: ((String) -> Unit)? = null
) {
  val lessons = remember { CurriculumRepository.allLessons }
  var selectedLesson by remember { mutableStateOf<CurriculumLesson?>(null) }
  var stepIndex by remember { mutableStateOf(0) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(10.dp))

    // Top Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.MenuBook,
          contentDescription = "Lessons",
          tint = CoachPrimary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = if (selectedLesson == null) "Chess Lessons" else selectedLesson!!.title,
          color = Color.White,
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold
        )
      }

      if (selectedLesson != null) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E242B))
            .clickable {
              selectedLesson = null
              stepIndex = 0
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
          Text("All Lessons", color = CoachPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (selectedLesson == null) {
      // List of Lesson Cards
      Text(
        text = "Select a masterclass to learn key ideas and opening strategy:",
        color = Color(0xFF94A3B8),
        fontSize = 13.sp,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp)
      )

      lessons.forEach { lesson ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF181B20))
            .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
            .clickable {
              selectedLesson = lesson
              stepIndex = 0
            }
            .padding(14.dp)
            .testTag("lesson_card_${lesson.id}")
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = lesson.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(0x22F59E0B))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = lesson.category,
                  color = CoachPrimary,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = lesson.summary,
              color = Color(0xFF94A3B8),
              fontSize = 12.sp,
              lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "Key Takeaway:",
                color = CoachPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = lesson.keyTakeaway,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp
              )
            }
          }
        }
      }
    } else {
      // Interactive Step Viewer
      val lesson = selectedLesson!!
      val currentStep = lesson.steps.getOrNull(stepIndex) ?: lesson.steps.first()
      val stepPos = remember(currentStep) {
        Position.tryFromFen(currentStep.startingFen).getOrNull() ?: Position.initial()
      }
      val nextPos = remember(currentStep, stepPos) {
        LegalMoveGenerator.makeMove(stepPos, currentStep.playedMove)
      }

      // Step indicator
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Step ${stepIndex + 1} of ${lesson.steps.size}",
          color = CoachPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = currentStep.conceptTitle,
          color = Color(0xFFCBD5E1),
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Board with lesson arrow
      InteractiveChessBoard(
        position = nextPos,
        flipped = false,
        boardTheme = ChessBoardTheme.CLASSIC_TOURNAMENT,
        selectedSquare = null,
        onSquareTapped = {},
        legalTargetSquares = emptySet(),
        recommendedArrow = currentStep.recommendedArrow,
        lastMove = currentStep.playedMove,
        modifier = Modifier.size(310.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Concept explanation card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFF181B20))
          .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
          .padding(14.dp)
      ) {
        Column {
          Text(
            text = "WHY THIS MOVE WORKS",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = currentStep.explanation,
            color = Color(0xFFF1F5F9),
            fontSize = 13.sp,
            lineHeight = 19.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Navigation Bar for Steps
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFF181B20))
          .border(1.dp, Color(0xFF2D323B), RoundedCornerShape(14.dp))
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Prev Step
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = stepIndex > 0) { stepIndex -= 1 }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = null,
              tint = if (stepIndex > 0) CoachPrimary else Color(0xFF475569),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Previous",
              color = if (stepIndex > 0) Color.White else Color(0xFF475569),
              fontSize = 12.sp
            )
          }
        }

        // Practice vs Stockfish
        if (onPracticeLessonInPlay != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(CoachPrimary.copy(alpha = 0.2f))
              .border(1.dp, CoachPrimary, RoundedCornerShape(8.dp))
              .clickable { onPracticeLessonInPlay(nextPos.toFen()) }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CoachPrimary,
                modifier = Modifier.size(16.dp)
              )
              Text("Practice vs Stockfish", color = CoachPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Next Step
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = stepIndex < lesson.steps.lastIndex) { stepIndex += 1 }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "Next",
              color = if (stepIndex < lesson.steps.lastIndex) Color.White else Color(0xFF475569),
              fontSize = 12.sp
            )
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              tint = if (stepIndex < lesson.steps.lastIndex) CoachPrimary else Color(0xFF475569),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(100.dp))
  }
}
