package com.chesstutor.app.domain

import androidx.annotation.DrawableRes
import com.chesstutor.app.R

/**
 * The coach the player picked (decision D20). All three ship; Pip is the default.
 * [id] is what settings store, so it must never change once released.
 * Art sources: `design/mascot/` (original, GPL-3.0 with the app).
 */
enum class Mascot(
    val id: String,
    val displayName: String,
    val tagline: String,
    @param:DrawableRes val art: Int,
) {
    PIP("pip", "Pip", "An eager pawn who grows with you", R.drawable.mascot_pip),
    ROOKIE("rookie", "Rookie", "A rook-shaped robot with an engine for a heart", R.drawable.mascot_rookie),
    SAGE("sage", "Sage", "A patient turtle with a chessboard shell", R.drawable.mascot_sage);

    companion object {
        val DEFAULT = PIP

        /** Unknown or missing ids (an old or damaged setting) fall back to [DEFAULT]. */
        fun fromId(id: String?): Mascot = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
