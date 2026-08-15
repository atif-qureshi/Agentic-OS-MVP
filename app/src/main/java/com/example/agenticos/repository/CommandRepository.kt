package com.example.agenticos.repository

import com.example.agenticos.ai.GeminiClient
import com.example.agenticos.ai.GeminiException
import com.example.agenticos.ai.JsonValidationException
import com.example.agenticos.ai.OllamaClient
import com.example.agenticos.backend.PythonBackendClient
import com.example.agenticos.context.ContextMemory
import com.example.agenticos.context.UserMemory
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

    private val pythonBackendClient = PythonBackendClient()
    private val ollamaClient        = OllamaClient()
    private val geminiClient        = GeminiClient()
    private val conversationEngine  = ConversationEngine()

    suspend fun processCommand(command: Command): RepositoryResult {
        if (command.text.isBlank()) {
            return RepositoryResult.Error("Command cannot be empty.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val contextAwareCommand = ContextMemory.enrichCommand(command.text)
                val backendResult = if (pythonBackendClient.isAvailable()) {
                    pythonBackendClient.extractIntent(command.text, ContextMemory.snapshot())
                } else {
                    null
                }

                val commandResult = backendResult
                    ?: tryFastLocalMatch(command.text)
                    ?: com.example.agenticos.model.CommandResult("UNKNOWN", emptyMap())

                ContextMemory.update(commandResult.intent, commandResult.entities)
                UserMemory.remember(command.text, commandResult.intent, commandResult.entities)

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

            } catch  (e: JsonValidationException) { RepositoryResult.Error("JSON error: ${e.message}") }
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

        // 3a. COMMON ROMAN-URDU COMMENT PATTERNS
        if (lower.contains("comment") || lower.contains("comment karo") || lower.contains("comment likho") || lower.contains("comment kar do")) {
            val textToComment = lower
                .replace("comment karo", "")
                .replace("comment kar do", "")
                .replace("comment likho", "")
                .replace("comment", "")
                .replace("karo", "")
                .replace("kar do", "")
                .trim()
            return com.example.agenticos.model.CommandResult("INSTAGRAM_COMMENT", mapOf("text" to textToComment.ifBlank { "Nice photo!" }))
        }

        // 4. INSTAGRAM FOLLOW / UNFOLLOW on current profile or screen account
        val currentProfileTrigger = lower.contains("this account") || lower.contains("this profile") ||
            lower.contains("current profile") || lower.contains("active profile") || lower.contains("same profile") ||
            lower.contains("same account") || lower.contains("jis profile") || lower.contains("yhi account") ||
            lower.contains("current account")

        if (lower.contains("unfollow")) {
            val account = lower.substringAfter("unfollow", "").replace("karo", "").trim()
            return if (currentProfileTrigger || account.isBlank()) {
                com.example.agenticos.model.CommandResult("INSTAGRAM_UNFOLLOW", emptyMap())
            } else {
                com.example.agenticos.model.CommandResult("INSTAGRAM_UNFOLLOW", mapOf("account" to account))
            }
        }
        if (lower.contains("follow")) {
            val account = lower.substringAfter("follow", "").replace("karo", "").replace("is ko", "").trim()
            return if (currentProfileTrigger || account.isBlank()) {
                com.example.agenticos.model.CommandResult("INSTAGRAM_FOLLOW", emptyMap())
            } else {
                com.example.agenticos.model.CommandResult("INSTAGRAM_FOLLOW", mapOf("account" to account))
            }
        }

        // 4a. COMMON ROMAN-URDU FOLLOW / UNFOLLOW PATTERNS
        if (lower.contains("follow") || lower.contains("follow karo") || lower.contains("follow kro") || lower.contains("follow kr do")) {
            val account = lower
                .replace("follow karo", "")
                .replace("follow kro", "")
                .replace("follow kr do", "")
                .replace("follow", "")
                .replace("karo", "")
                .replace("kro", "")
                .replace("kr do", "")
                .trim()
            return if (currentProfileTrigger || account.isBlank()) {
                com.example.agenticos.model.CommandResult("INSTAGRAM_FOLLOW", emptyMap())
            } else {
                com.example.agenticos.model.CommandResult("INSTAGRAM_FOLLOW", mapOf("account" to account))
            }
        }
        if (lower.contains("unfollow") || lower.contains("unfollow karo") || lower.contains("unfollow kro") || lower.contains("unfollow kr do")) {
            val account = lower
                .replace("unfollow karo", "")
                .replace("unfollow kro", "")
                .replace("unfollow kr do", "")
                .replace("unfollow", "")
                .replace("karo", "")
                .replace("kro", "")
                .replace("kr do", "")
                .trim()
            return if (currentProfileTrigger || account.isBlank()) {
                com.example.agenticos.model.CommandResult("INSTAGRAM_UNFOLLOW", emptyMap())
            } else {
                com.example.agenticos.model.CommandResult("INSTAGRAM_UNFOLLOW", mapOf("account" to account))
            }
        }

        // 5. INSTAGRAM SCROLL
        if (lower.contains("scroll") || lower.contains("neeche") || lower.contains("upar")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
            return com.example.agenticos.model.CommandResult("INSTAGRAM_SCROLL", mapOf("direction" to dir))
        }

        // 5a. ROMAN-URDU scroll patterns
        if (lower.contains("neeche") || lower.contains("upar") || lower.contains("feed scroll") || lower.contains("scroll down") || lower.contains("scroll up")) {
            val dir = if (lower.contains("up") || lower.contains("upar")) "up" else "down"
            return com.example.agenticos.model.CommandResult("INSTAGRAM_SCROLL", mapOf("direction" to dir))
        }

        // 6. DM / MESSAGE PATTERNS
        if (lower.contains("message") || lower.contains("dm ") || lower.contains("dm") || lower.contains("msg")) {
            val account = lower.substringAfter("to", "").substringBefore(" ")
            val message = lower.substringAfter("hello", "").ifBlank { "hello" }
            return com.example.agenticos.model.CommandResult("INSTAGRAM_DM", mapOf("account" to account.ifBlank { "ali" }, "message" to message))
        }

        // 6a. Reel + profile + search daily patterns
        if (lower.contains("watch") && lower.contains("reel")) {
            return com.example.agenticos.model.CommandResult("INSTAGRAM_REEL", emptyMap())
        }
        if (lower.contains("profile") || lower.contains("meri profile") || lower.contains("my profile")) {
            return com.example.agenticos.model.CommandResult("INSTAGRAM_OPEN_PROFILE", emptyMap())
        }
        if (lower.contains("search") && (lower.contains("user") || lower.contains("hashtag") || lower.contains("post") || lower.contains("account"))) {
            return com.example.agenticos.model.CommandResult("INSTAGRAM_SEARCH", emptyMap())
        }

        return null
    }

    suspend fun checkBackendConnection(): Boolean = withContext(Dispatchers.IO) {
        pythonBackendClient.isAvailable()
    }
}

/**
 * Sealed class representing the outcome of [CommandRepository.processCommand].
 */
sealed class RepositoryResult {
    data class Success(val decision: DecisionResult) : RepositoryResult()
    data class Error(val message: String) : RepositoryResult()
}
