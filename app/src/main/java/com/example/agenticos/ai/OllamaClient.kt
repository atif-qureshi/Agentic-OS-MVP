package com.example.agenticos.ai

import com.example.agenticos.model.CommandResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles all communication with the local Ollama server.
 *
 * IP auto-detection: tries multiple known IPs so it works
 * regardless of which WiFi network you are on.
 */
class OllamaClient {

    companion object {
        private const val PORT    = "11434"
        private const val MODEL   = "qwen3:1.7b"
        private val JSON_TYPE     = "application/json; charset=utf-8".toMediaType()

        // All possible IPs — tries each one until one works
        private val CANDIDATE_HOSTS = listOf(
            "10.69.48.107",    // Current network
            "192.168.100.9",   // Previous network
            "192.168.1.1",     // Common home router
            "192.168.0.1",     // Common home router 2
            "10.0.2.2"         // Emulator fallback
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)  // Model load time
        .readTimeout(180, TimeUnit.SECONDS)    // Long response wait
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Cached working URL — avoid re-scanning every time
    private var cachedBaseUrl: String? = null

    // ── Public API ────────────────────────────────────────────────────────────

    @Throws(OllamaException::class)
    suspend fun extractIntent(userCommand: String): CommandResult {
        val baseUrl = resolveBaseUrl()
            ?: throw OllamaException("Cannot connect to Ollama. Make sure it is running on your PC.")

        val requestJson = buildRequestJson(userCommand)
        val requestBody = requestJson.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyStr  = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw OllamaException("Ollama HTTP ${response.code}: $bodyStr")
            }
            parseResponse(bodyStr)
        } catch (e: OllamaException) {
            throw e
        } catch (e: Exception) {
            cachedBaseUrl = null // Reset cache on failure
            throw OllamaException("Cannot connect to Ollama: ${e.message}", e)
        }
    }

    fun isAvailable(): Boolean {
        return resolveBaseUrl() != null
    }

    // ── IP Auto-Detection ─────────────────────────────────────────────────────

    /**
     * Finds the first reachable Ollama host from CANDIDATE_HOSTS.
     * Caches the result so subsequent calls are instant.
     */
    private fun resolveBaseUrl(): String? {
        // Return cached URL if still working
        cachedBaseUrl?.let { url ->
            if (ping(url)) return url
            cachedBaseUrl = null // Cache invalidated
        }

        // Try each candidate
        for (host in CANDIDATE_HOSTS) {
            val url = "http://$host:$PORT"
            if (ping(url)) {
                cachedBaseUrl = url
                return url
            }
        }
        return null
    }

    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun ping(baseUrl: String): Boolean {
        return try {
            val req  = Request.Builder().url(baseUrl).get().build()
            val resp = pingClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ── Request Builder ───────────────────────────────────────────────────────

    private fun buildRequestJson(userCommand: String): String {
        val systemContent = PromptBuilder.systemPrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")

        val userContent = userCommand
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        return """{"model":"$MODEL","keep_alive":-1,"messages":[{"role":"system","content":"$systemContent"},{"role":"user","content":"$userContent"}],"stream":false,"think":false,"options":{"temperature":0.0,"num_predict":60,"top_k":1,"top_p":0.1}}"""
    }

    // ── Response Parser ───────────────────────────────────────────────────────

    private fun parseResponse(body: String): CommandResult {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw OllamaException("Invalid JSON from Ollama: $body")
        }

        val content = json
            .optJSONObject("message")
            ?.optString("content", "")
            ?: throw OllamaException("No message.content in response")

        // Strip <think>...</think> blocks
        var clean = content
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .trim()

        if (!clean.contains("{")) {
            throw OllamaException("No JSON object found in response:\n$content")
        }

        val start = clean.indexOf('{')
        val end   = clean.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            clean = clean.substring(start, end + 1)
        }

        return JsonValidator.parseCommandResult(clean)
    }
}

class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
