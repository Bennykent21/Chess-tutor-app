package com.chesstutor.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

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

    private val scope = CoroutineScope(Dispatchers.Default)

    fun playMove(soundEnabled: Boolean = true, isCapture: Boolean = false, isCheck: Boolean = false) {
        // Haptic feedback
        vibrateMove(isCapture, isCheck)

        if (!soundEnabled) return

        scope.launch {
            try {
                if (isCheck) {
                    playCheckTone()
                } else if (isCapture) {
                    playCaptureClick()
                } else {
                    playPieceClick()
                }
            } catch (_: Throwable) {
                // Audio fallback gracefully silent
            }
        }
    }

    fun playBlunder(soundEnabled: Boolean = true) {
        vibrateBlunder()
        if (!soundEnabled) return
        scope.launch {
            try {
                playBlunderTone()
            } catch (_: Throwable) {}
        }
    }

    fun playSuccess(soundEnabled: Boolean = true) {
        vibrateSuccess()
        if (!soundEnabled) return
        scope.launch {
            try {
                playSuccessChime()
            } catch (_: Throwable) {}
        }
    }

    private fun vibrateMove(isCapture: Boolean, isCheck: Boolean) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when {
                    isCheck -> VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 60), intArrayOf(0, 180, 0, 255), -1)
                    isCapture -> VibrationEffect.createWaveform(longArrayOf(0, 25, 20, 35), intArrayOf(0, 160, 0, 220), -1)
                    else -> VibrationEffect.createOneShot(22, 120)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (isCheck) 60L else 25L)
            }
        } catch (_: Throwable) {}
    }

    private fun vibrateBlunder() {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 40, 80), intArrayOf(0, 220, 0, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(100L)
            }
        } catch (_: Throwable) {}
    }

    private fun vibrateSuccess() {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 25, 30, 25, 45), intArrayOf(0, 120, 0, 160, 0, 220), -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(40L)
            }
        } catch (_: Throwable) {}
    }

    private fun playPieceClick() {
        val sampleRate = 22050
        val durationMs = 38
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-50.0 * (i.toDouble() / numSamples))
            val wave = sin(2 * PI * 750.0 * t) + 0.3 * sin(2 * PI * 1400.0 * t)
            buffer[i] = (wave * decay * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
        }

        writePcmBuffer(buffer, sampleRate)
    }

    private fun playCaptureClick() {
        val sampleRate = 22050
        val durationMs = 60
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val split = numSamples / 2

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = if (i < split) {
                exp(-45.0 * (i.toDouble() / split))
            } else {
                exp(-40.0 * ((i - split).toDouble() / split))
            }
            val freq = if (i < split) 850.0 else 600.0
            val wave = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 1.8 * t)
            buffer[i] = (wave * decay * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
        }

        writePcmBuffer(buffer, sampleRate)
    }

    private fun playCheckTone() {
        val sampleRate = 22050
        val durationMs = 120
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-18.0 * (i.toDouble() / numSamples))
            val wave = 0.7 * sin(2 * PI * 880.0 * t) + 0.3 * sin(2 * PI * 1760.0 * t)
            buffer[i] = (wave * decay * 22000.0).toInt().coerceIn(-32767, 32767).toShort()
        }

        writePcmBuffer(buffer, sampleRate)
    }

    private fun playBlunderTone() {
        val sampleRate = 22050
        val durationMs = 160
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 340.0 - (progress * 130.0)
            val t = i.toDouble() / sampleRate
            val decay = exp(-8.0 * progress)
            val wave = 0.8 * sin(2 * PI * freq * t)
            buffer[i] = (wave * decay * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
        }

        writePcmBuffer(buffer, sampleRate)
    }

    private fun playSuccessChime() {
        val sampleRate = 22050
        val durationMs = 210
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val third = numSamples / 3

        for (i in 0 until numSamples) {
            val section = i / third
            val localIdx = i % third
            val t = i.toDouble() / sampleRate
            val freq = when (section) {
                0 -> 523.25 // C5
                1 -> 659.25 // E5
                else -> 783.99 // G5
            }
            val decay = exp(-12.0 * (localIdx.toDouble() / third))
            val wave = sin(2 * PI * freq * t)
            buffer[i] = (wave * decay * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
        }

        writePcmBuffer(buffer, sampleRate)
    }

    private fun writePcmBuffer(buffer: ShortArray, sampleRate: Int) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            scope.launch {
                kotlinx.coroutines.delay(buffer.size * 1000L / sampleRate + 50L)
                runCatching {
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
        } catch (_: Throwable) {}
    }
}
