package com.example.chess.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural low-latency Audio Engine for Chess sound effects.
 * Synthesizes high-fidelity wooden piece clicks, captures, check alerts,
 * tactical hints, and victory/defeat chimes using 16-bit PCM and AudioTrack.
 * Zero external asset dependencies, zero network requests, instant playback.
 */
class ChessSoundEffects {

  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
  var isSoundEnabled: Boolean = true

  enum class Cue {
    MOVE,
    CAPTURE,
    CHECK,
    VICTORY,
    DEFEAT,
    BLUNDER,
    HINT
  }

  // Pre-synthesized PCM buffers (sample rate: 44100 Hz, 16-bit mono)
  private val soundBuffers = ConcurrentHashMap<Cue, ByteArray>()

  init {
    try {
      pregenerateAudioBuffers()
    } catch (e: Throwable) {
      Log.w("ChessSoundEffects", "Audio synthesis init notice: ${e.message}")
    }
  }

  private fun pregenerateAudioBuffers() {
    soundBuffers[Cue.MOVE] = synthesizeMoveSound()
    soundBuffers[Cue.CAPTURE] = synthesizeCaptureSound()
    soundBuffers[Cue.CHECK] = synthesizeCheckSound()
    soundBuffers[Cue.VICTORY] = synthesizeVictorySound()
    soundBuffers[Cue.DEFEAT] = synthesizeDefeatSound()
    soundBuffers[Cue.BLUNDER] = synthesizeBlunderSound()
    soundBuffers[Cue.HINT] = synthesizeHintSound()
  }

  fun play(cue: Cue) {
    if (!isSoundEnabled) return
    val buffer = soundBuffers[cue] ?: return

    scope.launch {
      try {
        val sampleRate = 44100
        val track = AudioTrack.Builder()
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
          .setBufferSizeInBytes(buffer.size)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

        track.write(buffer, 0, buffer.size)
        track.play()

        // Clean up track after estimated duration
        val durationMs = (buffer.size.toLong() * 1000L) / (sampleRate * 2L)
        kotlinx.coroutines.delay(durationMs + 100L)
        track.stop()
        track.release()
      } catch (e: Throwable) {
        // Fallback or silent catch if sound hardware is busy/unavailable
        Log.d("ChessSoundEffects", "Playback skipped: ${e.message}")
      }
    }
  }

  fun playMove(isCapture: Boolean = false, isCheck: Boolean = false) {
    when {
      isCheck -> play(Cue.CHECK)
      isCapture -> play(Cue.CAPTURE)
      else -> play(Cue.MOVE)
    }
  }

  fun playVictory() = play(Cue.VICTORY)
  fun playDefeat() = play(Cue.DEFEAT)
  fun playBlunder() = play(Cue.BLUNDER)
  fun playHint() = play(Cue.HINT)

  fun release() {
    scope.cancel()
    soundBuffers.clear()
  }

  // --- Procedural PCM Waveform Synthesizers ---

  private fun synthesizeMoveSound(): ByteArray {
    // 55ms crisp wooden board tap: transient click followed by rapidly decaying resonant wood thud
    val sampleRate = 44100
    val durationSec = 0.055
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    val random = Random(42)
    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      // Pitch sweeps from 480Hz down to 180Hz
      val freq = 480.0 - (300.0 * (t / durationSec))
      val woodResonance = sin(2.0 * PI * freq * t) * exp(-t / 0.012)
      // Transient surface click in the first 5ms
      val click = if (t < 0.006) (random.nextDouble() * 2.0 - 1.0) * exp(-t / 0.002) * 0.4 else 0.0

      val sample = ((woodResonance * 0.75 + click) * 32767.0 * 0.85).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeCaptureSound(): ByteArray {
    // 85ms heavier wooden impact: snappy double-impact strike and deeper body resonance
    val sampleRate = 44100
    val durationSec = 0.085
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    val random = Random(123)
    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      // Primary impact
      val freq1 = 400.0 - (260.0 * (t / durationSec))
      val strike1 = sin(2.0 * PI * freq1 * t) * exp(-t / 0.018)

      // Secondary piece collision tap at t = 14ms
      val strike2 = if (t > 0.014) {
        val t2 = t - 0.014
        sin(2.0 * PI * 320.0 * t2) * exp(-t2 / 0.014) * 0.7
      } else 0.0

      // Snappy wood crack transient
      val click = if (t < 0.008) (random.nextDouble() * 2.0 - 1.0) * exp(-t / 0.003) * 0.5 else 0.0

      val sample = ((strike1 * 0.6 + strike2 * 0.4 + click) * 32767.0 * 0.9).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeCheckSound(): ByteArray {
    // 320ms crystalline alert chime: harmonic dual-tone D5 (587.33Hz) + A5 (880Hz) with shimmering decay
    val sampleRate = 44100
    val durationSec = 0.320
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val f1 = sin(2.0 * PI * 587.33 * t)
      val f2 = sin(2.0 * PI * 880.00 * t) * 0.5
      val f3 = sin(2.0 * PI * 1174.66 * t) * 0.25 // subtle upper octave sparkle

      val envelope = exp(-t / 0.09)
      val sample = ((f1 + f2 + f3) / 1.75 * envelope * 32767.0 * 0.75).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeVictorySound(): ByteArray {
    // 480ms triumphant upward arpeggio: C5 -> E5 -> G5 -> C6
    val sampleRate = 44100
    val durationSec = 0.480
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    val notes = listOf(
      Pair(0.00, 523.25), // C5
      Pair(0.08, 659.25), // E5
      Pair(0.16, 783.99), // G5
      Pair(0.24, 1046.50) // C6
    )

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      var mix = 0.0

      for ((start, freq) in notes) {
        if (t >= start) {
          val dt = t - start
          val env = exp(-dt / 0.12)
          val tone = sin(2.0 * PI * freq * dt) + 0.3 * sin(2.0 * PI * freq * 2 * dt)
          mix += tone * env
        }
      }

      val sample = (mix * 0.38 * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeDefeatSound(): ByteArray {
    // 420ms soft minor descent: F#4 -> D4 -> B3
    val sampleRate = 44100
    val durationSec = 0.420
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    val notes = listOf(
      Pair(0.00, 369.99), // F#4
      Pair(0.11, 293.66), // D4
      Pair(0.22, 246.94)  // B3
    )

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      var mix = 0.0

      for ((start, freq) in notes) {
        if (t >= start) {
          val dt = t - start
          val env = exp(-dt / 0.14)
          val tone = sin(2.0 * PI * freq * dt)
          mix += tone * env
        }
      }

      val sample = (mix * 0.45 * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeBlunderSound(): ByteArray {
    // 220ms dissonant warning thud (low tritone minor buzz: 220Hz + 311Hz)
    val sampleRate = 44100
    val durationSec = 0.220
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val w1 = sin(2.0 * PI * 220.0 * t)
      val w2 = sin(2.0 * PI * 311.13 * t) * 0.8
      val env = exp(-t / 0.06)

      val sample = (((w1 + w2) / 1.8) * env * 32767.0 * 0.7).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }

  private fun synthesizeHintSound(): ByteArray {
    // 90ms gentle crystal waterdrop chime (1174Hz high sparkle)
    val sampleRate = 44100
    val durationSec = 0.090
    val numSamples = (sampleRate * durationSec).toInt()
    val pcm = ByteArray(numSamples * 2)

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val tone = sin(2.0 * PI * 1174.66 * t) + 0.25 * sin(2.0 * PI * 2349.32 * t)
      val env = exp(-t / 0.025)

      val sample = (tone * env * 32767.0 * 0.6).toInt().coerceIn(-32768, 32767).toShort()
      pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
      pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
  }
}

/**
 * Remembers a ChessSoundEffects instance across Compose lifecycles.
 */
@Composable
fun rememberChessSoundEffects(): ChessSoundEffects {
  val soundEffects = remember { ChessSoundEffects() }
  DisposableEffect(soundEffects) {
    onDispose {
      soundEffects.release()
    }
  }
  return soundEffects
}
