package com.chesstutor.app.audio

import androidx.annotation.RawRes
import com.chesstutor.app.R
import com.chesstutor.app.board.BoardSound

/**
 * The sounds of moves and results (D108). Wood is Kenney's (CC0); the others are Enigmahack's
 * sets from lichess (AGPLv3+), see LICENSES.md. Right, wrong and tap sounds are the same in every
 * pack. [id] is what settings store, so it must never change once released.
 */
enum class SoundPack(val id: String, val label: String) {
    WOOD("wood", "Wood"),
    STUDIO("studio", "Studio"),
    PIANO("piano", "Piano"),
    RETRO("retro", "Retro"),
    SCIFI("scifi", "Sci-fi"),
    ;

    @RawRes
    fun rawFor(sound: BoardSound): Int = when (sound) {
        BoardSound.CORRECT -> R.raw.sfx_correct
        BoardSound.WRONG -> R.raw.sfx_wrong
        BoardSound.TAP -> R.raw.sfx_tap
        else -> moves.getValue(this)[MOVE_SOUNDS.indexOf(sound)]
    }

    companion object {
        val DEFAULT = WOOD

        /** Unknown or missing ids fall back to [DEFAULT]. */
        fun fromId(id: String?): SoundPack = entries.firstOrNull { it.id == id } ?: DEFAULT

        private val MOVE_SOUNDS = listOf(BoardSound.MOVE, BoardSound.CAPTURE, BoardSound.CASTLE, BoardSound.CHECK, BoardSound.PROMOTE, BoardSound.WIN, BoardSound.LOSE)

        /** Per pack, in [MOVE_SOUNDS] order; listed explicitly so the resource shrinker keeps them. Only Wood has its own castling. */
        private val moves: Map<SoundPack, IntArray> = mapOf(
            WOOD to intArrayOf(R.raw.sfx_move, R.raw.sfx_capture, R.raw.sfx_castle, R.raw.sfx_check, R.raw.sfx_promote, R.raw.sfx_win, R.raw.sfx_lose),
            STUDIO to intArrayOf(R.raw.sfx_studio_move, R.raw.sfx_studio_capture, R.raw.sfx_studio_move, R.raw.sfx_studio_check, R.raw.sfx_studio_promote, R.raw.sfx_studio_win, R.raw.sfx_studio_lose),
            PIANO to intArrayOf(R.raw.sfx_piano_move, R.raw.sfx_piano_capture, R.raw.sfx_piano_move, R.raw.sfx_piano_check, R.raw.sfx_piano_promote, R.raw.sfx_piano_win, R.raw.sfx_piano_lose),
            RETRO to intArrayOf(R.raw.sfx_retro_move, R.raw.sfx_retro_capture, R.raw.sfx_retro_move, R.raw.sfx_retro_check, R.raw.sfx_retro_promote, R.raw.sfx_retro_win, R.raw.sfx_retro_lose),
            SCIFI to intArrayOf(R.raw.sfx_scifi_move, R.raw.sfx_scifi_capture, R.raw.sfx_scifi_move, R.raw.sfx_scifi_check, R.raw.sfx_scifi_promote, R.raw.sfx_scifi_win, R.raw.sfx_scifi_lose),
        )
    }
}
