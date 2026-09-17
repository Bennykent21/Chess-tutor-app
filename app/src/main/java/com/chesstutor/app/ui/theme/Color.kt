package com.chesstutor.app.ui.theme

import androidx.compose.ui.graphics.Color

object ChessTutorColors {
    // Redesign Specification Dark Palette (§1.2)
    val Background = Color(0xFF0F1115)       // near-black, slightly cool
    val Surface = Color(0xFF1A1D23)          // cards
    val SurfaceElevated = Color(0xFF232730)  // nested cards, sheets
    val SurfaceHighlight = Color(0xFF2C313A)
    val Border = Color(0xFF2C313A)           // 1dp, subtle

    // Typography
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFF9BA1AB)
    val TextTertiary = Color(0xFF6B7280)     // metadata only
    val TextMuted = Color(0xFF6B7280)

    // Primary Action Accent (§1.2: Amber)
    val Accent = Color(0xFFE0A03C)           // amber — primary actions, active nav
    val AccentMuted = Color(0xFF8A6427)      // pressed/disabled accent states
    val Primary = Accent
    val PrimaryPressed = AccentMuted
    val Secondary = Accent

    // Semantic colors — strictly for move quality only (§1.2)
    val Success = Color(0xFF4ADE80)          // good / positive chess evaluation
    val Warning = Color(0xFFFBBF24)          // inaccuracy
    val Inaccuracy = Color(0xFFFBBF24)
    val Mistake = Color(0xFFFFA459)          // mistake
    val Error = Color(0xFFF87171)            // blunder
    val Blunder = Color(0xFFF87171)
    val Brilliant = Color(0xFF38BDF8)        // brilliant cyan
    val Great = Color(0xFF60A5FA)            // great blue
    val Best = Color(0xFF4ADE80)             // best move green

    // Board Square Colors (refined slate & warm walnut)
    val BoardLight = Color(0xFFE2D6B5)
    val BoardDark = Color(0xFFB88B4A)
    val BoardHighlight = Color(0x99E0A03C)
    val BoardSelected = Color(0xCCE0A03C)
    val BoardArrow = Color(0xEEFBBF24)
}

