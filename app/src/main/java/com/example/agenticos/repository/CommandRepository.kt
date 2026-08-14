package com.example.agenticos.repository

import com.example.agenticos.ai.GeminiClient
import com.example.agenticos.ai.GeminiException
import com.example.agenticos.ai.JsonValidationException
import com.example.agenticos.ai.OllamaClient
import com.example.agenticos.ai.OllamaException
import com.example.agenticos.conversation.ConversationEngine
import com.example.agenticos.decision.DecisionEngine
import com.example.agenticos.model.Command
import com.example.agenticos.model.DecisionResult
import com.example.agenticos.model.ExecutionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for all AI processing.
 *
 * Flow:
 *   UI → ViewModel → CommandRepository
 *                        ↓
 *                   OllamaClient → Qwen3
 *                        ↓
 *                   Intent + Entities
 *                        ↓
 *                   JSON Validation
 *                        ↓
 *                   DecisionEngine
 *                        ↓
 *              SIMPLE          DIFFICULT
 *                ↓                  ↓
 *         Android Intent          Gemini
 *                                   ↓
 *                           Structured Action Steps
 */
class CommandRepository {

    private val ollamaClient       = OllamaClient()
    private val geminiClient       = GeminiClient()
    private val conversationEngine = ConversationEngine()

    suspend fun processCommand(command: Command): RepositoryResult {
        if (command.text.isBlank()) {
            return RepositoryResult.Error("Command cannot be empty.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val commandResult = tryFastLocalMatch(command.text)
                    ?: ollamaClient.extractIntent(command.text)

                val decision = DecisionEngine.decide(commandResult)

                val finalDecision = when (decision.executionType) {

                    ExecutionType.CONVERSATION -> {
                        val query    = decision.entities["query"] ?: command.text
                        val response = conversationEngine.respond(query)
                        decision.copy(
                            actionSteps = listOf(
                                com.example.agenticos.model.ActionStep(
                                    action = "SPEAK",
                                    query  = response
                                )
                            )
                        )
                    }

                    ExecutionType.GEMINI -> {
                        if (!geminiClient.isConfigured()) {
                            decision.copy(
                                actionSteps = listOf(
                                    com.example.agenticos.model.ActionStep(
                                        action = "WARNING",
                                        query  = "Gemini API key not configured."
                                    )
                                )
                            )
                        } else {
                            val steps = geminiClient.getActionSteps(
                                command.text, decision.intent, decision.entities
                            )
                            decision.copy(actionSteps = steps)
                        }
                    }

                    else -> decision
                }

                RepositoryResult.Success(finalDecision)

            } catch (e: OllamaException)        { RepositoryResult.Error("Ollama error: ${e.message}") }
            catch  (e: JsonValidationException) { RepositoryResult.Error("JSON error: ${e.message}") }
            catch  (e: GeminiException)         { RepositoryResult.Error("Gemini error: ${e.message}") }
            catch  (e: Exception)               { RepositoryResult.Error("Error: ${e.message}") }
        }
    }

    private fun tryFastLocalMatch(text: String): com.example.agenticos.model.CommandResult? {
        val lower = text.lowercase().trim()

        // 1. INSTAGRAM LIKE variations
        val isLikeKeyword = lower.contains("like") || lower.contains("heart") ||
                lower.contains("pasand") || lower.contains("dil de") || lower.contains("dil do") || lower.contains("pasaand")
        if (isLikeKeyword && (lower.contains("post") || lower.contains("pic") || lower.contains("photo") ||
                lower.contains("image") || lower.contains("this") || lower.contains("is") ||
                lower.contains("karo") || lower.contains("do") || lower == "like" || lower == "like post" || lower == "like this")) {
            return com.example.agenticos.model.CommandResult("INSTAGRAM_LIKE", emptyMap())
        }

        // 2. OPEN INSTAGRAM / APPS
        if ((lower.contains("open") || lower.contains("kholo") || lower.contains("launch") || lower.contains("start")) &&
            (lower.contains("instagram") || lower.contains("insta"))) {
            return com.example.agenticos.model.CommandResult("OPEN_APP", mapOf("app" to "Instagram"))
        }
        if (lower == "insta" || lower == "instagram") {
            return com.example.agenticos.model.CommandResult("OPEN_APP", mapOf("app" to "Instagram"))
        }

        // 3. INSTAGRAM COMMENT
        if (lower.contains("comment")) {
            val textToComment = lower.substringAfter("comment", "").replace("on post", "").replace("karo", "").trim()
            return com.example.agenticos.model.CommandResult("INSTAGRAM_COMMENT", mapOf("text" to textToComment.ifBlank { "Nice photo!" }))
        }

        // 4. INSTAGRAM FOLLOW / UNFOLLOW
        if (lower.contains("unfollow")) {
            val account = lower.substringAfter("unfollow", "").replace("karo", "").trim()
            return com.example.agenticos.model.CommandResult("INSTAGRAM_UNFOLLOW", mapOf("account" to account))
        }
        if (lower.contains("follow")) {
            val account = lower.substringAfter("follow", "").replace("karo", "").replace("is ko", "").trim()
            return com.example.agenticos.model.CommandResult("INSTAGRAM_FOLLOW", mapOf("account" to account))
        }

        // 5. INSTAGRAM SCROLL
        if (lower.contains("scroll") || lower.contains("neeche") || lower.contains("upar")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
            return com.example.agenticos.model.CommandResult("INSTAGRAM_SCROLL", mapOf("direction" to dir))
        }

        return null
    }

    suspend fun checkOllamaConnection(): Boolean = withContext(Dispatchers.IO) {
        ollamaClient.isAvailable()
    }
}

/**
 * Sealed class representing the outcome of [CommandRepository.processCommand].
 */
sealed class RepositoryResult {
    data class Success(val decision: DecisionResult) : RepositoryResult()
    data class Error(val message: String) : RepositoryResult()
}
