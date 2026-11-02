package com.chesstutor.app.domain

import androidx.annotation.DrawableRes
import com.chesstutor.app.R

/** How the coach looks while it speaks (decision D40). */
enum class MascotMood(
    /** What a screen reader says about the face, after the coach's name. */
    val description: String,
) {
    /** Waiting for your move, explaining. */
    READY("ready"),
    /** Giving a hint. */
    THINKING("thinking"),
    /** After a mistake: encouraging, never scolding. */
    KIND("encouraging"),
    /** You solved it. */
    PROUD("proud"),
}

/**
 * The coach the player picked (decision D20). All three ship; Pip is the default.
 * [id] is what settings store, so it must never change once released.
 * Art sources: `design/mascot/<coach>_<mood>.svg` (original, GPL-3.0 with the app), converted by
 * `tools/mascot_svg_to_vector.py`.
 */
enum class Mascot(
    val id: String,
    val displayName: String,
    val tagline: String,
) {
    PIP("pip", "Pip", "An eager pawn who grows with you"),
    ROOKIE("rookie", "Rookie", "A steady rook with a brass band, calm under pressure"),
    SAGE("sage", "Sage", "A patient turtle with a chessboard shell");

    /** The coach's drawing in [mood]. */
    @DrawableRes
    fun art(mood: MascotMood = MascotMood.READY): Int = when (this) {
        PIP -> when (mood) {
            MascotMood.READY -> R.drawable.mascot_pip_ready
            MascotMood.THINKING -> R.drawable.mascot_pip_thinking
            MascotMood.KIND -> R.drawable.mascot_pip_kind
            MascotMood.PROUD -> R.drawable.mascot_pip_proud
        }
        ROOKIE -> when (mood) {
            MascotMood.READY -> R.drawable.mascot_rookie_ready
            MascotMood.THINKING -> R.drawable.mascot_rookie_thinking
            MascotMood.KIND -> R.drawable.mascot_rookie_kind
            MascotMood.PROUD -> R.drawable.mascot_rookie_proud
        }
        SAGE -> when (mood) {
            MascotMood.READY -> R.drawable.mascot_sage_ready
            MascotMood.THINKING -> R.drawable.mascot_sage_thinking
            MascotMood.KIND -> R.drawable.mascot_sage_kind
            MascotMood.PROUD -> R.drawable.mascot_sage_proud
        }
    }

    companion object {
        val DEFAULT = PIP

        /** Unknown or missing ids (an old or damaged setting) fall back to [DEFAULT]. */
        fun fromId(id: String?): Mascot = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
