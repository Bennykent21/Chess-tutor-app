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
import androidx.compose.ui.unit.em
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
    val switchOff = Color(palette.switchOff)
    val outlinedCards = palette.outlinedCards

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
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = LocalCtColors.current.switchOff

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

/** Hairline-outlined cards (dark themes, Mono) or softly shadowed ones (other light themes). */
val CtOutlinedCards: Boolean @Composable @ReadOnlyComposable get() = LocalCtColors.current.outlinedCards

object CtSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Side margin of every screen (design language section 4). */
    val gutter = 20.dp

    /** Minimum touch target (accessibility). */
    val touchTarget = 48.dp
}

/** Corner radii (design language section 4). The playing board itself has square corners. */
object CtShapes {
    /** Hero cards and tiles. */
    val Card = RoundedCornerShape(20.dp)
    /** List cards, mode cards, theme cards. */
    val CardSmall = RoundedCornerShape(16.dp)
    /** Primary buttons. */
    val Button = RoundedCornerShape(16.dp)
    /** Secondary buttons, text fields, the segmented control. */
    val Control = RoundedCornerShape(14.dp)
    /** Small inner parts: a selected segment, board thumbnails. */
    val Chip = RoundedCornerShape(10.dp)
    /** Sheets rise from the bottom with rounded top corners. */
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    /** Chips, badges and round buttons. */
    val Pill = RoundedCornerShape(percent = 50)
}

/**
 * Headings, titles and big numbers (Schibsted Grotesk SemiBold, SIL Open Font License, bundled;
 * decision D38). Only 600 is bundled: a heavier request uses it as is, never a faked bold.
 */
val GroteskFontFamily = FontFamily(
    Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
)

/** Tabular figures, so ratings, clocks and counts do not shift as digits change. */
private const val TABULAR = "tnum"

private fun grotesk(size: Int, line: Int, color: Color) = TextStyle(
    fontFamily = GroteskFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = TABULAR,
    color = color,
)

private fun inter(weight: FontWeight, size: Int, line: Int, color: Color) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = TABULAR,
    color = color,
)

/** The type scale (`docs/design-language.md` section 3) in the current theme's text colours. */
@Immutable
class CtTypeScale(colors: CtColorScheme) {
    val display = grotesk(30, 36, colors.text)
    val headline = grotesk(22, 27, colors.text)
    val topBar = grotesk(20, 26, colors.text)
    val title = grotesk(19, 24, colors.text)
    val titleSmall = inter(FontWeight.SemiBold, 16, 22, colors.text)
    val body = inter(FontWeight.Normal, 16, 23, colors.text2)
    val bodySmall = inter(FontWeight.Normal, 14, 20, colors.text2)
    val chat = inter(FontWeight.Normal, 15, 21, colors.text)
    val label = inter(FontWeight.SemiBold, 14, 18, colors.text)
    val caption = inter(FontWeight.Normal, 13, 18, colors.text2)
    val navLabel = inter(FontWeight.Medium, 12, 16, colors.text2)
    val number = grotesk(22, 26, colors.text)
    val bigNumber = grotesk(48, 52, colors.text)
    val button = inter(FontWeight.SemiBold, 16, 20, colors.onPrimary)
}

val LocalCtType = staticCompositionLocalOf { CtTypeScale(CtColorScheme(Palette.PARCHMENT_LIGHT)) }

/**
 * The type scale. Every screen uses these styles instead of raw font sizes. Grotesk for
 * headings and numbers, Inter for everything else.
 */
object CtType {
    /** Screen title, 30 sp. */
    val Display: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.display
    /** Card and sheet title, 22 sp. */
    val Headline: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.headline
    /** Top bar title, 20 sp. */
    val TopBar: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.topBar
    /** Section title, 19 sp. */
    val Title: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.title
    val TitleSmall: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.titleSmall
    val Body: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.body
    val BodySmall: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.bodySmall
    /** What a coach or a bot says in a speech bubble, 15 sp: a little smaller than body so more fits. */
    val Chat: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.chat
    val Label: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.label
    val Caption: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.caption
    /** Bottom navigation labels, 12 sp. */
    val NavLabel: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.navLabel
    val Number: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.number
    /** The one big figure of a screen (a rating, a score), 48 sp. */
    val BigNumber: TextStyle @Composable @ReadOnlyComposable get() = LocalCtType.current.bigNumber
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
            ProvideTextStyle(TextStyle(fontFamily = InterFontFamily, fontFeatureSettings = TABULAR, color = colors.text), content)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
