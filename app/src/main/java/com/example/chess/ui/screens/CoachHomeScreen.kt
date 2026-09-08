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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.coaching.CurriculumLesson
import com.example.chess.data.MistakeRecord
import com.example.chess.engine.TrainingLevel
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassBorderCyan
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.LiquidGlassSurfaceSubtle
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.StatusBlunder
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

/**
 * Tab 1: Coach (The Studio / Home).
 * Refined with a tactile Liquid Glass theme, translucent layers, and specular rim accents.
 */
@Composable
fun CoachHomeScreen(
  userEstimatedRating: Int,
  userTacticsRating: Int = 1100,
  puzzlesSolvedCount: Int = 0,
  dueMistakes: List<MistakeRecord>,
  activeLesson: CurriculumLesson,
  onStartPlacementAssessment: () -> Unit,
  onOpenTacticsDojo: () -> Unit,
  onStartSpacedReview: () -> Unit,
  onResumeLesson: (CurriculumLesson) -> Unit,
  onLaunchSparring: (TrainingLevel) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .padding(top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.School,
              contentDescription = null,
              tint = CoachPrimary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "CHESS TUTOR ACADEMY",
              color = CoachPrimary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.1.sp
            )
          }
          Text(
            text = "Welcome to the Studio",
            color = TextTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        // Clean Streak & Rating Badge (Glass Pill)
        Box(
          modifier = Modifier
            .liquidGlassPill(shape = RoundedCornerShape(14.dp), isActive = true)
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Bolt,
              contentDescription = "Daily Streak",
              tint = CoachAccentGold,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "$userEstimatedRating ELO",
              color = CoachAccentGold,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // Diagnostic Placement Test Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(20.dp),
            borderBrush = LiquidGlassBorderCyan,
            backgroundColor = Color(0x180284C7) // Glass cyan tint
          )
          .padding(20.dp)
          .testTag("placement_test_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "SKILL ASSESSMENT",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            }

            Text(
              text = "5 Diagnostics",
              color = CoachAccentGold,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Text(
            text = "Adaptive Skill Placement & Rating Test",
            color = TextTitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = "Solve 5 rapid tactical and strategic positions to calibrate your exact ELO rating and receive personalized curriculum recommendations.",
            color = TextBody,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
          )

          OutlinedButton(
            onClick = onStartPlacementAssessment,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("take_placement_test_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderCyan)
          ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("Take Skill Placement Test", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // High Impact Feature: Tactical Puzzle Dojo with Calibrated Elo & Voice Coach
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(20.dp),
            borderBrush = LiquidGlassBorderGold,
            backgroundColor = Color(0x22F59E0B) // Amber liquid glass
          )
          .padding(20.dp)
          .testTag("tactics_dojo_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = CoachAccentGold,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "TACTICAL PUZZLE DOJO",
                color = CoachAccentGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            }

            Box(
              modifier = Modifier
                .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "$userTacticsRating ELO",
                color = CoachAccentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = "Tactics Dojo & Voice Coach",
            color = TextTitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = "Sharpen pattern recognition with forks, skewers, pins, and endgames. Features progressive 4-tier pedagogical hints and spoken audio commentary.",
            color = TextBody,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
          )

          Button(
            onClick = onOpenTacticsDojo,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("open_tactics_dojo_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CoachAccentGold,
              contentColor = Color(0xFF0F1115)
            )
          ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("Enter Puzzle Dojo ($puzzlesSolvedCount Solved)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Daily Concept Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(20.dp))
          .padding(20.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(CoachPrimary)
            )
            Text(
              text = "TODAY'S CONCEPT FOCUS",
              color = CoachPrimary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
          }
          Text(
            text = "Defending Against Early Queen Attacks",
            color = TextTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Learn why developing pieces naturally and defending e5 with Nc6 neutralizes premature Queen raids while winning tempo.",
            color = TextBody,
            fontSize = 13.sp,
            lineHeight = 18.sp
          )
        }
      }
    }

    // Killer Loop Feature: Spaced Repetition Personal Mistake Queue
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(20.dp))
          .padding(20.dp)
          .testTag("spaced_repetition_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "PERSONAL MISTAKE BOOK",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = "${dueMistakes.size} Blunders Due for Review",
                color = TextTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(StatusBlunder.copy(alpha = 0.2f))
                .border(1.dp, StatusBlunder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "Spaced Repetition",
                color = StatusBlunder,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = "Every mistake you make in the Arena is filed into your personal memory book and resurfaces 1, 3, and 7 days later until mastered.",
            color = TextBody,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
          )

          Button(
            onClick = onStartSpacedReview,
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("review_blunders_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CoachPrimary,
              contentColor = Color(0xFF0F1115)
            )
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
              text = "Start Spaced Review (${dueMistakes.size} positions)",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // Active Curriculum Lesson Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(shape = RoundedCornerShape(20.dp))
          .clickable { onResumeLesson(activeLesson) }
          .padding(20.dp)
          .testTag("active_curriculum_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "ACTIVE CURRICULUM",
              color = TextMuted,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
            Text(
              text = activeLesson.ecoCode,
              color = CoachAccentGold,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Text(
            text = activeLesson.title,
            color = TextTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = activeLesson.summary,
            color = TextBody,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Step 1 of ${activeLesson.steps.size}",
              color = CoachPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "Resume Lesson",
                color = CoachPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = CoachPrimary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }

    // Quick Sparring Arena Launcher (Calibrated Training Levels 800 - 1800)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "SPARRING ARENA (CALIBRATED TIERS)",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
          Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = null,
            tint = CoachPrimary,
            modifier = Modifier.size(16.dp)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          TrainingLevel.values().take(3).forEach { level ->
            SparringQuickTile(
              level = level,
              onClick = { onLaunchSparring(level) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          TrainingLevel.values().drop(3).take(3).forEach { level ->
            SparringQuickTile(
              level = level,
              onClick = { onLaunchSparring(level) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SparringQuickTile(
  level: TrainingLevel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .liquidGlassCard(shape = RoundedCornerShape(14.dp), elevation = 4.dp)
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 12.dp)
      .testTag("sparring_tier_${level.elo}"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = "${level.elo}",
        color = CoachAccentGold,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = level.title.substringBefore(" ("),
        color = TextBody,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}
