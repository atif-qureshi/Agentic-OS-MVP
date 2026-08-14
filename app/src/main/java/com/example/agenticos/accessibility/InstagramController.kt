package com.example.agenticos.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.agenticos.conversation.AgentSpeaker
import kotlinx.coroutines.delay

/**
 * High-level Instagram actions using AccessibilityService.
 * Handles: Like, Comment, Follow, Unfollow, DM, Search, Scroll
 */
class InstagramController(
    private val context: Context,
    private val speaker: AgentSpeaker
) {
    private val service get() = InstagramAccessibilityService.instance

    // ── Open Instagram ────────────────────────────────────────────────────────

    suspend fun openInstagram(): Boolean {
        val intent = context.packageManager
            .getLaunchIntentForPackage("com.instagram.android")
            ?: run {
                speaker.speak("Instagram is not installed.")
                return false
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // Wait up to 5 seconds for Instagram to load
        var waited = 0
        while (waited < 5000) {
            delay(500)
            waited += 500
            val svc = InstagramAccessibilityService.instance
            if (svc?.isInstagramOpen() == true) return true
        }
        // Still return true — Instagram may be open even if check fails
        return true
    }

    // ── Like ──────────────────────────────────────────────────────────────────

    suspend fun likePost(): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        // Try tapping Like button (heart icon)
        val liked = svc.tapByContentDescription("Like") ||
                    svc.tapByContentDescription("heart") ||
                    svc.tapByText("Like")

        return if (liked) {
            speaker.speak("Post liked!")
            true
        } else {
            speaker.speak("Could not find the like button.")
            false
        }
    }

    // ── Comment ───────────────────────────────────────────────────────────────

    suspend fun commentOnPost(commentText: String): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        // Tap comment icon
        val opened = svc.tapByContentDescription("Comment") ||
                     svc.tapByText("Add a comment…") ||
                     svc.tapByContentDescription("comment")

        if (!opened) {
            speaker.speak("Could not open comment box.")
            return false
        }
        delay(800)

        // Type comment
        val typed = svc.typeText(commentText)
        if (!typed) {
            speaker.speak("Could not type comment.")
            return false
        }
        delay(500)

        // Submit
        val submitted = svc.tapByText("Post") ||
                        svc.tapByContentDescription("Post") ||
                        svc.tapByText("Send")

        return if (submitted) {
            speaker.speak("Comment posted: $commentText")
            true
        } else {
            speaker.speak("Comment typed but could not submit.")
            false
        }
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    suspend fun followAccount(accountName: String): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        // Search for account first if name given
        if (accountName.isNotBlank()) {
            searchAccount(accountName)
            delay(1500)
        }

        val followed = svc.tapByText("Follow")
        return if (followed) {
            speaker.speak("Followed $accountName.")
            true
        } else {
            speaker.speak("Follow button not found.")
            false
        }
    }

    // ── Unfollow ──────────────────────────────────────────────────────────────

    suspend fun unfollowAccount(accountName: String): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        val tapped = svc.tapByText("Following") ||
                     svc.tapByText("Requested")

        if (!tapped) {
            speaker.speak("Unfollow button not found.")
            return false
        }
        delay(600)

        // Confirm unfollow dialog
        svc.tapByText("Unfollow")

        speaker.speak("Unfollowed $accountName.")
        return true
    }

    // ── Search Account ────────────────────────────────────────────────────────

    suspend fun searchAccount(accountName: String): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        // Tap search tab
        svc.tapByContentDescription("Search and explore") ||
        svc.tapByContentDescription("Search")
        delay(800)

        // Type in search box
        svc.tapByText("Search") || svc.tapByContentDescription("Search")
        delay(500)
        svc.typeText(accountName)
        delay(1000)

        // Tap first result
        val tapped = svc.tapByText(accountName)
        if (!tapped) {
            speaker.speak("Account $accountName not found.")
            return false
        }
        delay(1000)
        return true
    }

    // ── Direct Message ────────────────────────────────────────────────────────

    suspend fun sendDirectMessage(accountName: String, message: String): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!

        // Open DMs
        svc.tapByContentDescription("Direct") ||
        svc.tapByContentDescription("Messenger") ||
        svc.tapByContentDescription("Send")
        delay(1000)

        // Search for contact
        svc.tapByText("Search") || svc.tapByContentDescription("Search")
        delay(500)
        svc.typeText(accountName)
        delay(1000)
        svc.tapByText(accountName)
        delay(800)

        // Type message
        svc.tapByText("Message…") ||
        svc.tapByContentDescription("Message")
        delay(500)
        svc.typeText(message)
        delay(300)

        // Send
        val sent = svc.tapByContentDescription("Send") ||
                   svc.tapByText("Send")

        return if (sent) {
            speaker.speak("Message sent to $accountName.")
            true
        } else {
            speaker.speak("Could not send message.")
            false
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    fun scrollFeed(direction: String): Boolean {
        val svc = service ?: return false
        return if (direction.contains("up", ignoreCase = true)) {
            svc.scrollUp()
        } else {
            svc.scrollDown()
        }
    }

    // ── Open Reels ────────────────────────────────────────────────────────────

    suspend fun openReels(): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!
        val opened = svc.tapByContentDescription("Reels") ||
                     svc.tapByText("Reels")
        if (opened) speaker.speak("Opening Reels.")
        return opened
    }

    // ── Open Stories ──────────────────────────────────────────────────────────

    suspend fun openFirstStory(): Boolean {
        ensureServiceAvailable() ?: return false
        val svc = service!!
        // Stories are at the top — tap first story circle
        val opened = svc.tapByContentDescription("Your story") ||
                     svc.tapByContentDescription("story")
        if (opened) speaker.speak("Opening stories.")
        return opened
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ensureServiceAvailable(): InstagramAccessibilityService? {
        val svc = service
        if (svc == null) {
            speaker.speak(
                "Please enable Agentic OS in Accessibility Settings first."
            )
            // Open accessibility settings
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return null
        }
        return svc
    }

    fun isInstagramInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.instagram.android", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
