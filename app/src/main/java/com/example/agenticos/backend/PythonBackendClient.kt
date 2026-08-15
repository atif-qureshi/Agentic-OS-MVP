package com.example.agenticos.backend

import com.example.agenticos.context.AgentContext
import com.example.agenticos.context.ContextMemory
import com.example.agenticos.model.CommandResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PythonBackendClient {

    companion object {
        private const val PORT = "8000"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val CANDIDATE_HOSTS = listOf(
            "10.0.2.2",
            "127.0.0.1",
            "localhost",
            "192.168.1.10",
            "192.168.0.10",
            "192.168.100.9"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedBaseUrl: String? = null

    fun isAvailable(): Boolean = resolveBaseUrl() != null

    fun extractIntent(command: String, context: AgentContext = ContextMemory.snapshot()): CommandResult? {
        val baseUrl = resolveBaseUrl() ?: return null
        val payload = JSONObject().apply {
            put("text", command.trim())
            put("context", JSONObject().apply {
                put("app", context.app)
                put("screen", context.screen)
                put("last_intent", context.lastIntent ?: "none")
                put("last_account", context.lastAccount ?: "none")
                put("memory", JSONObject().put("summary", context.memorySummary.ifBlank { "No personal memory yet." }))
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/process")
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .header("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            parseResponse(body)
        } catch (_: Exception) {
            cachedBaseUrl = null
            null
        }
    }

    private fun resolveBaseUrl(): String? {
        cachedBaseUrl?.let { url ->
            if (ping(url)) return url
            cachedBaseUrl = null
        }

        for (host in CANDIDATE_HOSTS) {
            val url = "http://$host:$PORT"
            if (ping(url)) {
                cachedBaseUrl = url
                return url
            }
        }

        return null
    }

    private fun ping(baseUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    private fun parseResponse(rawBody: String): CommandResult? {
        return try {
            val root = JSONObject(rawBody)
            val data = root.optJSONObject("data") ?: root
            val intent = data.optString("intent", "UNKNOWN").trim()
            if (intent.isBlank() || intent.equals("UNKNOWN", ignoreCase = true)) {
                return null
            }

            val entitiesObject = data.optJSONObject("entities") ?: JSONObject()
            val entities = mutableMapOf<String, String>()
            val keys = entitiesObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                entities[key] = entitiesObject.optString(key, "")
            }

            CommandResult(intent, entities)
        } catch (_: Exception) {
            null
        }
    }
}
