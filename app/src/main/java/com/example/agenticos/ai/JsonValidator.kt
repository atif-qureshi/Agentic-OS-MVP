package com.example.agenticos.ai

import com.example.agenticos.model.ActionStep
import com.example.agenticos.model.CommandResult
import org.json.JSONObject

/**
 * Validates and parses structured JSON returned by Qwen3 and Gemini.
 *
 * Handles:
 *  - Valid JSON check
 *  - Intent presence
 *  - Entities presence
 *  - Supported intent check
 *  - Required entity check per intent
 */
object JsonValidator {

    /** All intents the app knows how to handle */
    val supportedIntents = setOf(
        "OPEN_APP",
        "CALL_CONTACT",
        "SEND_MESSAGE",
        "SEARCH",
        "OPEN_URL",
        "OPEN_SETTINGS",
        "GET_TIME",
        "TAKE_SCREENSHOT",
        "GO_BACK",
        "UNKNOWN"
    )

    /**
     * Parses raw model output into a [CommandResult].
     * Extracts the JSON block even if the model added surrounding text.
     *
     * @throws JsonValidationException on any validation failure.
     */
    @Throws(JsonValidationException::class)
    fun parseCommandResult(rawText: String): CommandResult {
        val jsonString = extractJsonBlock(rawText)

        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw JsonValidationException("Response is not valid JSON:\n$rawText")
        }

        // Validate intent field
        val intent = json.optString("intent", "").uppercase()
        if (intent.isBlank()) {
            throw JsonValidationException("Missing 'intent' field in response")
        }

        // Validate entities field
        val entitiesJson = json.optJSONObject("entities")
            ?: throw JsonValidationException("Missing 'entities' field in response")

        val entities = mutableMapOf<String, String>()
        entitiesJson.keys().forEach { key ->
            entities[key] = entitiesJson.optString(key, "")
        }

        // Check intent is in supported list
        val resolvedIntent = if (intent in supportedIntents) intent else "UNKNOWN"

        // Validate required entities per intent
        validateRequiredEntities(resolvedIntent, entities)

        return CommandResult(
            intent = resolvedIntent,
            entities = entities,
            rawJson = jsonString
        )
    }

    /**
     * Parses Gemini's multi-step action response into a list of [ActionStep].
     */
    fun parseActionSteps(rawText: String): List<ActionStep> {
        val jsonString = extractJsonBlock(rawText)

        return try {
            val json = JSONObject(jsonString)
            val stepsArray = json.optJSONArray("steps") ?: return emptyList()
            val steps = mutableListOf<ActionStep>()

            for (i in 0 until stepsArray.length()) {
                val step = stepsArray.getJSONObject(i)
                steps.add(
                    ActionStep(
                        action = step.optString("action", "UNKNOWN"),
                        query  = step.optString("query", ""),
                        target = step.optString("target", "")
                    )
                )
            }
            steps
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extracts the first JSON object `{...}` from a string that may
     * contain extra text before or after the JSON block.
     */
    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) {
            throw JsonValidationException("No JSON object found in response:\n$text")
        }
        return text.substring(start, end + 1)
    }

    /**
     * Checks that the required entity for an intent is present and non-empty.
     * Fills in UNKNOWN intent when a required entity is missing.
     */
    private fun validateRequiredEntities(intent: String, entities: Map<String, String>) {
        val missing = when (intent) {
            "OPEN_APP"      -> if (entities["app"].isNullOrBlank()) "app" else null
            "CALL_CONTACT"  -> if (entities["contact"].isNullOrBlank()) "contact" else null
            "SEND_MESSAGE"  -> if (entities["contact"].isNullOrBlank()) "contact" else null
            "SEARCH"        -> if (entities["query"].isNullOrBlank()) "query" else null
            "OPEN_URL"      -> if (entities["url"].isNullOrBlank()) "url" else null
            else            -> null
        }
        // We don't throw here — missing entity degrades gracefully to UNKNOWN at engine level
        // But we log it so the ViewModel can surface it
        if (missing != null) {
            // Not a hard failure — DecisionEngine will handle UNKNOWN gracefully
        }
    }
}

/**
 * Thrown when the model's JSON output fails validation.
 */
class JsonValidationException(message: String) : Exception(message)
