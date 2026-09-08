package com.example.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.chess.coaching.CoachingState
import com.example.chess.ui.theme.CoachAccentGold
import com.example.chess.ui.theme.CoachBadgeBg
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.liquidGlassCard
import com.example.chess.ui.theme.liquidGlassPill
import com.example.chess.ui.theme.TextBody
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

/**
 * Coach Dialogue Deck — Liquid Glass Interactive Deck.
 * Translucent frosted glass backing, specular highlights, and crisp pedagogical accents.
 */
@Composable
fun CoachDialogueDeck(
  state: CoachingState,
  onNextHint: () -> Unit,
  onPracticeInArena: () -> Unit,
  onSpeak: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .liquidGlassCard(
        shape = RoundedCornerShape(20.dp),
        borderBrush = LiquidGlassBorderGold,
        backgroundColor = Color(0xCC131720)
      )
      .padding(18.dp)
      .testTag("coach_dialogue_deck")
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top Tag & Concept Header
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
            imageVector = Icons.Default.School,
            contentDescription = null,
            tint = CoachPrimary,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = "COACH MENTORSHIP",
            color = CoachPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (onSpeak != null) {
            IconButton(
              onClick = onSpeak,
              modifier = Modifier
                .size(32.dp)
                .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = true)
            ) {
              Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Read Aloud",
                tint = CoachPrimary,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          Box(
            modifier = Modifier
              .liquidGlassPill(shape = RoundedCornerShape(8.dp), isActive = false)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = state.conceptTitle,
              color = CoachAccentGold,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // Title
      Text(
        text = state.title,
        color = TextTitle,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )

      // Main Pedagogical Narrative Explanation
      Text(
        text = state.explanationText,
        color = TextBody,
        fontSize = 13.sp,
        lineHeight = 18.sp
      )

      // 4-Level Hint Ladder Box (Active if hint requested)
      if (state.hintLevel > 0 && state.currentHintText != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(
              shape = RoundedCornerShape(12.dp),
              elevation = 4.dp,
              backgroundColor = Color(0x30F59E0B),
              borderBrush = LiquidGlassBorderGold
            )
            .padding(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Lightbulb,
              contentDescription = null,
              tint = CoachAccentGold,
              modifier = Modifier.size(18.dp)
            )
            Column {
              Text(
                text = "HINT (LEVEL ${state.hintLevel}/4)",
                color = CoachAccentGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
              )
              Text(
                text = state.currentHintText,
                color = TextTitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
              )
            }
          }
        }
      }

      // Action Controls: Hint Ladder Progression & Contextual "Practice This"
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (state.hintLadder != null && state.hintLevel < 4) {
          OutlinedButton(
            onClick = onNextHint,
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("request_hint_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoachPrimary),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = LiquidGlassBorderGold)
          ) {
            Icon(
              imageVector = Icons.Default.Lightbulb,
              contentDescription = null,
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
              text = if (state.hintLevel == 0) "Ask Hint" else "Deeper Hint (${state.hintLevel + 1}/4)",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        if (state.canPracticeInArena) {
          Button(
            onClick = onPracticeInArena,
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .testTag("practice_this_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = CoachPrimary,
              contentColor = Color(0xFF0F1115)
            )
          ) {
            Icon(
              imageVector = Icons.Default.SportsEsports,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
              text = "Practice in Arena",
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
