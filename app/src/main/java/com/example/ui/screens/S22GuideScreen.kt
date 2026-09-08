package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import com.example.ui.components.FrostedGlassBox
import com.example.ui.model.S22OptimizationTip
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextSlate100
import com.example.ui.theme.TextSlate300
import com.example.ui.theme.TextSlate400

@Composable
fun S22GuideScreen(
  tips: List<S22OptimizationTip>,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .padding(top = 16.dp, bottom = 90.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
          text = "HARDWARE TUNING",
          color = TextSlate400,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp
        )
        Text(
          text = "Samsung S22 eFootball Guide",
          color = TextSlate100,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }

    item {
      // Pro-tip banner
      FrostedGlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = Color(0x1A22D3EE),
        borderColor = GlassBorderHighlight
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(24.dp)
          )
          Column {
            Text(
              text = "Why Native S22 Tuning Beats Generic Boosters",
              color = CyanPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = "The Galaxy S22's Snapdragon 8 Gen 1 throttles if pushed past 42°C. These 4 hardware & network configurations eliminate micro-stutter without fake task-killers.",
              color = TextSlate300,
              fontSize = 12.sp,
              lineHeight = 16.sp,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    }

    items(tips, key = { it.id }) { tip ->
      TipCard(tip = tip)
    }
  }
}

@Composable
private fun TipCard(tip: S22OptimizationTip) {
  FrostedGlassBox(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("tip_item_${tip.id}"),
    shape = RoundedCornerShape(24.dp),
    backgroundColor = GlassSurface,
    borderColor = GlassBorder
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = tip.title,
          color = TextSlate100,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = AccentEmerald,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "RECOMMENDED",
            color = AccentEmerald,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
          )
        }
      }

      Text(
        text = tip.subtitle,
        color = CyanPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
      )

      Text(
        text = tip.detailedSteps,
        color = TextSlate300,
        fontSize = 12.sp,
        lineHeight = 17.sp
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0x14FFFFFF))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Text(
          text = "Result: ${tip.benefit}",
          color = TextSlate400,
          fontSize = 11.sp,
          fontWeight = FontWeight.Normal
        )
      }
    }
  }
}
