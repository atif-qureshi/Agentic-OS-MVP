package com.example.agenticos.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * VoiceManager — Two modes:
 *
 * 1. WAKE_WORD mode — single shot listen, stops after result/error.
 *    User must manually call startWakeWordListening() to listen again.
 *    This prevents continuous mic usage.
 *
 * 2. COMMAND mode — listens for one command then stops.
 *
 * Mic is ONLY on when explicitly started. Never auto-restarts.
 */
class VoiceManager(
    private val context: Context,
    private val callback: VoiceCallback
) {
    companion object {
        private val WAKE_WORDS = listOf(
            "hey agentic", "hey agent", "agentic",
            "hi agentic", "ok agentic", "hello agentic"
        )
        private const val START_DELAY_MS = 200L
    }

    private var recognizer: SpeechRecognizer? = null
    private var currentMode: Mode = Mode.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    enum class Mode { IDLE, WAKE_WORD, COMMAND }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start ONE wake word listen session.
     * Mic turns ON, listens once, then turns OFF.
     * Does NOT auto-restart — caller must call again if needed.
     */
    fun startWakeWordListening() {
        if (isDestroyed) return
        if (currentMode == Mode.COMMAND) return  // Don't interrupt command
        currentMode = Mode.WAKE_WORD
        startOnce()
    }

    /**
     * Start ONE command listen session.
     * Mic turns ON, listens once, then turns OFF.
     */
    fun startCommandListening() {
        if (isDestroyed) return
        currentMode = Mode.COMMAND
        startOnce()
    }

    /**
     * Immediately stop all listening. Mic goes OFF.
     */
    fun stopAll() {
        currentMode = Mode.IDLE
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    fun destroy() {
        isDestroyed = true
        stopAll()
    }

    val isListening: Boolean
        get() = currentMode != Mode.IDLE

    // ── Core ──────────────────────────────────────────────────────────────────

    private fun startOnce() {
        if (isDestroyed || currentMode == Mode.IDLE) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onVoiceError("Speech recognition not available")
            currentMode = Mode.IDLE
            return
        }

        destroyRecognizer()

        handler.postDelayed({
            if (isDestroyed || currentMode == Mode.IDLE) return@postDelayed
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
                it.startListening(buildIntent())
            }
        }, START_DELAY_MS)
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            if (currentMode == Mode.COMMAND) callback.onListeningStarted()
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (currentMode == Mode.COMMAND) callback.onVolumeChanged(rmsdB)
        }

        override fun onEndOfSpeech() {
            if (currentMode == Mode.COMMAND) callback.onListeningEnded()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (currentMode != Mode.COMMAND) return
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) callback.onPartialResult(partial)
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: emptyList()

            val modeAtTime = currentMode
            // Mic is now OFF — set to IDLE
            currentMode = Mode.IDLE
            destroyRecognizer()

            when (modeAtTime) {
                Mode.WAKE_WORD -> handleWakeWordResult(matches)
                Mode.COMMAND   -> handleCommandResult(matches)
                Mode.IDLE      -> {}
            }
        }

        override fun onError(error: Int) {
            val modeAtTime = currentMode
            // Mic is now OFF
            currentMode = Mode.IDLE
            destroyRecognizer()

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    callback.onVoiceError("Microphone permission denied")
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal — user didn't speak or no match
                    if (modeAtTime == Mode.COMMAND) {
                        callback.onVoiceError("No speech detected. Tap mic to try again.")
                    }
                    // Wake word: silently stop — user didn't say anything
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Busy — just stop, user can tap again
                    if (modeAtTime == Mode.COMMAND) {
                        callback.onVoiceError("Microphone busy. Please try again.")
                    }
                }
                else -> {
                    if (modeAtTime == Mode.COMMAND) {
                        callback.onVoiceError("Voice error. Tap mic to try again.")
                    }
                }
            }
        }

        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Result Handlers ───────────────────────────────────────────────────────

    private fun handleWakeWordResult(matches: List<String>) {
        val heard = matches.joinToString(" ").lowercase()
        if (WAKE_WORDS.any { heard.contains(it) }) {
            // Wake word detected — switch to command mode
            currentMode = Mode.COMMAND
            handler.post {
                callback.onWakeWordDetected()
                // Start command listening AFTER callback (dialog will show)
                startOnce()
            }
        }
        // No wake word → mic stays off until next manual call
    }

    private fun handleCommandResult(matches: List<String>) {
        val text = matches.firstOrNull()
        if (!text.isNullOrBlank()) {
            handler.post { callback.onCommandReceived(text) }
        } else {
            handler.post { callback.onVoiceError("Could not understand. Tap mic to try again.") }
        }
    }

    // ── Intent Builder ────────────────────────────────────────────────────────

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, currentMode == Mode.COMMAND)

        if (currentMode == Mode.WAKE_WORD) {
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        } else {
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }
}

interface VoiceCallback {
    fun onWakeWordDetected()
    fun onCommandReceived(text: String)
    fun onPartialResult(text: String)
    fun onListeningStarted()
    fun onListeningEnded()
    fun onVolumeChanged(rms: Float)
    fun onVoiceError(message: String)
}
