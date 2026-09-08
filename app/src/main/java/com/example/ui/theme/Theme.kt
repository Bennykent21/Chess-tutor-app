package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val FrostedGlassColorScheme =
  darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBackground,
    primaryContainer = CyanDark,
    onPrimaryContainer = TextSlate100,
    secondary = AccentIndigo,
    onSecondary = DarkBackground,
    tertiary = AccentEmerald,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextSlate100,
    surface = DarkSurface,
    onSurface = TextSlate100,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSlate300,
    outline = GlassBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = FrostedGlassColorScheme,
    typography = Typography,
    content = content
  )
}
