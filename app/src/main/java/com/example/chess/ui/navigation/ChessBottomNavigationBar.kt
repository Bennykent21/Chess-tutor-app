package com.example.chess.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.ui.theme.CoachPrimary
import com.example.chess.ui.theme.LiquidGlassBorder
import com.example.chess.ui.theme.LiquidGlassBorderGold
import com.example.chess.ui.theme.LiquidGlassSurface
import com.example.chess.ui.theme.LiquidGlassSurfaceElevated
import com.example.chess.ui.theme.TextMuted
import com.example.chess.ui.theme.TextTitle

enum class ChessAppTab(val label: String, val icon: ImageVector) {
  PLAY("Play", Icons.Default.SportsEsports),
  ANALYZE("Analyze", Icons.Default.AutoGraph),
  PUZZLES("Puzzles", Icons.Default.Extension),
  LEARN("Learn", Icons.AutoMirrored.Filled.MenuBook)
}

/**
 * Liquid Glass Floating Dock Navigation Bar.
 * Features specular top highlights, subtle refraction surface,
 * and high-contrast glowing active glass pill.
 */
@Composable
fun ChessBottomNavigationBar(
  currentTab: ChessAppTab,
  onTabSelected: (ChessAppTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val barShape = RoundedCornerShape(26.dp)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 10.dp)
      .height(68.dp)
      .shadow(16.dp, barShape, ambientColor = Color(0x60000000), spotColor = Color(0x90000000))
      .clip(barShape)
      .background(Color(0xCC11151D)) // Frosted tinted dark glass backing
      .background(LiquidGlassSurfaceElevated) // Frosted glass highlight
      .border(1.dp, LiquidGlassBorder, barShape)
      .testTag("bottom_nav_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      ChessAppTab.values().forEach { tab ->
        val isSelected = tab == currentTab
        val tintColor by animateColorAsState(
          targetValue = if (isSelected) CoachPrimary else TextMuted,
          label = "nav_color_${tab.name}"
        )

        val pillShape = RoundedCornerShape(18.dp)

        Box(
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .clip(pillShape)
            .then(
              if (isSelected) {
                Modifier
                  .background(Color(0x33F59E0B)) // Golden amber refraction glow
                  .border(1.dp, LiquidGlassBorderGold, pillShape)
              } else {
                Modifier
              }
            )
            .clickable { onTabSelected(tab) }
            .testTag("nav_tab_${tab.name.lowercase()}"),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = tab.icon,
              contentDescription = tab.label,
              tint = tintColor,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = tab.label,
              color = if (isSelected) TextTitle else TextMuted,
              fontSize = 10.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              letterSpacing = 0.6.sp,
              modifier = Modifier.padding(top = 2.dp)
            )
          }
        }
      }
    }
  }
}

