package com.chesstutor.app.design

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Design system v2 colours (plan section 5.1), as plain ARGB values so they can be tested
 * on the JVM. Compose code reads them through [CtColors].
 *
 * Direction: deep navy to indigo base, sunrise gold for primary actions and rewards,
 * electric teal for learning and progress, lime and violet for success and XP, coral for
 * mistakes. All original; no colours copied from other apps.
 */
object Palette {
    // Base, darkest to lightest.
    const val BG_DEEP = 0xFF0B1026L
    const val BG = 0xFF121838L
    const val BG_INDIGO = 0xFF1E1D52L
    const val SURFACE = 0xFF1B2250L
    const val SURFACE_RAISED = 0xFF252E66L
    /** Pressed states, chips and tracks. Only primary and secondary text may sit on it. */
    const val SURFACE_HIGH = 0xFF303A7AL
    const val LINE = 0xFF3A4688L
    /** Dark accent tints for gradient cards (the dark end of their gradient). */
    const val CARD_TEAL = 0xFF0B4652L
    const val CARD_VIOLET = 0xFF3A2A8CL

    // Text.
    const val TEXT = 0xFFF4F6FFL
    const val TEXT_2 = 0xFFBAC2EAL
    const val TEXT_3 = 0xFF949DD0L

    // Accents.
    const val GOLD = 0xFFFFC53DL
    const val GOLD_DEEP = 0xFFF29A1FL
    const val ON_GOLD = 0xFF2A1B00L
    const val TEAL = 0xFF2EE6D6L
    const val TEAL_DEEP = 0xFF119E9AL
    const val ON_TEAL = 0xFF00211EL
    const val LIME = 0xFFA8E63AL
    const val ON_LIME = 0xFF1B2A00L
    const val VIOLET = 0xFFA487FFL
    const val VIOLET_DEEP = 0xFF6A4BE0L
    const val ON_VIOLET = 0xFF160B3DL
    /** Sky blue: "Brilliant" moves in a review. */
    const val SKY = 0xFF5CC8FFL
    const val ON_SKY = 0xFF00213AL
    const val CORAL = 0xFFFF6F6FL
    const val ON_CORAL = 0xFF2B0505L

    /** Text colour and the backgrounds it is allowed on. Tested to meet WCAG AA (4.5:1). */
    val TEXT_PAIRS: List<Pair<Long, List<Long>>> = listOf(
        TEXT to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED, SURFACE_HIGH),
        TEXT_2 to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED, SURFACE_HIGH),
        TEXT_3 to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED),
        GOLD to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED, SURFACE_HIGH),
        TEAL to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED, SURFACE_HIGH),
        LIME to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED, SURFACE_HIGH),
        VIOLET to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED),
        CORAL to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED),
        SKY to listOf(BG_DEEP, BG, BG_INDIGO, SURFACE, SURFACE_RAISED),
        ON_GOLD to listOf(GOLD, GOLD_DEEP, LIME),
        // Gradient cards: these text colours may sit anywhere on a teal or violet card.
        TEXT to listOf(CARD_TEAL, CARD_VIOLET),
        TEXT_2 to listOf(CARD_TEAL, CARD_VIOLET),
        TEAL to listOf(CARD_TEAL, CARD_VIOLET),
        GOLD to listOf(CARD_TEAL, CARD_VIOLET),
        ON_TEAL to listOf(TEAL),
        ON_LIME to listOf(LIME),
        ON_CORAL to listOf(CORAL),
        ON_VIOLET to listOf(VIOLET),
        ON_SKY to listOf(SKY),
        // The eval bar's White share is drawn in TEXT; its score label there is BG_DEEP.
        BG_DEEP to listOf(TEXT),
    )

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
