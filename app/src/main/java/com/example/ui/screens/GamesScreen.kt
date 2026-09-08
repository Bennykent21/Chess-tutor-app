package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.FrostedGlassBox
import com.example.ui.model.GameProfile
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextSlate100
import com.example.ui.theme.TextSlate300
import com.example.ui.theme.TextSlate400

@Composable
fun GamesScreen(
  games: List<GameProfile>,
  onLaunchGame: (GameProfile) -> Unit,
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
      Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
          text = "GAME LIBRARY",
          color = TextSlate400,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp
        )
        Text(
          text = "Optimized Profiles",
          color = TextSlate100,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }

    items(games, key = { it.id }) { game ->
      GameLibraryCard(
        game = game,
        onLaunch = { onLaunchGame(game) }
      )
    }

    item {
      // Add custom game action card
      FrostedGlassBox(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { /* no-op demonstration */ },
        shape = RoundedCornerShape(24.dp),
        backgroundColor = GlassSurface.copy(alpha = 0.05f),
        borderColor = GlassBorder.copy(alpha = 0.1f)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Game",
            tint = CyanPrimary,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = "Add Another Game to Boost Library",
            color = CyanPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun GameLibraryCard(
  game: GameProfile,
  onLaunch: () -> Unit
) {
  FrostedGlassBox(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("game_item_${game.id}"),
    shape = RoundedCornerShape(26.dp),
    backgroundColor = GlassSurface,
    borderColor = if (game.isSelected) GlassBorderHighlight else GlassBorder
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
              brush = Brush.linearGradient(
                colors = listOf(AccentIndigo, Color(0xFF2563EB))
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SportsSoccer,
            contentDescription = game.name,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = game.name,
            color = TextSlate100,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = game.graphicsPreset,
            color = TextSlate400,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
          )
        }

        if (game.isSelected) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .background(CyanPrimary),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Active Profile",
              tint = DarkBackground,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = null,
              tint = CyanPrimary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "${game.targetFps} FPS Cap",
              color = TextSlate300,
              fontSize = 12.sp
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.NetworkPing,
              contentDescription = null,
              tint = CyanPrimary,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "${game.pingMs} ms Low Latency",
              color = TextSlate300,
              fontSize = 12.sp
            )
          }
        }

        Button(
          onClick = onLaunch,
          colors = ButtonDefaults.buttonColors(
            containerColor = CyanPrimary,
            contentColor = DarkBackground
          ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(34.dp)
        ) {
          Text(
            text = "PLAY",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          )
        }
      }
    }
  }
}
