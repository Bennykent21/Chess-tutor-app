package com.chesstutor.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.chesstutor.app.R
import com.chesstutor.app.board.BoardFeedback
import com.chesstutor.app.board.BoardSound
import com.chesstutor.app.board.HapticPattern
import com.chesstutor.app.board.MoveEffects

/**
 * Plays the sound pack (Kenney, CC0; see LICENSES.md) and the vibration patterns of plan
 * section 4.3. Sounds and haptics each follow their own setting.
 */
class ChessSoundManager(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    private val soundPool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } catch (_: Throwable) {
        null
    }

    /** Sound ids inside [soundPool]. Loading is asynchronous; a sound not loaded yet is skipped. */
    private val soundIds: Map<BoardSound, Int> = soundPool?.let { pool ->
        BoardSound.entries.associateWith { sound -> runCatching { pool.load(context, rawFor(sound), 1) }.getOrDefault(0) }
    } ?: emptyMap()

    /** Follows the Haptics setting. When false, no vibration at all. */
    @Volatile
    var hapticsEnabled: Boolean = true

    /** The new board's entry point: one sound and one vibration per event. */
    fun play(feedback: BoardFeedback, soundEnabled: Boolean) {
        vibrate(feedback.haptic)
        if (soundEnabled) feedback.sound?.let(::playSound)
    }

    private fun playSound(sound: BoardSound) {
        val id = soundIds[sound] ?: return
        if (id == 0) return
        runCatching { soundPool?.play(id, 1f, 1f, 1, 0, 1f) }
    }

    private fun vibrate(pattern: HapticPattern) {
        try {
            if (!hapticsEnabled) return
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = if (vib.hasAmplitudeControl()) pattern.amplitudes else IntArray(pattern.amplitudes.size) { if (it % 2 == 1) VibrationEffect.DEFAULT_AMPLITUDE else 0 }
                vib.vibrate(VibrationEffect.createWaveform(pattern.timingsMs, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern.timingsMs, -1)
            }
        } catch (_: Throwable) {}
    }

    private fun rawFor(sound: BoardSound): Int = when (sound) {
        BoardSound.MOVE -> R.raw.sfx_move
        BoardSound.CAPTURE -> R.raw.sfx_capture
        BoardSound.CASTLE -> R.raw.sfx_castle
        BoardSound.CHECK -> R.raw.sfx_check
        BoardSound.PROMOTE -> R.raw.sfx_promote
        BoardSound.WIN -> R.raw.sfx_win
        BoardSound.LOSE -> R.raw.sfx_lose
        BoardSound.CORRECT -> R.raw.sfx_correct
        BoardSound.WRONG -> R.raw.sfx_wrong
        BoardSound.TAP -> R.raw.sfx_tap
    }
}
