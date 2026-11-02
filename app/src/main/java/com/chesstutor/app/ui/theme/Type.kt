package com.chesstutor.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chesstutor.app.R
import com.chesstutor.app.design.GroteskFontFamily

/** Body and interface text (Inter 4.001, SIL Open Font License, bundled as real 400, 500 and 600 weights). */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private fun heading(size: Int, line: Int) = TextStyle(
    fontFamily = GroteskFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.02).em,
)

private fun text(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = "tnum",
)

/**
 * The same type scale for Material components (dialogs, menus, text fields), so they match
 * `CtType`. Colours come from the theme, never from here.
 */
val ChessTutorTypography = Typography(
    displayLarge = heading(30, 36),
    displayMedium = heading(30, 36),
    displaySmall = heading(30, 36),
    headlineLarge = heading(22, 27),
    headlineMedium = heading(22, 27),
    headlineSmall = heading(22, 27),
    titleLarge = heading(20, 26),
    titleMedium = text(FontWeight.SemiBold, 16, 22),
    titleSmall = text(FontWeight.Medium, 14, 20),
    bodyLarge = text(FontWeight.Normal, 16, 23),
    bodyMedium = text(FontWeight.Normal, 14, 20),
    bodySmall = text(FontWeight.Normal, 13, 18),
    labelLarge = text(FontWeight.SemiBold, 14, 18),
    labelMedium = text(FontWeight.Medium, 13, 18),
    labelSmall = text(FontWeight.Medium, 12, 16),
)
