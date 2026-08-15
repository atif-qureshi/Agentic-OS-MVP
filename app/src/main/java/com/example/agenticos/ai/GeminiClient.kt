package com.example.agenticos.ai

import com.example.agenticos.model.ActionStep
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

class GeminiClient {

    companion object {
        private const val MODEL_NAME     = "gemini-1.5-flash"
        
        // Load API key from BuildConfig (set in build.gradle from local.properties)
        fun getApiKey(): String {
            // BuildConfig.GEMINI_API_KEY is injected via build.gradle
            return try {
                Class.forName("com.example.agenticos.BuildConfig")
                    .getDeclaredField("GEMINI_API_KEY")
                    .get(null) as String
            } catch (e: Exception) {
                "" // Empty key if not configured - client should handle gracefully
            }
        }
    }

    private val model = GenerativeModel(
        modelName    = MODEL_NAME,
        apiKey       = getApiKey(),
        generationConfig = generationConfig {
            temperature      = 0.1f
            maxOutputTokens  = 512
        }
    )

    suspend fun analyze(prompt: String): List<ActionStep> {
        return try {
            val response = model.generateContent(prompt)
            val text = response.text ?: ""
            JsonValidator.parseGeminiResponse(text)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
