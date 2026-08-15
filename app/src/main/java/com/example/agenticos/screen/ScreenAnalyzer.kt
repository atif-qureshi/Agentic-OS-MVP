package com.example.agenticos.screen

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject

/**
 * Analyzes phone screen using Gemini Vision.
 * Returns exact pixel coordinates of UI elements.
 */
class ScreenAnalyzer {

    companion object {
        private const val TAG = "ScreenAnalyzer"
        
        // Load API key from BuildConfig (set in build.gradle from local.properties)
        fun getApiKey(): String {
            return try {
                Class.forName("com.example.agenticos.BuildConfig")
                    .getDeclaredField("GEMINI_API_KEY")
                    .get(null) as String
            } catch (e: Exception) {
                "" // Empty key if not configured
            }
        }
    }

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey    = getApiKey(),
        generationConfig = generationConfig {
            temperature     = 0.0f
            maxOutputTokens = 300
        }
    )

    /**
     * Find exact pixel coordinates of a UI element.
     * Returns ScreenCoordinate with x, y position.
     *
     * Example: findCoordinates(bitmap, "Like button")
     * Returns: ScreenCoordinate(x=120, y=890, found=true)
     */
    suspend fun findCoordinates(bitmap: Bitmap, elementDescription: String): ScreenCoordinate {
        val w = bitmap.width
        val h = bitmap.height

        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text("""
This is an Android screen screenshot. Image resolution: ${w}x${h} pixels.

Task: Find "$elementDescription" on the screen.

Reply ONLY in this exact JSON format (no other text):
{
  "found": true,
  "x_percent": 15.0,
  "y_percent": 88.0,
  "confidence": 90,
  "description": "heart like button at bottom left of post"
}

Rules:
- x_percent: 0.0 to 100.0 (horizontal position percentage from left edge to center of element)
- y_percent: 0.0 to 100.0 (vertical position percentage from top edge to center of element)
- If element is not visible: {"found": false, "x_percent": 0.0, "y_percent": 0.0, "confidence": 0, "description": "not visible"}
""".trimIndent())
                }
            )

            parseCoordinateResponse(response.text ?: "", w, h)

        } catch (e: Exception) {
            Log.e(TAG, "findCoordinates failed: ${e.message}")
            ScreenCoordinate(found = false, x = 0, y = 0, confidence = 0)
        }
    }

    /**
     * Find multiple elements at once — more efficient.
     * Returns map of element name → coordinates.
     */
    suspend fun findMultipleElements(
        bitmap: Bitmap,
        elements: List<String>
    ): Map<String, ScreenCoordinate> {
        val w = bitmap.width
        val h = bitmap.height

        return try {
            val elementList = elements.joinToString(", ") { "\"$it\"" }
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text("""
This is an Android screenshot. Image size: ${w}x${h} pixels.

Find these UI elements: $elementList

Reply ONLY in this exact JSON format:
{
  "elements": [
    {"name": "Like button", "found": true, "x": 120, "y": 890, "confidence": 90},
    {"name": "Comment button", "found": true, "x": 200, "y": 890, "confidence": 85}
  ]
}

Use actual pixel coordinates. If not found set found=false and x=0, y=0.
""".trimIndent())
                }
            )

            parseMultipleElements(response.text ?: "", elements)

        } catch (e: Exception) {
            Log.e(TAG, "findMultipleElements failed: ${e.message}")
            elements.associateWith { ScreenCoordinate(false, 0, 0, 0) }
        }
    }

    /**
     * Describe what is on screen — for CONVERSATION intent.
     */
    suspend fun describeScreen(bitmap: Bitmap): String {
        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text("Describe this Android screen in 2-3 sentences. What app is open and what is visible?")
                }
            )
            response.text ?: "Could not analyze screen content."
        } catch (e: Exception) {
            Log.e(TAG, "describeScreen failed: ${e.message}", e)
            "Screen analysis failed. Please verify your Gemini API Key in ScreenAnalyzer.kt."
        }
    }

    /**
     * Ask Gemini what action to take and where to tap.
     * Returns ordered list of tap coordinates.
     */
    suspend fun planAction(bitmap: Bitmap, userCommand: String): ActionPlan {
        val w = bitmap.width
        val h = bitmap.height

        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text("""
Android screenshot size: ${w}x${h} pixels.
User wants to: "$userCommand"

What UI elements need to be tapped to accomplish this?
Reply ONLY in this JSON format:
{
  "possible": true,
  "steps": [
    {"description": "Tap Like button", "x": 120, "y": 890},
    {"description": "Confirm action", "x": 300, "y": 500}
  ],
  "message": "I will like this post for you"
}

If not possible: {"possible": false, "steps": [], "message": "reason why"}
Use actual pixel coordinates from the screenshot.
""".trimIndent())
                }
            )

            parseActionPlan(response.text ?: "")

        } catch (e: Exception) {
            ActionPlan(possible = false, steps = emptyList(),
                message = "Screen analysis failed: ${e.message}")
        }
    }

    /**
     * Verify if an action completed successfully.
     */
    suspend fun verifyAction(bitmap: Bitmap, expectedResult: String): Boolean {
        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text("Does this screen show evidence that: \"$expectedResult\" was completed? Reply only YES or NO.")
                }
            )
            response.text?.trim()?.uppercase()?.startsWith("YES") == true
        } catch (e: Exception) {
            false
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun parseCoordinateResponse(text: String, maxW: Int, maxH: Int): ScreenCoordinate {
        return try {
            val start = text.indexOf('{')
            val end   = text.lastIndexOf('}')
            if (start == -1 || end == -1) return ScreenCoordinate(false, 0, 0, 0)

            val json = JSONObject(text.substring(start, end + 1))
            val found = json.optBoolean("found", false)
            val x = json.optInt("x", 0).coerceIn(0, maxW)
            val y = json.optInt("y", 0).coerceIn(0, maxH)
            val conf = if (json.has("confidence")) json.optInt("confidence", 80) else if (found) 80 else 0
            val desc = json.optString("description", "")

            ScreenCoordinate(found, x, y, conf, desc)
        } catch (e: Exception) {
            ScreenCoordinate(false, 0, 0, 0)
        }
    }

    private fun parseMultipleElements(
        text: String,
        elements: List<String>
    ): Map<String, ScreenCoordinate> {
        val result = mutableMapOf<String, ScreenCoordinate>()
        elements.forEach { result[it] = ScreenCoordinate(false, 0, 0, 0) }

        return try {
            val start = text.indexOf('{')
            val end   = text.lastIndexOf('}')
            if (start == -1 || end == -1) return result

            val json  = JSONObject(text.substring(start, end + 1))
            val arr   = json.optJSONArray("elements") ?: return result

            for (i in 0 until arr.length()) {
                val el   = arr.getJSONObject(i)
                val name = el.optString("name", "")
                result[name] = ScreenCoordinate(
                    found       = el.optBoolean("found", false),
                    x           = el.optInt("x", 0),
                    y           = el.optInt("y", 0),
                    confidence  = el.optInt("confidence", 0)
                )
            }
            result
        } catch (e: Exception) {
            result
        }
    }

    private fun parseActionPlan(text: String): ActionPlan {
        return try {
            val start = text.indexOf('{')
            val end   = text.lastIndexOf('}')
            if (start == -1 || end == -1) return ActionPlan(false, emptyList(), "Parse failed")

            val json     = JSONObject(text.substring(start, end + 1))
            val possible = json.optBoolean("possible", false)
            val message  = json.optString("message", "")
            val stepsArr = json.optJSONArray("steps") ?: return ActionPlan(possible, emptyList(), message)

            val steps = mutableListOf<TapStep>()
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.getJSONObject(i)
                steps.add(TapStep(
                    description = s.optString("description", ""),
                    x = s.optInt("x", 0),
                    y = s.optInt("y", 0)
                ))
            }
            ActionPlan(possible, steps, message)
        } catch (e: Exception) {
            ActionPlan(false, emptyList(), "Parse failed: ${e.message}")
        }
    }
}

// ── Data Classes ──────────────────────────────────────────────────────────────

data class ScreenCoordinate(
    val found: Boolean,
    val x: Int,
    val y: Int,
    val confidence: Int,
    val description: String = ""
)

data class TapStep(
    val description: String,
    val x: Int,
    val y: Int
)

data class ActionPlan(
    val possible: Boolean,
    val steps: List<TapStep>,
    val message: String
)
