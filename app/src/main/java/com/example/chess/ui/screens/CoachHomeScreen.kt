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
import com.example.chess.ui.theme.LiquidGlassBorderSubtle
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
 * Refined with a single primary accent, calm surfaces, and a dominant next study action hero.
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
  val hasDueMistakes = dueMistakes.isNotEmpty()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .padding(top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Header Bar
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

        // Clean Streak & Rating Badge
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
              contentDescription = "Rating",
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

    // 2. DOMINANT HERO: Next Study Action
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassCard(
            shape = RoundedCornerShape(20.dp),
            borderBrush = LiquidGlassBorderGold,
            backgroundColor = LiquidGlassSurfaceElevated
          )
          .padding(20.dp)
          .testTag("dominant_study_hero_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(CoachAccentGold)
              )
              Text(
                text = "RECOMMENDED NEXT ACTION",
                color = CoachAccentGold,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x26F59E0B))
                .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = if (hasDueMistakes) "${dueMistakes.size} Due" else "Active Line",
                color = CoachAccentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = if (hasDueMistakes) {
              "Clear ${dueMistakes.size} Spaced-Review Blunders"
            } else {
              activeLesson.title
            },
            color = TextTitle,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = if (hasDueMistakes) {
              "Immediate repetition reinforces correct move instincts before mistakes solidify into bad habits."
            } else {
              activeLesson.summary
            },
            color = TextBody,
            fontSize = 13.sp,
            lineHeight = 18.sp
          )

          Button(
            onClick = {
              if (hasDueMistakes) onStartSpacedReview() else onResumeLesson(activeLesson)
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("dominant_study_action_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CoachAccentGold,
              contentColor = Color(0xFF0F1115)
            )
          ) {
            Icon(
              imageVector = if (hasDueMistakes) Icons.Default.Refresh else Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
              text = if (hasDueMistakes) "Start Priority Review (${dueMistakes.size})" else "Continue Lesson",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // 3. Compact Metrics Row
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        MetricTile(
          title = "Tactics Rating",
          value = "$userTacticsRating",
          subtext = "$puzzlesSolvedCount Solved",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          title = "Mistake Queue",
          value = "${dueMistakes.size}",
          subtext = "In Memory Book",
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 4. Secondary Action Cards: Placement Assessment & Tactics Dojo
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "PRACTICE & DIAGNOSTICS",
          color = TextMuted,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )

        // Tactics Dojo Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("tactics_dojo_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
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
                  text = "Tactics Dojo & Voice Coach",
                  color = TextTitle,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Text(
                text = "Four-tier pedagogical hint ladder with voice commentary.",
                color = TextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
              )
            }

            OutlinedButton(
              onClick = onOpenTacticsDojo,
              modifier = Modifier
                .padding(start = 12.dp)
                .testTag("open_tactics_dojo_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachAccentGold),
              border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold)
            ) {
              Text("Enter Dojo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Placement Test Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("placement_test_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Psychology,
                  contentDescription = null,
                  tint = TextMuted,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = "Skill Placement Assessment",
                  color = TextTitle,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Text(
                text = "5 rapid diagnostics to calibrate your rating and curriculum.",
                color = TextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
              )
            }

            OutlinedButton(
              onClick = onStartPlacementAssessment,
              modifier = Modifier
                .padding(start = 12.dp)
                .testTag("take_placement_test_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTitle),
              border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderSubtle)
            ) {
              Text("Calibrate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Spaced Repetition Card (preserved testTag)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("spaced_repetition_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Personal Mistake Book",
                color = TextTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${dueMistakes.size} mistakes scheduled for 1/3/7-day review.",
                color = TextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
              )
            }

            Button(
              onClick = onStartSpacedReview,
              modifier = Modifier
                .padding(start = 12.dp)
                .testTag("review_blunders_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = LiquidGlassSurfaceElevated,
                contentColor = CoachAccentGold
              )
            ) {
              Text("Review (${dueMistakes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Active Curriculum Lesson Card (preserved testTag)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(shape = RoundedCornerShape(16.dp))
            .clickable { onResumeLesson(activeLesson) }
            .padding(16.dp)
            .testTag("active_curriculum_card")
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "CURRICULUM MODULE",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = activeLesson.ecoCode,
                color = CoachAccentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
            Text(
              text = activeLesson.title,
              color = TextTitle,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }

    // 5. Sparring Arena Launcher (Calibrated Tiers)
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
private fun MetricTile(
  title: String,
  value: String,
  subtext: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .liquidGlassCard(shape = RoundedCornerShape(14.dp), elevation = 1.dp)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Column {
      Text(text = title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
      Text(
        text = value,
        color = TextTitle,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 2.dp)
      )
      Text(text = subtext, color = CoachAccentGold, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
      .liquidGlassCard(shape = RoundedCornerShape(12.dp), elevation = 2.dp)
      .clickable { onClick() }
      .padding(horizontal = 6.dp, vertical = 10.dp)
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
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = level.title.substringBefore(" ("),
        color = TextBody,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1
      )
    }
  }
}
