package com.example.agenticos.executor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.agenticos.model.DecisionResult

/**
 * Executes Android Intents based on DecisionEngine output.
 *
 * Handles:
 *  - OPEN_APP   → launches app by name
 *  - CALL_CONTACT → initiates phone call (requests permission)
 *  - OPEN_URL   → opens URL in browser
 *  - OPEN_SETTINGS → opens Android Settings
 *  - SEARCH     → opens Google search
 *  - GET_TIME   → shows clock app
 *  - GO_BACK    → goes back
 */
class AndroidIntentExecutor(private val context: Context) {

    /**
     * Executes the intent based on DecisionResult.
     * Returns an [ExecutionResult] describing what happened.
     */
    fun execute(decision: DecisionResult): ExecutionResult {
        return when (decision.intent) {
            "OPEN_APP"       -> openApp(decision.entities["app"] ?: "")
            "CALL_CONTACT"   -> callContact(decision.entities["contact"] ?: "")
            "OPEN_URL"       -> openUrl(decision.entities["url"] ?: "")
            "OPEN_SETTINGS"  -> openSettings()
            "SEARCH"         -> searchGoogle(decision.entities["query"] ?: "")
            "GET_TIME"       -> openClock()
            "TAKE_SCREENSHOT"-> ExecutionResult.NotSupported("Screenshot requires accessibility service")
            "GO_BACK"        -> ExecutionResult.NotSupported("Go back not applicable here")
            "UNKNOWN"        -> ExecutionResult.NotSupported("Command not recognized")
            else             -> ExecutionResult.NotSupported("Intent '${decision.intent}' not implemented")
        }
    }

    // ── App Launcher ──────────────────────────────────────────────────────────

    private fun openApp(appName: String): ExecutionResult {
        if (appName.isBlank()) return ExecutionResult.Failure("No app name provided")

        val packageName = resolvePackageName(appName)

        if (packageName != null) {
            // Method 1: getLaunchIntentForPackage — works on all Android versions
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                return try {
                    context.startActivity(launchIntent)
                    ExecutionResult.Success("Opening $appName")
                } catch (e: Exception) {
                    openPlayStore(packageName)
                }
            }
            // App not installed
            return openPlayStore(packageName)
        }

        // Unknown app name
        return openPlayStoreSearch(appName)
    }

    private fun resolvePackageName(appName: String): String? {
        val name = appName.lowercase().trim()
        return when {
            name.contains("youtube")       -> "com.google.android.youtube"
            name.contains("maps") ||
            name.contains("google maps")   -> "com.google.android.apps.maps"
            name.contains("gmail")         -> "com.google.android.gm"
            name.contains("chrome")        -> "com.android.chrome"
            name.contains("whatsapp")      -> "com.whatsapp"
            name.contains("instagram")     -> "com.instagram.android"
            name.contains("facebook")      -> "com.facebook.katana"
            name.contains("twitter") ||
            name.contains("x")             -> "com.twitter.android"
            name.contains("spotify")       -> "com.spotify.music"
            name.contains("netflix")       -> "com.netflix.mediaclient"
            name.contains("camera")        -> "android.media.action.IMAGE_CAPTURE"
            name.contains("settings")      -> "com.android.settings"
            name.contains("calculator")    -> "com.google.android.calculator"
            name.contains("calendar")      -> "com.google.android.calendar"
            name.contains("clock")         -> "com.google.android.deskclock"
            name.contains("photos")        -> "com.google.android.apps.photos"
            name.contains("drive")         -> "com.google.android.apps.docs"
            name.contains("meet")          -> "com.google.android.apps.meetings"
            name.contains("zoom")          -> "us.zoom.videomeetings"
            name.contains("telegram")      -> "org.telegram.messenger"
            name.contains("tiktok")        -> "com.zhiliaoapp.musically"
            name.contains("snapchat")      -> "com.snapchat.android"
            else                           -> null
        }
    }

    private fun openPlayStore(packageName: String): ExecutionResult {
        return try {
            // Open specific app page on Play Store
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionResult.Success("App not installed. Opening Play Store to install it.")
        } catch (e: ActivityNotFoundException) {
            // Play Store not available — open in browser
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionResult.Success("Opening Play Store in browser.")
        }
    }

    private fun openPlayStoreSearch(appName: String): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=${Uri.encode(appName)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionResult.Success("Searching Play Store for: $appName")
        } catch (e: ActivityNotFoundException) {
            ExecutionResult.Failure("Play Store not available")
        }
    }

    // ── Phone Call ────────────────────────────────────────────────────────────

    fun callContact(contactName: String): ExecutionResult {
        if (contactName.isBlank()) return ExecutionResult.Failure("No contact name provided")

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasPermission) {
            // Search contacts by name and open dialer
            val intent = Intent(Intent.ACTION_DIAL).apply {
                // Uri.encode so names with spaces work e.g. "Ali Khan"
                data = Uri.parse("tel:${Uri.encode(contactName)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opening dialer for: $contactName")
        } else {
            ExecutionResult.NeedsPermission(
                permission = Manifest.permission.CALL_PHONE,
                reason = "Phone permission needed to call $contactName"
            )
        }
    }

    // ── URL / Browser ─────────────────────────────────────────────────────────

    private fun openUrl(url: String): ExecutionResult {
        if (url.isBlank()) return ExecutionResult.Failure("No URL provided")
        val fullUrl = if (url.startsWith("http")) url else "https://$url"
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionResult.Success("Opening: $fullUrl")
        } catch (e: Exception) {
            ExecutionResult.Failure("Cannot open URL: ${e.message}")
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private fun openSettings(): ExecutionResult {
        val intent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ExecutionResult.Success("Opening Settings")
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun searchGoogle(query: String): ExecutionResult {
        if (query.isBlank()) return ExecutionResult.Failure("No search query provided")
        return try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ExecutionResult.Success("Searching: $query")
        } catch (e: Exception) {
            ExecutionResult.Failure("Search failed: ${e.message}")
        }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun openClock(): ExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName("com.google.android.deskclock",
                    "com.android.deskclock.DeskClock")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opening Clock")
        } catch (e: Exception) {
            ExecutionResult.Failure("Clock app not found")
        }
    }
}

// ── Result Sealed Class ───────────────────────────────────────────────────────

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
    data class NeedsPermission(val permission: String, val reason: String) : ExecutionResult()
    data class NotSupported(val message: String) : ExecutionResult()
}
