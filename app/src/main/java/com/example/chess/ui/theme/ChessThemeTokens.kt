package com.example.chess.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Modern Studio Theme — Warm Slate & Clean Neutral
// ==========================================

// Clean, rich canvas without dirty brown/yellow muddy gradient
val CanvasBackground = Color(0xFF0F1115) // Deep Slate-Charcoal
val CanvasCard = Color(0xFF181B20)       // Clean Solid Surface
val CanvasCardElevated = Color(0xFF21252C) // Elevated Interactive Card
val CanvasCardBorder = Color(0xFF2D323B)   // Crisp subtle border
val CanvasCardBorderActive = Color(0xFFEAB308) // Crisp Gold Accent

// Mentorship / Coach Accent Palette (Clean Golden Amber, no sludge)
val CoachPrimary = Color(0xFFF59E0B)       // Warm Amber Gold
val CoachPrimaryHover = Color(0xFFD97706)  // Deeper Gold
val CoachAccentGold = Color(0xFFFBBF24)    // Bright Gold
val CoachBadgeBg = Color(0x29F59E0B)       // Translucent Pill Tag

// ==========================================
// Tournament Classical Chessboard Colors
// Warm Sandstone & Forest Walnut (FIDE / Classical Master styling)
// ==========================================
val BoardLightSquare = Color(0xFFEADBBE)   // Warm Sandstone / Ivory (High contrast with white pieces)
val BoardDarkSquare = Color(0xFF6F8F72)    // Forest Olive-Green / Tournament Sage
val BoardHighlightSquare = Color(0x66F59E0B) // Golden Selection Glow
val BoardCheckSquare = Color(0x80DC2626)   // Clear Red Check indicator
val BoardLastMoveSquare = Color(0x4DFBBF24) // Soft Gold Last Move tint

// ==========================================
// High Contrast Piece Tokens
// ==========================================
val PieceWhiteBody = Color(0xFFFFFFFF)       // Pure Crisp White
val PieceWhiteBorder = Color(0xFF1E293B)     // Dark Slate Outline for 100% visibility on light squares
val PieceBlackBody = Color(0xFF1E2128)       // Deep Obsidian Black
val PieceBlackBorder = Color(0xFF94A3B8)     // Soft Silver Rim for clear silhouette on dark squares

// ==========================================
// Move Evaluation Semantics
// ==========================================
val StatusBestMove = Color(0xFF06B6D4)      // Crisp Cyan
val StatusExcellent = Color(0xFF10B981)     // Crisp Emerald
val StatusInaccuracy = Color(0xFFF59E0B)    // Crisp Amber
val StatusMistake = Color(0xFFF97316)       // Clean Orange
val StatusBlunder = Color(0xFFEF4444)       // Clean Crimson

// ==========================================
// Typography & Readability
// ==========================================
val TextTitle = Color(0xFFF8FAFC)           // 98% Light
val TextBody = Color(0xFFCBD5E1)            // 85% Light Slate
val TextMuted = Color(0xFF8B95A5)           // 60% Muted Slate
val TextSubtle = Color(0xFF64748B)          // 40% Secondary Slate
