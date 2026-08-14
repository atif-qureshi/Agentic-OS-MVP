package com.example.agenticos.decision

import com.example.agenticos.model.CommandResult
import com.example.agenticos.model.DecisionResult
import com.example.agenticos.model.ExecutionType

/**
 * Decides whether a command is SIMPLE (handled by Android Intent)
 * or DIFFICULT (sent to Gemini for multi-step planning).
 *
 * SIMPLE intents are direct one-shot actions.
 * DIFFICULT intents require reasoning and multiple steps.
 */
object DecisionEngine {

    private val simpleIntents = setOf(
        "OPEN_APP", "CALL_CONTACT", "OPEN_SETTINGS",
        "GET_TIME", "TAKE_SCREENSHOT", "GO_BACK", "OPEN_URL"
    )

    private val difficultIntents = setOf(
        "SEARCH", "SEND_MESSAGE"
    )

    private val conversationIntents = setOf(
        "CONVERSATION"
    )

    private val instagramIntents = setOf(
        "INSTAGRAM_LIKE", "INSTAGRAM_COMMENT", "INSTAGRAM_FOLLOW",
        "INSTAGRAM_UNFOLLOW", "INSTAGRAM_DM", "INSTAGRAM_SEARCH",
        "INSTAGRAM_SCROLL", "INSTAGRAM_POST", "INSTAGRAM_STORY",
        "INSTAGRAM_REEL", "INSTAGRAM_REELS", "INSTAGRAM_OPEN_PROFILE"
    )

    fun decide(commandResult: CommandResult): DecisionResult {
        val intent = commandResult.intent

        return when {
            intent == "UNKNOWN" -> DecisionResult(
                executionType = ExecutionType.UNKNOWN,
                intent = intent, entities = commandResult.entities
            )
            intent in conversationIntents -> DecisionResult(
                executionType = ExecutionType.CONVERSATION,
                intent = intent, entities = commandResult.entities
            )
            intent in instagramIntents -> DecisionResult(
                executionType = ExecutionType.INSTAGRAM,
                intent = intent, entities = commandResult.entities
            )
            intent in simpleIntents -> DecisionResult(
                executionType = ExecutionType.ANDROID_INTENT,
                intent = intent, entities = commandResult.entities
            )
            intent in difficultIntents -> DecisionResult(
                executionType = ExecutionType.GEMINI,
                intent = intent, entities = commandResult.entities
            )
            else -> DecisionResult(
                executionType = ExecutionType.GEMINI,
                intent = intent, entities = commandResult.entities
            )
        }
    }

    fun describeDecision(result: DecisionResult): String {
        return when (result.executionType) {
            ExecutionType.ANDROID_INTENT -> "SIMPLE → Execute via Android Intent"
            ExecutionType.GEMINI         -> "DIFFICULT → Send to Gemini for planning"
            ExecutionType.CONVERSATION   -> "CONVERSATION → Agent responds"
            ExecutionType.INSTAGRAM      -> "INSTAGRAM → Accessibility Service"
            ExecutionType.UNKNOWN        -> "UNKNOWN → Command not recognized"
        }
    }
}
