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
 * VoiceManager — stable two-mode mic control:
 *
 * WAKE_WORD — continuous background loop, silently restarts on timeout.
 * COMMAND   — single listen session, stops after result/error.
 *
 * Use pauseListening() before TTS; resumeWakeWord() after TTS finishes.
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
        private const val START_DELAY_MS = 350L
        private const val WAKE_RESTART_DELAY_MS = 500L
    }

    private var recognizer: SpeechRecognizer? = null
    private var currentMode: Mode = Mode.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false
    private var isPaused = false

    private val wakeWordRestart = Runnable {
        if (!isDestroyed && !isPaused && currentMode == Mode.IDLE) {
            currentMode = Mode.WAKE_WORD
            startOnce()
        }
    }

    enum class Mode { IDLE, WAKE_WORD, COMMAND }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Disable continuous wake-word loop (Tap-only mode). */
    fun startWakeWordListening() {
        // Tap-only mode: Wake word disabled
    }

    /** Start one command listen session. */
    fun startCommandListening() {
        if (isDestroyed) return
        isPaused = false
        handler.removeCallbacks(wakeWordRestart)
        currentMode = Mode.COMMAND
        destroyRecognizer()
        startOnce()
    }

    /** Pause listening. */
    fun pauseListening() {
        isPaused = true
        handler.removeCallbacks(wakeWordRestart)
        if (currentMode == Mode.WAKE_WORD || currentMode == Mode.COMMAND) {
            currentMode = Mode.IDLE
            destroyRecognizer()
        }
    }

    /** Tap-only mode — do not auto resume background wake word loop. */
    fun resumeWakeWord() {
        // Tap-only mode: Wake word disabled
    }

    /** Immediately stop all listening. */
    fun stopAll() {
        isPaused = true
        currentMode = Mode.IDLE
        handler.removeCallbacks(wakeWordRestart)
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    fun destroy() {
        isDestroyed = true
        stopAll()
    }

    val isListening: Boolean get() = currentMode != Mode.IDLE
    val isInCommandMode: Boolean get() = currentMode == Mode.COMMAND

    // ── Core ──────────────────────────────────────────────────────────────────

    private fun startOnce() {
        if (isDestroyed || isPaused || currentMode == Mode.IDLE) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onVoiceError("Speech recognition not available")
            currentMode = Mode.IDLE
            return
        }

        destroyRecognizer()

        handler.postDelayed({
            if (isDestroyed || isPaused || currentMode == Mode.IDLE) return@postDelayed
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also {
                it.setRecognitionListener(listener)
                it.startListening(buildIntent())
            }
        }, START_DELAY_MS)
    }

    private fun scheduleWakeWordRestart() {
        if (isDestroyed || isPaused) return
        currentMode = Mode.IDLE
        handler.removeCallbacks(wakeWordRestart)
        handler.postDelayed(wakeWordRestart, WAKE_RESTART_DELAY_MS)
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
            currentMode = Mode.IDLE
            destroyRecognizer()

            when (modeAtTime) {
                Mode.WAKE_WORD -> handleWakeWordError(error)
                Mode.COMMAND   -> handleCommandError(error)
                Mode.IDLE      -> {}
            }
        }

        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Result / Error Handlers ───────────────────────────────────────────────

    private fun handleWakeWordResult(matches: List<String>) {
        val heard = matches.joinToString(" ").lowercase()
        if (WAKE_WORDS.any { heard.contains(it) }) {
            isPaused = false
            handler.removeCallbacks(wakeWordRestart)
            currentMode = Mode.COMMAND
            handler.post {
                callback.onWakeWordDetected()
                startOnce()
            }
        } else {
            scheduleWakeWordRestart()
        }
    }

    private fun handleWakeWordError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                callback.onVoiceError("Microphone permission denied")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                handler.postDelayed(wakeWordRestart, 1000)
            else ->
                scheduleWakeWordRestart()
        }
    }

    private fun handleCommandResult(matches: List<String>) {
        val text = matches.firstOrNull()
        if (!text.isNullOrBlank()) {
            handler.post { callback.onCommandReceived(text) }
        } else {
            handler.post { callback.onVoiceError("Could not understand. Tap mic to try again.") }
        }
    }

    private fun handleCommandError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                callback.onVoiceError("Microphone permission denied")
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                callback.onVoiceError("No speech detected. Tap mic to try again.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                callback.onVoiceError("Microphone busy. Please try again.")
            else ->
                callback.onVoiceError("Voice error. Tap mic to try again.")
        }
    }

    // ── Intent Builder ────────────────────────────────────────────────────────

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, currentMode == Mode.COMMAND)

        if (currentMode == Mode.WAKE_WORD) {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        } else {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
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
