package com.example.agenticos.context

import java.util.Locale

/**
 * Lightweight in-memory profile for the user.
 * It remembers the commands the user usually issues and their preferred Instagram actions,
 * so future short commands can be interpreted against the user's habits.
 */
data class UserMemoryProfile(
    val recentCommands: MutableList<String> = mutableListOf(),
    val frequentIntents: MutableMap<String, Int> = linkedMapOf(),
    val favoriteAccounts: MutableMap<String, Int> = linkedMapOf(),
    val preferredActions: MutableMap<String, Int> = linkedMapOf()
) {
    fun remember(command: String, intent: String, entities: Map<String, String>) {
        val normalized = command.trim()
        if (normalized.isBlank()) return

        recentCommands.add(normalized)
        while (recentCommands.size > 12) recentCommands.removeAt(0)

        frequentIntents[intent] = (frequentIntents[intent] ?: 0) + 1

        val account = entities["account"] ?: entities["contact"]
        if (!account.isNullOrBlank()) {
            favoriteAccounts[account] = (favoriteAccounts[account] ?: 0) + 1
        }

        val actionKey = when (intent) {
            "INSTAGRAM_LIKE" -> "like"
            "INSTAGRAM_COMMENT" -> "comment"
            "INSTAGRAM_FOLLOW" -> "follow"
            "INSTAGRAM_UNFOLLOW" -> "unfollow"
            "INSTAGRAM_DM" -> "dm"
            "INSTAGRAM_SCROLL" -> "scroll"
            "INSTAGRAM_POST" -> "post"
            "INSTAGRAM_STORY" -> "story"
            "INSTAGRAM_REEL" -> "reel"
            else -> intent.lowercase(Locale.getDefault())
        }
        preferredActions[actionKey] = (preferredActions[actionKey] ?: 0) + 1
    }

    fun summary(): String {
        if (recentCommands.isEmpty()) return "No personal memory yet."

        val topIntents = frequentIntents.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString { "${it.key}(${it.value})" }

        val topAccounts = favoriteAccounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString { "${it.key}(${it.value})" }

        val topActions = preferredActions.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString { "${it.key}(${it.value})" }

        return "User memory: recent commands=${recentCommands.takeLast(4).joinToString(" | ")}; top intents=$topIntents; favorite accounts=$topAccounts; preferred actions=$topActions."
    }
}

object UserMemory {
    private val profile = UserMemoryProfile()

    fun remember(command: String, intent: String, entities: Map<String, String>) {
        profile.remember(command, intent, entities)
    }

    fun summary(): String = profile.summary()

    fun snapshot(): UserMemoryProfile = profile
}
