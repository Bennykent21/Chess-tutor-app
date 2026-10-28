package com.chesstutor.app.design

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The app's colour themes (decisions D36, D37). They change colours only; the interface is shared. */
enum class AppTheme(val label: String) {
    PARCHMENT("Parchment"),
    BAIZE("Baize"),
    MONO("Mono"),
    SLATE("Slate"),
    ;

    companion object {
        val DEFAULT = PARCHMENT

        fun fromName(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Light or dark. [SYSTEM] follows the phone's setting (the default). */
enum class Appearance(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromName(name: String?): Appearance = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Every colour role of one theme in one mode, as plain ARGB values so they can be tested on
 * the JVM. Compose code reads them through [CtColors]. Values come from
 * `docs/design-language.md` section 2.
 */
data class CtPalette(
    val theme: AppTheme,
    val dark: Boolean,
    val bg: Long,
    /** Tiles, chips, list rows and secondary buttons. */
    val surface: Long,
    /** Hero cards, sheets and bubbles. */
    val raised: Long,
    val line: Long,
    /** Progress tracks and switches that are off. Text and text2 may sit on it. */
    val track: Long,
    val text: Long,
    val text2: Long,
    /** Icons, progress, selection and the odd accent word. */
    val accent: Long,
    val onAccent: Long,
    /** The main button of a screen. */
    val primary: Long,
    val onPrimary: Long,
    val navPill: Long,
    val navIcon: Long,
    val success: Long,
    val onSuccess: Long,
    val error: Long,
    val onError: Long,
    /** A switch's track when it is off (the white knob marks it). */
    val switchOff: Long,
    /** The board this theme suggests (D38: the player's own board choice always wins). */
    val boardLight: Long,
    val boardDark: Long,
) {
    /** Dark themes and Mono outline their cards with a hairline; the other light themes use a soft shadow. */
    val outlinedCards: Boolean get() = dark || theme == AppTheme.MONO

    /** Text colour and the backgrounds it is allowed on. Tested to meet WCAG AA (4.5:1). */
    val textPairs: List<Pair<Long, List<Long>>>
        get() = listOf(
            text to listOf(bg, surface, raised, track),
            text2 to listOf(bg, surface, raised, track),
            accent to listOf(bg, surface, raised),
            success to listOf(bg, surface, raised),
            error to listOf(bg, surface, raised),
            onPrimary to listOf(primary),
            onAccent to listOf(accent),
            onSuccess to listOf(success),
            onError to listOf(error),
            navIcon to listOf(navPill),
            // Selected chips draw their label in the background colour on a full accent fill,
            // and the eval bar labels White's share (drawn in text) in the background colour.
            bg to listOf(text, accent, success, error),
        )
}

object Palette {
    // Light themes darken accent, success and error a little from the mock-up values so they
    // pass 4.5:1 as text (Parchment accent was #8A6A3A, success #3E7A54, error #B4473A).
    val PARCHMENT_LIGHT = CtPalette(
        theme = AppTheme.PARCHMENT, dark = false,
        bg = 0xFFF4EFE6, surface = 0xFFEBE4D7, raised = 0xFFFFFDF8, line = 0xFFE2D9C9, track = 0xFFE8E0D1,
        text = 0xFF1C1814, text2 = 0xFF665D52,
        accent = 0xFF7D6035, onAccent = 0xFFFFFDF8,
        primary = 0xFF1C1814, onPrimary = 0xFFF7F2E9,
        navPill = 0xFFE6DCC9, navIcon = 0xFF1C1814,
        success = 0xFF39704D, onSuccess = 0xFFFFFDF8,
        error = 0xFFAA4337, onError = 0xFFFFFDF8,
        switchOff = 0xFFD9CFBD, boardLight = 0xFFECDDC2, boardDark = 0xFFB8956A,
    )
    val PARCHMENT_DARK = CtPalette(
        theme = AppTheme.PARCHMENT, dark = true,
        bg = 0xFF13110E, surface = 0xFF1E1B16, raised = 0xFF211D18, line = 0xFF2E2922, track = 0xFF38322A,
        text = 0xFFF2ECE1, text2 = 0xFFADA392,
        accent = 0xFFCFA96C, onAccent = 0xFF13110E,
        primary = 0xFFCFA96C, onPrimary = 0xFF1A140C,
        navPill = 0xFF2F281C, navIcon = 0xFFF2ECE1,
        success = 0xFF7DBE93, onSuccess = 0xFF13110E,
        error = 0xFFE08374, onError = 0xFF13110E,
        switchOff = 0xFF4A4236, boardLight = 0xFFD4BD98, boardDark = 0xFF8F6E48,
    )
    val BAIZE_LIGHT = CtPalette(
        theme = AppTheme.BAIZE, dark = false,
        bg = 0xFFF3F5F2, surface = 0xFFE8EDE9, raised = 0xFFFFFFFF, line = 0xFFDCE2DD, track = 0xFFE2E8E3,
        text = 0xFF101613, text2 = 0xFF4C5751,
        accent = 0xFF2B6A4D, onAccent = 0xFFFFFFFF,
        primary = 0xFF1F5A40, onPrimary = 0xFFFFFFFF,
        navPill = 0xFFD6E7DC, navIcon = 0xFF143A2A,
        success = 0xFF2B6A4D, onSuccess = 0xFFFFFFFF,
        error = 0xFFB24639, onError = 0xFFFFFFFF,
        switchOff = 0xFFC4CFC7, boardLight = 0xFFE6EBDD, boardDark = 0xFF7C9D84,
    )
    val BAIZE_DARK = CtPalette(
        theme = AppTheme.BAIZE, dark = true,
        bg = 0xFF0D1210, surface = 0xFF161D19, raised = 0xFF19211D, line = 0xFF25302A, track = 0xFF2B3731,
        text = 0xFFE6EEE9, text2 = 0xFF9DACA4,
        accent = 0xFF7CC19B, onAccent = 0xFF0D1210,
        primary = 0xFF7CC19B, onPrimary = 0xFF0A1610,
        navPill = 0xFF1F3328, navIcon = 0xFFC5E6D1,
        success = 0xFF7CC19B, onSuccess = 0xFF0D1210,
        error = 0xFFE08374, onError = 0xFF0D1210,
        switchOff = 0xFF3A4740, boardLight = 0xFFCBD5C2, boardDark = 0xFF60846A,
    )
    val MONO_LIGHT = CtPalette(
        theme = AppTheme.MONO, dark = false,
        bg = 0xFFFFFFFF, surface = 0xFFF3F3F3, raised = 0xFFFFFFFF, line = 0xFFE7E7E7, track = 0xFFE7E7E7,
        text = 0xFF0A0A0A, text2 = 0xFF5F5F5F,
        accent = 0xFF0A0A0A, onAccent = 0xFFFFFFFF,
        primary = 0xFF0A0A0A, onPrimary = 0xFFFFFFFF,
        navPill = 0xFFEDEDED, navIcon = 0xFF0A0A0A,
        success = 0xFF1F7A4A, onSuccess = 0xFFFFFFFF,
        error = 0xFFC0392B, onError = 0xFFFFFFFF,
        switchOff = 0xFFCFCFCF, boardLight = 0xFFEAD3AF, boardDark = 0xFFB3825A,
    )
    val MONO_DARK = CtPalette(
        theme = AppTheme.MONO, dark = true,
        bg = 0xFF000000, surface = 0xFF121212, raised = 0xFF141414, line = 0xFF242424, track = 0xFF2A2A2A,
        text = 0xFFF5F5F5, text2 = 0xFFA0A0A0,
        accent = 0xFFF5F5F5, onAccent = 0xFF000000,
        primary = 0xFFF5F5F5, onPrimary = 0xFF000000,
        navPill = 0xFF262626, navIcon = 0xFFF5F5F5,
        success = 0xFF6CC08E, onSuccess = 0xFF000000,
        error = 0xFFEC7A6A, onError = 0xFF000000,
        switchOff = 0xFF3A3A3A, boardLight = 0xFFD9BF97, boardDark = 0xFF9B6D47,
    )
    // Slate takes the colours of the developer's own mock-up (dark); its light mode is derived.
    val SLATE_LIGHT = CtPalette(
        theme = AppTheme.SLATE, dark = false,
        bg = 0xFFF3F5F7, surface = 0xFFE6EAEE, raised = 0xFFFFFFFF, line = 0xFFD9DFE5, track = 0xFFDFE4E9,
        text = 0xFF0E1216, text2 = 0xFF4E5A66,
        accent = 0xFF8C5F1E, onAccent = 0xFFFFFFFF,
        primary = 0xFFD4A24C, onPrimary = 0xFF161009,
        navPill = 0xFFF1E2C2, navIcon = 0xFF5C3F10,
        success = 0xFF2D744A, onSuccess = 0xFFFFFFFF,
        error = 0xFFB04539, onError = 0xFFFFFFFF,
        switchOff = 0xFFC3CCD5, boardLight = 0xFFE8E0D1, boardDark = 0xFF58707E,
    )
    val SLATE_DARK = CtPalette(
        theme = AppTheme.SLATE, dark = true,
        bg = 0xFF0E1216, surface = 0xFF161C22, raised = 0xFF1E262E, line = 0xFF28323C, track = 0xFF26303A,
        text = 0xFFEDF1F4, text2 = 0xFF94A1AE,
        accent = 0xFFD4A24C, onAccent = 0xFF0E1216,
        primary = 0xFFD4A24C, onPrimary = 0xFF161009,
        navPill = 0xFF2C2618, navIcon = 0xFFE2B566,
        success = 0xFF74B78E, onSuccess = 0xFF0E1216,
        error = 0xFFDC7466, onError = 0xFF0E1216,
        switchOff = 0xFF3A4652, boardLight = 0xFFE8E0D1, boardDark = 0xFF58707E,
    )

    val ALL: List<CtPalette> = listOf(
        PARCHMENT_LIGHT, PARCHMENT_DARK, BAIZE_LIGHT, BAIZE_DARK,
        MONO_LIGHT, MONO_DARK, SLATE_LIGHT, SLATE_DARK,
    )

    fun of(theme: AppTheme, dark: Boolean): CtPalette = ALL.first { it.theme == theme && it.dark == dark }

    /** WCAG 2.x contrast ratio between two opaque ARGB colours (1.0 to 21.0). */
    fun contrastRatio(a: Long, b: Long): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
