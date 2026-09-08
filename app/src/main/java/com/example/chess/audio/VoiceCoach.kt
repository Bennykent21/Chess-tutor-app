package com.example.chess.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Android Text-To-Speech (TTS) Voice Coach commentary manager.
 * Speaks pedagogical explanations, blunder alerts, and hint steps in real-time.
 */
class VoiceCoach(context: Context) {

  private var tts: TextToSpeech? = null
  private var isInitialized = false
  var isSpeechEnabled = true

  init {
    tts = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val result = tts?.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          Log.w("VoiceCoach", "US English TTS not supported or missing data, falling back to default")
          tts?.setLanguage(Locale.getDefault())
        }
        tts?.setSpeechRate(0.95f) // Warm, clear pedagogical cadence
        tts?.setPitch(1.0f)
        isInitialized = true
      } else {
        Log.e("VoiceCoach", "TTS initialization failed with status: $status")
      }
    }
  }

  fun speak(text: String, flush: Boolean = true) {
    if (!isSpeechEnabled || !isInitialized || text.isBlank()) return
    val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    tts?.speak(text, queueMode, null, "coach_utterance_${System.currentTimeMillis()}")
  }

  fun stop() {
    tts?.stop()
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
    isInitialized = false
  }
}

/**
 * Remembers a VoiceCoach lifecycle across Composables.
 */
@Composable
fun rememberVoiceCoach(): VoiceCoach {
  val context = LocalContext.current
  val voiceCoach = remember { VoiceCoach(context) }
  DisposableEffect(voiceCoach) {
    onDispose {
      voiceCoach.stop()
    }
  }
  return voiceCoach
}
