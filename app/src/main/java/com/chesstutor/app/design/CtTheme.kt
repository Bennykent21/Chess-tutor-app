package com.chesstutor.app.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chesstutor.app.R
import com.chesstutor.app.ui.theme.ChessTutorTypography
import com.chesstutor.app.ui.theme.InterFontFamily

/** The colour roles of the current theme and mode, as Compose colours. Values live in [Palette]. */
@Immutable
class CtColorScheme(val palette: CtPalette) {
    val isDark = palette.dark
    val bg = Color(palette.bg)
    val surface = Color(palette.surface)
    val raised = Color(palette.raised)
    val line = Color(palette.line)
    val track = Color(palette.track)
    val text = Color(palette.text)
    val text2 = Color(palette.text2)
    val accent = Color(palette.accent)
    val onAccent = Color(palette.onAccent)
    val primary = Color(palette.primary)
    val onPrimary = Color(palette.onPrimary)
    val navPill = Color(palette.navPill)
    val navIcon = Color(palette.navIcon)
    val success = Color(palette.success)
    val onSuccess = Color(palette.onSuccess)
    val error = Color(palette.error)
    val onError = Color(palette.onError)

    /** The same roles for Material components (dialogs, menus, switches, text fields). */
    val material: ColorScheme = if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            secondary = accent, onSecondary = onAccent,
            tertiary = accent, onTertiary = onAccent,
            background = bg, onBackground = text,
            surface = bg, onSurface = text,
            surfaceVariant = surface, onSurfaceVariant = text2,
            surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = raised,
            surfaceContainerHigh = raised, surfaceContainerHighest = track,
            inverseSurface = text, inverseOnSurface = bg,
            outline = line, outlineVariant = line,
            error = error, onError = onError,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            secondary = accent, onSecondary = onAccent,
            tertiary = accent, onTertiary = onAccent,
            background = bg, onBackground = text,
            surface = bg, onSurface = text,
            surfaceVariant = surface, onSurfaceVariant = text2,
            surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = raised,
            surfaceContainerHigh = raised, surfaceContainerHighest = track,
            inverseSurface = text, inverseOnSurface = bg,
            outline = line, outlineVariant = line,
            error = error, onError = onError,
        )
    }
}

/** The current theme's colours. [CtTheme] provides it; the default is Parchment light. */
val LocalCtColors = staticCompositionLocalOf { CtColorScheme(Palette.PARCHMENT_LIGHT) }

/**
 * Colour roles of the current theme (decisions D36, D37). Screens use these, never raw colours.
 *
 * The second group are the names of the old dark-only design system, kept so existing screens
 * build while Phase 7D redesigns them; each maps onto one role. New code uses the roles.
 */
object CtColors {
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalCtColors.current.isDark
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.raised
    val Line: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.line
    val Track: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.track
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.text
    val Text2: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.text2
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.onAccent
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.primary
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.onPrimary
    val NavPill: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.navPill
    val NavIcon: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.navIcon
    val Success: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.success
    val OnSuccess: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.onSuccess
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.error
    val OnError: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.onError

    // Old names (transition only).
    val BgDeep: Color @Composable @ReadOnlyComposable get() = Bg
    val BgIndigo: Color @Composable @ReadOnlyComposable get() = Bg
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = Raised
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = Track
    val CardTeal: Color @Composable @ReadOnlyComposable get() = Raised
    val CardViolet: Color @Composable @ReadOnlyComposable get() = Raised
    val Text3: Color @Composable @ReadOnlyComposable get() = Text2
    val Gold: Color @Composable @ReadOnlyComposable get() = Accent
    val GoldDeep: Color @Composable @ReadOnlyComposable get() = Accent
    val OnGold: Color @Composable @ReadOnlyComposable get() = OnAccent
    val Teal: Color @Composable @ReadOnlyComposable get() = Accent
    val TealDeep: Color @Composable @ReadOnlyComposable get() = Accent
    val OnTeal: Color @Composable @ReadOnlyComposable get() = OnAccent
    val Violet: Color @Composable @ReadOnlyComposable get() = Accent
    val VioletDeep: Color @Composable @ReadOnlyComposable get() = Accent
    val OnViolet: Color @Composable @ReadOnlyComposable get() = OnAccent
    val Sky: Color @Composable @ReadOnlyComposable get() = Accent
    val OnSky: Color @Composable @ReadOnlyComposable get() = OnAccent
    val Lime: Color @Composable @ReadOnlyComposable get() = Success
    val OnLime: Color @Composable @ReadOnlyComposable get() = OnSuccess
    val Coral: Color @Composable @ReadOnlyComposable get() = Error
    val OnCoral: Color @Composable @ReadOnlyComposable get() = OnError
}

/** Former gradients. The new design has no gradients (D36), so each is now one flat role colour. */
object CtGradients {
    val Screen: Brush @Composable @ReadOnlyComposable get() = SolidColor(CtColors.Bg)
    val Gold: Brush @Composable @ReadOnlyComposable get() = SolidColor(CtColors.Accent)
    val Teal: Brush @Composable @ReadOnlyComposable get() = SolidColor(CtColors.Raised)
    val Violet: Brush @Composable @ReadOnlyComposable get() = SolidColor(CtColors.Raised)
    val Card: Brush @Composable @ReadOnlyComposable get() = SolidColor(CtColors.Raised)
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

/** The type scale in the current theme's text colours. */
@Immutable
class CtTypeScale(colors: CtColorScheme) {
    val display = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp, color = colors.text)
    val headline = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, color = colors.text)
    val title = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp, color = colors.text)
    val titleSmall = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = colors.text)
    val body = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, color = colors.text2)
    val bodySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = colors.text2)
    val label = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = colors.text)
    val caption = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = colors.text2)
    val number = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp, color = colors.text)
    val button = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp)
}

val LocalCtType = staticCompositionLocalOf { CtTypeScale(CtColorScheme(Palette.PARCHMENT_LIGHT)) }

/**
 * The type scale. Every new screen uses these styles instead of raw font sizes, which is
 * what fixes the 205 hard-coded sizes in the old screens (plan section 5.1).
 */
object CtType {
    val Display: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.display
    val Headline: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.headline
    val Title: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.title
    val TitleSmall: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.titleSmall
    val Body: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.body
    val BodySmall: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.bodySmall
    val Label: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.label
    val Caption: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.caption
    val Number: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.number
    val Button: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.button
}

/** True when the player turned on "Reduce motion": animations snap instead of moving. */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * The app's theme: colour roles for [theme] in light or dark ([appearance] follows the phone
 * by default), the type scale, Material components in the same colours, and status bar icons
 * that stay readable on the background.
 */
@Composable
fun CtTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    appearance: Appearance = Appearance.DEFAULT,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors = remember(theme, dark) { CtColorScheme(Palette.of(theme, dark)) }
    val type = remember(colors) { CtTypeScale(colors) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = colors.material, typography = ChessTutorTypography) {
        CompositionLocalProvider(
            LocalCtColors provides colors,
            LocalCtType provides type,
            LocalReduceMotion provides reduceMotion,
            LocalContentColor provides colors.text,
        ) {
            ProvideTextStyle(TextStyle(fontFamily = InterFontFamily, fontFeatureSettings = "tnum", color = colors.text), content)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
