package com.example.agenticos.conversation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Handles conversational responses — when user asks questions
 * instead of giving commands.
 */
class ConversationEngine {

    private val history = ArrayDeque<ConversationTurn>(5)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60,  TimeUnit.SECONDS)
        .build()

    private val CANDIDATE_URLS = listOf(
        "http://10.69.48.107:11434",
        "http://192.168.100.9:11434",
        "http://192.168.1.1:11434",
        "http://localhost:11434"
    )

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun respond(userQuery: String): String {
        val builtIn = handleBuiltIn(userQuery)
        if (builtIn != null) {
            addToHistory(userQuery, builtIn)
            return builtIn
        }
        return withContext(Dispatchers.IO) {
            try {
                val response = callQwen3(userQuery)
                addToHistory(userQuery, response)
                response
            } catch (e: Exception) {
                "I'm sorry, I couldn't process that. Please try again."
            }
        }
    }

    // ── Built-in Responses ────────────────────────────────────────────────────

    private fun handleBuiltIn(query: String): String? {
        val q = query.lowercase().trim()
        return when {
            q.contains("who are you") || q.contains("what are you") ->
                "I am Agentic, your AI-powered Android assistant. I can open apps, make calls, control Instagram, post photos, and answer your questions."

            q.contains("what can you do") || q.contains("help") ->
                "I can open apps, call contacts, search Google, post on Instagram, like posts, comment, follow accounts, answer questions, and much more. Just tell me what you need!"

            q.contains("what time") || q == "time" -> {
                val t = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                "It is $t."
            }

            q.contains("what date") || q.contains("today") || q.contains("what day") -> {
                val d = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
                "Today is $d."
            }

            q == "hello" || q == "hi" || q == "hey" ||
            q.contains("good morning") || q.contains("good evening") ->
                "Hello! I'm Agentic. How can I help you today?"

            q.contains("how are you") ->
                "I'm doing great, thank you! Ready to help you."

            q.contains("thank") ->
                "You're welcome! Let me know if you need anything else."

            q.contains("bye") || q.contains("goodbye") ->
                "Goodbye! Say Hey Agentic whenever you need me."

            q.contains("weather") ->
                "I don't have live weather data yet. You can say Open Google and search for weather."

            // Math
            q.matches(Regex(".*\\d+\\s*[+\\-*/x×÷]\\s*\\d+.*")) ->
                solveMath(q)

            else -> null
        }
    }

    private fun solveMath(query: String): String {
        return try {
            val pattern = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×÷])\\s*(\\d+(?:\\.\\d+)?)")
            val match = pattern.find(query) ?: return "I couldn't calculate that."
            val a  = match.groupValues[1].toDouble()
            val op = match.groupValues[2]
            val b  = match.groupValues[3].toDouble()
            val result = when (op) {
                "+"          -> a + b
                "-"          -> a - b
                "*", "x", "×" -> a * b
                "/", "÷"     -> if (b != 0.0) a / b else return "Cannot divide by zero."
                else         -> return "Unknown operator."
            }
            val fmt = if (result == result.toLong().toDouble())
                result.toLong().toString() else String.format("%.2f", result)
            "$a $op $b = $fmt"
        } catch (e: Exception) { "I couldn't calculate that." }
    }

    // ── Qwen3 Conversation ────────────────────────────────────────────────────

    private fun callQwen3(query: String): String {
        val historyCtx = if (history.isNotEmpty()) {
            history.takeLast(3).joinToString("\\n") {
                "User: ${it.userMessage}\\nAgent: ${it.agentResponse}"
            } + "\\n"
        } else ""

        val systemMsg = "You are Agentic, a friendly Android AI assistant. " +
            "Answer in 1-3 short sentences. Be helpful and conversational. $historyCtx"

        val escapedSystem = systemMsg
            .replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedQuery  = query
            .replace("\\", "\\\\").replace("\"", "\\\"")

        val body = """{"model":"qwen3:1.7b","keep_alive":-1,"messages":[{"role":"system","content":"$escapedSystem"},{"role":"user","content":"$escapedQuery"}],"stream":false,"think":false,"options":{"temperature":0.7,"num_predict":150}}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // Try each candidate URL
        for (baseUrl in CANDIDATE_URLS) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/chat")
                    .post(body)
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue
                val json    = JSONObject(resp.body?.string() ?: continue)
                val content = json.optJSONObject("message")?.optString("content", "") ?: continue
                val clean   = content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
                if (clean.isNotBlank()) return clean
            } catch (_: Exception) { continue }
        }
        return "I'm not sure about that. Can you rephrase?"
    }

    // ── History ───────────────────────────────────────────────────────────────

    private fun addToHistory(user: String, agent: String) {
        if (history.size >= 5) history.removeFirst()
        history.addLast(ConversationTurn(user, agent, System.currentTimeMillis()))
    }

    fun clearHistory() = history.clear()
    fun getHistory(): List<ConversationTurn> = history.toList()
}

data class ConversationTurn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Long
)
