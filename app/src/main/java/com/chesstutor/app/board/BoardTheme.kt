package com.chesstutor.app.board

import com.chesstutor.app.core.PieceColor
import com.chesstutor.app.data.settings.BoardOrientation

/**
 * The bundled piece sets (plan section 4.3, decision D22). Every one is GPL-3 compatible and
 * recorded in LICENSES.md; sources are in `design/pieces/<id>/`.
 * [id] is what settings store, so it must never change once released.
 */
enum class PieceSet(val id: String, val displayName: String, val credit: String) {
    CBURNETT("cburnett", "Classic", "Colin M.L. Burnett, GPLv2+"),
    MERIDA("merida", "Merida", "Armando Hernandez Marroquin, GPLv2+"),
    CHESSNUT("chessnut", "Chessnut", "Alexis Luengas, Apache 2.0"),
    FANTASY("fantasy", "Fantasy", "Maurizio Monge, MIT");

    companion object {
        val DEFAULT = CBURNETT

        /** Unknown or missing ids (an old or damaged setting) fall back to [DEFAULT]. */
        fun fromId(id: String?): PieceSet = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Board colours (plan section 4.3), as plain ARGB values so they can be tested on the JVM.
 * All original. Coordinates are small text, so each theme picks its own ink for light and
 * dark squares; BoardThemeTest holds both to WCAG AA (4.5:1).
 * [id] is what settings store, so it must never change once released.
 */
enum class BoardColors(
    val id: String,
    val displayName: String,
    val light: Long,
    val dark: Long,
    val coordinateOnLight: Long,
    val coordinateOnDark: Long,
    /** Wood and stone themes get a soft diagonal sheen across the board. */
    val sheen: Boolean = false,
) {
    CLASSIC("classic", "Classic wood", 0xFFEFDAB8L, 0xFFB5835AL, 0xFF5A3418L, 0xFF2A1708L, sheen = true),
    MEADOW("meadow", "Meadow", 0xFFE9EDCFL, 0xFF6E9A55L, 0xFF3C5A2AL, 0xFF0F2209L),
    LAGOON("lagoon", "Lagoon", 0xFFDCE8F2L, 0xFF6690BAL, 0xFF2B4C6EL, 0xFF061525L),
    WALNUT("walnut", "Walnut", 0xFFE3C29CL, 0xFFA26E44L, 0xFF4A2A12L, 0xFF0D0500L, sheen = true),
    ICE("ice", "Ice", 0xFFF2F7FBL, 0xFFA9C7DDL, 0xFF4F6F86L, 0xFF1C3346L),
    NEON("neon", "Neon", 0xFFC9F7F2L, 0xFF7B6CF0L, 0xFF3A2F96L, 0xFF0E0838L),
    MARBLE("marble", "Marble", 0xFFECEAE6L, 0xFFA7A39CL, 0xFF55514BL, 0xFF1F1D1AL, sheen = true);

    companion object {
        val DEFAULT = CLASSIC

        /** Unknown or missing ids (an old or damaged setting) fall back to [DEFAULT]. */
        fun fromId(id: String?): BoardColors = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Translucent marks drawn over squares (design language section 2). Shared by every app and
 * board theme so meaning never changes: brass for the last move and the selection, a dark ink
 * for legal moves, red for check and mistakes, the move-grade colours for engine arrows.
 */
object BoardMarkColors {
    /** Brass at 48%. */
    const val LAST_MOVE = 0x7AD6AA4AL
    /** Brass at 66%. */
    const val SELECTED = 0xA8D6AA4AL
    /** Dark ink at 26%: dots on empty squares, rings on captures. */
    const val LEGAL_DOT = 0x42281C0AL
    const val CHECK = 0xD9D9473BL
    /** The engine's best move: the "Best" grade green. */
    const val ARROW_BEST = 0xC72E8650L
    const val ARROW_HINT = 0xC7D6AA4AL
    /** Arrows the player draws: the "Brilliant" grade teal. */
    const val ARROW_USER = 0xC71C8AA3L
    /** Another good move: the "Great" grade blue. */
    const val ARROW_ALTERNATIVE = 0x994769CCL
    /** A wrong move in a puzzle, flashed on its target square: the "Blunder" red. */
    const val MISTAKE = 0xE6BF3728L
    /** Under a review's quality badge, so it stands out on any square. */
    const val BADGE_SHADOW = 0x66000000L
}

/** Who you are on every board: colours and pieces, from user settings (plan section 4.1). */
data class BoardTheme(
    val colors: BoardColors = BoardColors.DEFAULT,
    val pieceSet: PieceSet = PieceSet.DEFAULT,
)

/**
 * Whether White sits at the bottom. The board faces the side you play (decision D2) unless
 * the player chose "White always at bottom".
 */
fun whiteAtBottom(orientation: BoardOrientation, playerSide: PieceColor): Boolean = when (orientation) {
    BoardOrientation.FOLLOW_SIDE -> playerSide == PieceColor.WHITE
    BoardOrientation.WHITE_ALWAYS_BOTTOM -> true
}
