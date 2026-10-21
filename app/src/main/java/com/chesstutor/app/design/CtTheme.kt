package com.chesstutor.app.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chesstutor.app.R
import com.chesstutor.app.ui.theme.InterFontFamily

/** Compose colours for design system v2. Values and contrast rules live in [Palette]. */
object CtColors {
    val BgDeep = Color(Palette.BG_DEEP)
    val Bg = Color(Palette.BG)
    val BgIndigo = Color(Palette.BG_INDIGO)
    val Surface = Color(Palette.SURFACE)
    val SurfaceRaised = Color(Palette.SURFACE_RAISED)
    val SurfaceHigh = Color(Palette.SURFACE_HIGH)
    val Line = Color(Palette.LINE)
    val CardTeal = Color(Palette.CARD_TEAL)
    val CardViolet = Color(Palette.CARD_VIOLET)

    val Text = Color(Palette.TEXT)
    val Text2 = Color(Palette.TEXT_2)
    val Text3 = Color(Palette.TEXT_3)

    val Gold = Color(Palette.GOLD)
    val GoldDeep = Color(Palette.GOLD_DEEP)
    val OnGold = Color(Palette.ON_GOLD)
    val Teal = Color(Palette.TEAL)
    val TealDeep = Color(Palette.TEAL_DEEP)
    val OnTeal = Color(Palette.ON_TEAL)
    val Lime = Color(Palette.LIME)
    val OnLime = Color(Palette.ON_LIME)
    val Violet = Color(Palette.VIOLET)
    val VioletDeep = Color(Palette.VIOLET_DEEP)
    val Coral = Color(Palette.CORAL)
    val OnCoral = Color(Palette.ON_CORAL)
}

object CtGradients {
    val Screen: Brush = Brush.verticalGradient(listOf(CtColors.BgDeep, CtColors.Bg, CtColors.BgIndigo))
    val Gold: Brush = Brush.horizontalGradient(listOf(CtColors.Gold, CtColors.GoldDeep))
    /** Gradient cards. Only Text, Text2 and accent colours go on them (see Palette.TEXT_PAIRS). */
    val Teal: Brush = Brush.linearGradient(listOf(CtColors.CardTeal, CtColors.SurfaceRaised))
    val Violet: Brush = Brush.linearGradient(listOf(CtColors.CardViolet, CtColors.SurfaceRaised))
    val Card: Brush = Brush.verticalGradient(listOf(CtColors.SurfaceRaised, CtColors.Surface))
}

object CtSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Side margin of every screen. */
    val gutter = 16.dp

    /** Minimum touch target (accessibility). */
    val touchTarget = 48.dp
}

object CtShapes {
    val Card = RoundedCornerShape(22.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Rounded display face for headings (Nunito, SIL Open Font License, bundled). */
val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/**
 * The type scale. Every new screen uses these styles instead of raw font sizes, which is
 * what fixes the 205 hard-coded sizes in the old screens (plan section 5.1).
 */
object CtType {
    val Display = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp, color = CtColors.Text)
    val Headline = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, color = CtColors.Text)
    val Title = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp, color = CtColors.Text)
    val TitleSmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = CtColors.Text)
    val Body = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, color = CtColors.Text2)
    val BodySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = CtColors.Text2)
    val Label = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = CtColors.Text)
    val Caption = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = CtColors.Text3)
    val Number = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp, color = CtColors.Text)
    val Button = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp)
}

/** True when the player turned on "Reduce motion": animations snap instead of moving. */
val LocalReduceMotion = compositionLocalOf { false }

/** Provides design-system settings to everything inside it. */
@Composable
fun CtTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
}
