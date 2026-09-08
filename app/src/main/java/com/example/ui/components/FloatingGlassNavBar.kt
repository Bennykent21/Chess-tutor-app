package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.NavTab
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextSlate500

@Composable
fun FloatingGlassNavBar(
  currentTab: NavTab,
  onTabSelected: (NavTab) -> Unit,
  modifier: Modifier = Modifier
) {
  FrostedGlassBox(
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 8.dp)
      .height(68.dp),
    shape = RoundedCornerShape(34.dp),
    backgroundColor = GlassSurfaceElevated,
    borderColor = GlassBorderHighlight
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
        .padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      NavBarItem(
        icon = Icons.Default.RocketLaunch,
        label = "BOOST",
        isSelected = currentTab == NavTab.BOOST,
        onClick = { onTabSelected(NavTab.BOOST) },
        testTag = "nav_tab_boost"
      )

      NavBarItem(
        icon = Icons.Default.SportsEsports,
        label = "GAMES",
        isSelected = currentTab == NavTab.GAMES,
        onClick = { onTabSelected(NavTab.GAMES) },
        testTag = "nav_tab_games"
      )

      NavBarItem(
        icon = Icons.Default.Analytics,
        label = "MONITOR",
        isSelected = currentTab == NavTab.MONITOR,
        onClick = { onTabSelected(NavTab.MONITOR) },
        testTag = "nav_tab_monitor"
      )

      NavBarItem(
        icon = Icons.Default.Info,
        label = "S22 TIPS",
        isSelected = currentTab == NavTab.S22_TIPS,
        onClick = { onTabSelected(NavTab.S22_TIPS) },
        testTag = "nav_tab_tips"
      )
    }
  }
}

@Composable
private fun NavBarItem(
  icon: ImageVector,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String
) {
  val tintColor by animateColorAsState(
    targetValue = if (isSelected) CyanPrimary else TextSlate500,
    label = "nav_tint"
  )

  Column(
    modifier = Modifier
      .testTag(testTag)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = 24.dp),
        onClick = onClick
      )
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tintColor,
      modifier = Modifier.size(24.dp)
    )
    Text(
      text = label,
      color = tintColor,
      fontSize = 9.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      letterSpacing = 0.5.sp,
      modifier = Modifier.padding(top = 2.dp)
    )
  }
}
