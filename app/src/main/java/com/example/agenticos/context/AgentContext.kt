package com.example.agenticos.context

/**
 * Lightweight session state used to keep the agent aware of the user's current context.
 * This is especially important for Instagram automation where short phrases like
 * "like", "comment", "follow", or "scroll" depend on the currently active screen.
 */
data class AgentContext(
    val app: String = "Instagram",
    val screen: String = "feed",
    val lastIntent: String? = null,
    val lastAccount: String? = null,
    val recentCommands: List<String> = emptyList(),
    val memorySummary: String = "No personal memory yet.",
    val instagramCapabilities: List<String> = listOf(
        "LIKE_POST",
        "COMMENT_POST",
        "FOLLOW_ACCOUNT",
        "UNFOLLOW_ACCOUNT",
        "SEND_DM",
        "POST_TO_FEED",
        "POST_TO_STORY",
        "POST_REEL",
        "SEARCH_ACCOUNT",
        "SCROLL_FEED"
    )
)

object ContextMemory {
    private var currentContext: AgentContext = AgentContext()

    fun snapshot(): AgentContext = currentContext

    fun update(intent: String, entities: Map<String, String>) {
        if (intent.isBlank()) return

        val nextScreen = when (intent) {
            "INSTAGRAM_LIKE" -> "post_detail"
            "INSTAGRAM_COMMENT" -> "post_detail"
            "INSTAGRAM_FOLLOW", "INSTAGRAM_UNFOLLOW" -> "profile"
            "INSTAGRAM_DM" -> "dm"
            "INSTAGRAM_POST", "INSTAGRAM_STORY", "INSTAGRAM_REEL" -> "composer"
            "INSTAGRAM_SCROLL" -> "feed"
            else -> currentContext.screen
        }

        val account = entities["account"] ?: entities["contact"] ?: currentContext.lastAccount

        currentContext = currentContext.copy(
            app = if (currentContext.app.isBlank()) "Instagram" else currentContext.app,
            screen = nextScreen,
            lastIntent = intent,
            lastAccount = account ?: if (nextScreen == "profile") "CURRENT_PROFILE" else currentContext.lastAccount,
            recentCommands = (listOf(intent) + currentContext.recentCommands).distinct().take(5),
            memorySummary = UserMemory.summary()
        )
    }

    fun isCurrentProfileCommand(command: String): Boolean {
        val lower = command.lowercase()
        return lower.contains("this account") ||
            lower.contains("this profile") ||
            lower.contains("current profile") ||
            lower.contains("active profile") ||
            lower.contains("same profile") ||
            lower.contains("same account") ||
            lower.contains("jis profile") ||
            lower.contains("yhi account") ||
            lower.contains("current account")
    }

    fun enrichCommand(command: String): String {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return trimmed

        val screenDescription = when (currentContext.screen) {
            "post_detail" -> "user is viewing an Instagram post"
            "profile" -> "user is viewing an Instagram profile or account"
            "dm" -> "user is inside Instagram direct messages"
            "composer" -> "user is creating content in Instagram"
            "feed" -> "user is on the Instagram home feed"
            else -> "user is inside Instagram"
        }

        val targetHint = if (isCurrentProfileCommand(trimmed)) {
            "Target account: CURRENT_PROFILE (use the account currently open on screen, do not ask for a name)."
        } else {
            "Target account: use explicit account name when provided."
        }

        val lastAccountText = currentContext.lastAccount?.let { "Last referenced account: $it." } ?: ""
        val historyText = if (currentContext.recentCommands.isEmpty()) {
            "No recent Instagram action history."
        } else {
            "Recent intent history: ${currentContext.recentCommands.joinToString(", ")}."
        }

        val memoryText = currentContext.memorySummary.ifBlank { "No personal memory yet." }

        return """
            User context:
            - Active app: ${currentContext.app}
            - Screen context: $screenDescription
            - Last intent: ${currentContext.lastIntent ?: "none"}
            - $lastAccountText
            - $targetHint
            - $historyText
            - Personal memory: $memoryText
            - Supported Instagram actions: ${currentContext.instagramCapabilities.joinToString(", ")}
            - If the user says a short command like "like", "comment", "follow", "scroll", "dm", "story", or "post" while in Instagram, resolve it against the current screen context before interpreting it as a generic action.
            - Special trigger keywords: "this account", "this profile", "current profile", "active profile", "same profile", "same account", "jis profile", "yhi account" → target the account that is already open on screen.
            Command: $trimmed
        """.trimIndent()
    }
}
