package com.example.agenticos.conversation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Text-to-Speech engine for Agentic OS.
 * Agent speaks responses out loud to the user.
 */
class AgentSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)   // Slightly slower — clearer
                tts?.setPitch(1.0f)
                isReady = true
            }
        }
    }

    /** Speak text out loud — interrupts any current speech */
    fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent_response")
    }

    /** Speak without interrupting current speech */
    fun speakQueued(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "agent_queued")
    }

    /** Stop speaking immediately */
    fun stop() {
        tts?.stop()
    }

    /** Check if currently speaking */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /** Release resources */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** Set callback when speech finishes */
    fun setOnDoneListener(onDone: () -> Unit) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            override fun onError(utteranceId: String?) {}
        })
    }
}
