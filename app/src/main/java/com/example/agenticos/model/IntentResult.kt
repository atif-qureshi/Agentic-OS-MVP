package com.example.agenticos.model

/**
 * Parsed result from Qwen3 — intent + extracted entities.
 */
data class IntentResult(
    val intent: String,
    val entities: Map<String, String>
)
