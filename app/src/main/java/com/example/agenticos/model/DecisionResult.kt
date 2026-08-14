package com.example.agenticos.model

/**
 * Execution type decided by the DecisionEngine.
 */
enum class ExecutionType {
    ANDROID_INTENT,
    GEMINI,
    CONVERSATION,
    INSTAGRAM,
    UNKNOWN
}

/**
 * Output of the DecisionEngine containing execution type
 * and optional Gemini action steps for difficult commands.
 */
data class DecisionResult(
    val executionType: ExecutionType,
    val intent: String,
    val entities: Map<String, String>,
    val actionSteps: List<ActionStep> = emptyList()
)
