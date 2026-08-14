package com.example.agenticos.model

/**
 * A single structured action step returned by Gemini
 * for difficult commands.
 */
data class ActionStep(
    val action: String,
    val target: String = "",
    val query: String = ""
)
