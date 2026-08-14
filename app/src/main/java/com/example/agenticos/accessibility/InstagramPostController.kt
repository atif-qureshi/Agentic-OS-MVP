package com.example.agenticos.accessibility

import android.content.Context
import com.example.agenticos.conversation.AgentSpeaker
import kotlinx.coroutines.delay

/**
 * Handles Instagram Post, Story, and Reel creation
 * using AccessibilityService — no root required.
 *
 * Flow for Post:
 *   + button → POST tab → select photo → Next → Next → caption → Share
 *
 * Flow for Story:
 *   + button → STORY tab → select photo → Send to → Your Story → Share
 *
 * Flow for Reel:
 *   + button → REELS tab → select video → Next → caption → Share
 */
class InstagramPostController(
    private val context: Context,
    private val speaker: AgentSpeaker,
    private val controller: InstagramController
) {
    private val service get() = InstagramAccessibilityService.instance

    // ── Create Post ───────────────────────────────────────────────────────────

    suspend fun createPost(caption: String): Boolean {
        if (!ensureInstagramOpen()) return false
        val svc = service ?: return noService()

        speaker.speak("Creating post. Please wait.")

        // Step 1: Tap + (new post) button
        if (!tapNewPostButton(svc)) {
            speaker.speak("Could not find the new post button.")
            return false
        }
        delay(1200)

        // Step 2: Select POST tab
        svc.tapByText("POST") || svc.tapByText("Post")
        delay(800)

        // Step 3: Select latest photo from gallery
        if (!svc.tapFirstGalleryItem()) {
            speaker.speak("Could not select photo from gallery.")
            return false
        }
        delay(1000)

        // Step 4: Tap Next
        if (!tapNext(svc)) {
            speaker.speak("Could not proceed to next step.")
            return false
        }
        delay(1500)

        // Step 5: Skip filters — tap Next again
        tapNext(svc)
        delay(1500)

        // Step 6: Add caption
        if (caption.isNotBlank()) {
            svc.tapByText("Write a caption…") ||
            svc.tapByText("Write a caption") ||
            svc.tapByContentDescription("caption")
            delay(500)
            svc.typeText(caption)
            delay(500)
        }

        // Step 7: Tap Share
        if (!tapShare(svc)) {
            speaker.speak("Could not find the Share button.")
            return false
        }
        delay(2000)

        speaker.speak(
            if (caption.isNotBlank())
                "Your post has been shared with caption: $caption"
            else
                "Your post has been shared successfully."
        )
        return true
    }

    // ── Create Story ──────────────────────────────────────────────────────────

    suspend fun createStory(): Boolean {
        if (!ensureInstagramOpen()) return false
        val svc = service ?: return noService()

        speaker.speak("Posting to story. Please wait.")

        // Tap + button
        if (!tapNewPostButton(svc)) {
            speaker.speak("Could not find the new post button.")
            return false
        }
        delay(1200)

        // Select STORY tab
        svc.tapByText("STORY") || svc.tapByText("Story")
        delay(800)

        // Select latest photo
        if (!svc.tapFirstGalleryItem()) {
            speaker.speak("Could not select photo.")
            return false
        }
        delay(1000)

        // Tap "Send to"
        svc.tapByText("Send to") ||
        svc.tapByContentDescription("Send to")
        delay(1000)

        // Tap "Your Story"
        svc.tapByText("Your Story") ||
        svc.tapByText("Add to Your Story")
        delay(800)

        // Share
        tapShare(svc)
        delay(1500)

        speaker.speak("Story posted successfully.")
        return true
    }

    // ── Create Reel ───────────────────────────────────────────────────────────

    suspend fun createReel(caption: String): Boolean {
        if (!ensureInstagramOpen()) return false
        val svc = service ?: return noService()

        speaker.speak("Creating reel. Please wait.")

        // Tap + button
        if (!tapNewPostButton(svc)) {
            speaker.speak("Could not find the new post button.")
            return false
        }
        delay(1200)

        // Select REELS tab
        svc.tapByText("REELS") ||
        svc.tapByText("Reels") ||
        svc.tapByContentDescription("Reels")
        delay(800)

        // Select latest video
        if (!svc.tapFirstGalleryItem()) {
            speaker.speak("Could not select video from gallery.")
            return false
        }
        delay(1000)

        // Tap Next
        tapNext(svc)
        delay(1500)

        // Add caption
        if (caption.isNotBlank()) {
            svc.tapByText("Write a caption…") ||
            svc.tapByText("Caption") ||
            svc.tapByContentDescription("caption")
            delay(500)
            svc.typeText(caption)
            delay(500)
        }

        // Share
        if (!tapShare(svc)) {
            speaker.speak("Could not find the Share button.")
            return false
        }
        delay(2000)

        speaker.speak(
            if (caption.isNotBlank())
                "Reel posted with caption: $caption"
            else
                "Reel posted successfully."
        )
        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ensureInstagramOpen(): Boolean {
        if (service?.isInstagramOpen() != true) {
            return controller.openInstagram()
        }
        return true
    }

    private fun noService(): Boolean {
        speaker.speak("Please enable Agentic OS accessibility service first.")
        return false
    }

    private fun tapNewPostButton(svc: InstagramAccessibilityService): Boolean {
        return svc.tapByContentDescription("New post") ||
               svc.tapByContentDescription("Create") ||
               svc.tapByContentDescription("+") ||
               svc.tapByText("+")
    }

    private fun tapNext(svc: InstagramAccessibilityService): Boolean {
        return svc.tapByText("Next") ||
               svc.tapByContentDescription("Next") ||
               svc.tapByText("NEXT")
    }

    private fun tapShare(svc: InstagramAccessibilityService): Boolean {
        return svc.tapByText("Share") ||
               svc.tapByContentDescription("Share") ||
               svc.tapByText("SHARE")
    }
}
