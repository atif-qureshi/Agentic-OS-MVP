package com.example.agenticos.model

/**
 * Combined result after intent extraction and JSON validation.
 */
data class CommandResult(
    val intent: String,
    val entities: Map<String, String>,
    val rawJson: String = ""
)
