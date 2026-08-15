package com.example.agenticos.controller

import com.example.agenticos.model.Command
import com.example.agenticos.model.DecisionResult
import com.example.agenticos.model.ExecutionType
import com.example.agenticos.repository.CommandRepository
import com.example.agenticos.repository.RepositoryResult

/**
 * MVC — Controller
 *
 * Acts as the mediator between the View (MainActivity / UI) and
 * the Model layer (Repository, DecisionEngine, OllamaClient, GeminiClient).
 *
 * Responsibilities:
 *  - Receive user input from the View
 *  - Validate input
 *  - Delegate processing to the Repository (Model)
 *  - Map results back to the View via the [CommandControllerCallback] interface
 *
 * The Controller has NO reference to Android Views/Context, keeping it
 * testable and lifecycle-independent.
 */
class CommandController(
    private val repository: CommandRepository,
    private val callback: CommandControllerCallback
) {

    // ── Input Handling ────────────────────────────────────────────────────────

    /**
     * Entry point called by the View when the user submits a command.
     * Validates input, then delegates to the Model.
     */
    suspend fun onCommandSubmitted(rawInput: String) {
        val trimmed = rawInput.trim()

        if (trimmed.isBlank()) {
            callback.onValidationError("Command cannot be empty. Please type something.")
            return
        }

        if (trimmed.length < 2) {
            callback.onValidationError("Command is too short. Please be more descriptive.")
            return
        }

        callback.onProcessingStarted()

        val result = repository.processCommand(Command(trimmed))

        when (result) {
            is RepositoryResult.Success -> handleSuccess(result.decision)
            is RepositoryResult.Error   -> callback.onError(result.message)
        }
    }

    /**
     * Called when user taps the connection check button.
     * Checks Python Backend (Groq) availability.
     */
    suspend fun onCheckConnectionRequested() {
        callback.onConnectionChecking()
        val isConnected = repository.checkBackendConnection()
        if (isConnected) {
            callback.onConnectionSuccess()
        } else {
            callback.onConnectionFailed(
                "Python Backend not available.\n" +
                "Make sure FastAPI server is running on port 8000."
            )
        }
    }

    /**
     * Called when user taps Clear / Reset.
     */
    fun onResetRequested() {
        callback.onReset()
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun handleSuccess(decision: DecisionResult) {
        when (decision.executionType) {
            ExecutionType.ANDROID_INTENT -> callback.onSimpleCommandReady(decision)
            ExecutionType.GEMINI         -> callback.onDifficultCommandReady(decision)
            ExecutionType.CONVERSATION   -> callback.onConversationReady(decision)
            ExecutionType.INSTAGRAM      -> callback.onInstagramCommandReady(decision)
            ExecutionType.UNKNOWN        -> callback.onUnknownCommand(decision)
        }
    }
}

// ── Callback Interface ────────────────────────────────────────────────────────

/**
 * Interface the View implements to receive Controller events.
 * Keeps the Controller decoupled from any Android UI class.
 */
interface CommandControllerCallback {

    /** Input failed validation before being sent to the model. */
    fun onValidationError(message: String)

    /** Model processing has started — show loading indicator. */
    fun onProcessingStarted()

    /** Simple command resolved — execute via Android Intent. */
    fun onSimpleCommandReady(decision: DecisionResult)

    /** Difficult command resolved — display Gemini action steps. */
    fun onDifficultCommandReady(decision: DecisionResult)

    /** Conversation — agent speaks back the answer. */
    fun onConversationReady(decision: DecisionResult)

    /** Instagram command — accessibility service executes it. */
    fun onInstagramCommandReady(decision: DecisionResult)

    /** Command intent is UNKNOWN — display friendly message. */
    fun onUnknownCommand(decision: DecisionResult)

    /** General processing error. */
    fun onError(message: String)

    /** UI should return to its default idle state. */
    fun onReset()

    /** Connection check started. */
    fun onConnectionChecking()

    /** Ollama is reachable. */
    fun onConnectionSuccess()

    /** Ollama is unreachable — message explains why. */
    fun onConnectionFailed(reason: String)
}
