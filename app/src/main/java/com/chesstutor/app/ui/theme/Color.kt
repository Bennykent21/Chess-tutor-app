package com.chesstutor.app.ui.theme

import androidx.compose.ui.graphics.Color

object ChessTutorColors {
    // Design Mockup Tokens (:root)
    val Background = Color(0xFF0E1216)       // --bg: #0E1216
    val Surface = Color(0xFF161C22)          // --surface: #161C22
    val Surface2 = Color(0xFF1E262E)         // --surface-2: #1E262E
    val Surface3 = Color(0xFF26303A)         // --surface-3: #26303A
    val SurfaceElevated = Surface2
    val Line = Color(0xFF28323C)             // --line: #28323C
    val LineSoft = Color(0xFF1E262E)         // --line-soft: #1E262E
    val Border = Line

    // Typography Tokens
    val TextPrimary = Color(0xFFEDF1F4)      // --text: #EDF1F4
    val TextSecondary = Color(0xFF94A1AE)    // --text-2: #94A1AE
    val TextTertiary = Color(0xFF606D79)     // --text-3: #606D79
    val TextMuted = TextTertiary

    // Brass / Accent Tokens
    val Brass = Color(0xFFD4A24C)            // --brass: #D4A24C
    val BrassDim = Color(0xFF8C6B32)         // --brass-dim: #8C6B32
    val BrassInk = Color(0xFF161009)         // --brass-ink: #161009
    val Accent = Brass
    val Primary = Brass

    // Semantic Accents
    val Sage = Color(0xFF74B78E)             // --sage: #74B78E (success / best)
    val Coral = Color(0xFFDC7466)            // --coral: #DC7466 (mistake / blunder)
    val Amber = Color(0xFFE0A04A)            // --amberq: #E0A04A
    val Secondary = Sage
    val Brilliant = Brass
    val Great = Sage

    // Backward-compatibility aliases
    val Best = Sage
    val Success = Sage
    val Inaccuracy = Amber
    val Mistake = Coral
    val Blunder = Coral
    val Warning = Amber

    // Board Tokens
    val SqLight = Color(0xFFE8E0D1)          // --sq-light: #E8E0D1
    val SqDark = Color(0xFF58707E)           // --sq-dark: #58707E
    val BoardLight = SqLight
    val BoardDark = SqDark

    val PcWhite = Color(0xFFF7F3EA)          // --pc-white: #F7F3EA
    val PcWhiteInk = Color(0xFF2A3138)       // --pc-white-ink: #2A3138
    val PcBlack = Color(0xFF232B33)          // --pc-black: #232B33
    val PcBlackInk = Color(0xFF0C1014)       // --pc-black-ink: #0C1014

    // Board Interaction Overlays
    val SquareLastMove = Color(0x42D4A24C)   // brass with 26% opacity
    val SquareSelected = Color(0x6BD4A24C)   // brass with 42% opacity
    val SquareBad = Color(0x57DC7466)        // coral with 34% opacity
    val TargetDot = Color(0x4D121A20)
    val TargetCaptureRing = Color(0x4D121A20)
}
