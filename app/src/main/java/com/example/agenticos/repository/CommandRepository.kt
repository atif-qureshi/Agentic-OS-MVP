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
                val commandResult = ollamaClient.extractIntent(command.text)
                val decision      = DecisionEngine.decide(commandResult)

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

                    // INSTAGRAM and ANDROID_INTENT are handled by executor in UI layer
                    else -> decision
                }

                RepositoryResult.Success(finalDecision)

            } catch (e: OllamaException)        { RepositoryResult.Error("Ollama error: ${e.message}") }
            catch  (e: JsonValidationException) { RepositoryResult.Error("JSON error: ${e.message}") }
            catch  (e: GeminiException)         { RepositoryResult.Error("Gemini error: ${e.message}") }
            catch  (e: Exception)               { RepositoryResult.Error("Error: ${e.message}") }
        }
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
