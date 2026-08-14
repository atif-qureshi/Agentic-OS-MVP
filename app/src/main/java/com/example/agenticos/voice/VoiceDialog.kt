package com.example.agenticos.voice

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.animation.AnimationUtils
import android.view.LayoutInflater
import android.widget.TextView
import com.example.agenticos.R
import com.example.agenticos.databinding.DialogVoiceBinding
import kotlin.math.abs

/**
 * Gemini-style fullscreen voice dialog with:
 *  - Animated pulsing mic rings
 *  - Live transcription text
 *  - Volume-reactive animation
 *  - Cancel button
 */
class VoiceDialog(
    context: Context,
    private val onCancel: () -> Unit
) {
    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val binding: DialogVoiceBinding

    init {
        val inflater = LayoutInflater.from(context)
        binding = DialogVoiceBinding.inflate(inflater)

        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
        dialog.setCancelable(false)

        // Start pulse animations
        val pulse1 = AnimationUtils.loadAnimation(context, R.anim.pulse_scale)
        val pulse2 = AnimationUtils.loadAnimation(context, R.anim.pulse_scale_slow)
        binding.pulseRing1.startAnimation(pulse1)
        binding.pulseRing2.startAnimation(pulse2)

        binding.btnCancelVoice.setOnClickListener {
            dismiss()
            onCancel()
        }
    }

    fun show() = dialog.show()

    fun dismiss() {
        binding.pulseRing1.clearAnimation()
        binding.pulseRing2.clearAnimation()
        if (dialog.isShowing) dialog.dismiss()
    }

    /** Update live transcription text while user is speaking */
    fun updateLiveText(text: String) {
        binding.tvLiveText.text = text
    }

    /** React to volume — scale pulse rings based on voice loudness */
    fun onVolumeChanged(rms: Float) {
        val scale = 1f + (abs(rms) / 20f).coerceIn(0f, 0.5f)
        binding.pulseRing1.scaleX = scale
        binding.pulseRing1.scaleY = scale
    }

    fun setListening() {
        binding.tvLiveText.text = "Listening…"
    }

    fun setProcessing() {
        binding.tvLiveText.text = "Processing…"
        binding.pulseRing1.clearAnimation()
        binding.pulseRing2.clearAnimation()
    }
}
