package com.chesstutor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

private val DarkColorScheme = darkColorScheme(
    primary = ChessTutorColors.Primary,
    onPrimary = ChessTutorColors.Background,
    secondary = ChessTutorColors.Secondary,
    background = ChessTutorColors.Background,
    surface = ChessTutorColors.Surface,
    onBackground = ChessTutorColors.TextPrimary,
    onSurface = ChessTutorColors.TextPrimary,
    outline = ChessTutorColors.Border
)

@Composable
fun ChessTutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ChessTutorTypography
    ) {
        ProvideTextStyle(
            value = TextStyle(
                fontFamily = InterFontFamily,
                fontFeatureSettings = "tnum",
                color = ChessTutorColors.TextPrimary
            ),
            content = content
        )
    }
}
