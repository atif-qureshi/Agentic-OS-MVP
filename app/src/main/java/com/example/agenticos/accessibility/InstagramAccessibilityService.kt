package com.example.agenticos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Accessibility Service that controls Instagram UI elements.
 *
 * Capabilities:
 * - Tap buttons by text or content description
 * - Type text into input fields
 * - Scroll feeds
 * - Navigate Instagram screens
 *
 * Must be enabled in: Settings → Accessibility → Agentic OS
 */
class InstagramAccessibilityService : AccessibilityService() {

    companion object {
        var instance: InstagramAccessibilityService? = null
            private set

        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── Public Actions ────────────────────────────────────────────────────────

    /** Tap element by visible text */
    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByText(root, text) ?: return false
        return performClick(node)
    }

    /** Tap element by content description */
    fun tapByContentDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByContentDescription(root, desc) ?: return false
        return performClick(node)
    }

    /** Type text into focused or hinted input field */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // Find focused editable field
        val node = findFocusedEditText(root) ?: findEditText(root) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Type text into field with specific hint */
    fun typeInFieldWithHint(hint: String, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findByHint(root, hint) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Scroll down in current view */
    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Scroll up in current view */
    fun scrollUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /** Tap first image/video item in gallery */
    fun tapFirstGalleryItem(): Boolean {
        val root = rootInActiveWindow ?: return false
        // Instagram gallery items have content descriptions like "Photo 1" or are RecyclerView items
        val node = findFirstImageNode(root) ?: return false
        return performClick(node)
    }

    /** Go back */
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Check if Instagram is currently in foreground */
    fun isInstagramOpen(): Boolean {
        return rootInActiveWindow?.packageName == INSTAGRAM_PACKAGE
    }

    /** Wait for element with text to appear */
    suspend fun waitForText(text: String, timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (tapByText(text)) return true
            delay(300)
        }
        return false
    }

    // ── Node Finders ──────────────────────────────────────────────────────────

    private fun findByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByContentDescription(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun findByHint(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.hintText?.toString()?.contains(hint, ignoreCase = true) == true) return node
        if (node.isEditable && node.text?.toString()?.contains(hint, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByHint(child, hint)
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val found = findFocusedEditText(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditText(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollable(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstImageNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("photo", ignoreCase = true) ||
            desc.contains("image", ignoreCase = true) ||
            desc.contains("video", ignoreCase = true)) {
            return node
        }
        // Also check by class name
        if (node.className?.toString()?.contains("ImageView") == true &&
            node.isClickable) return node

        for (i in 0 until node.childCount) {
            val found = findFirstImageNode(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Try parent
        val parent = node.parent ?: return false
        if (parent.isClickable) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Try coordinate tap
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            return tapCoordinate(
                bounds.centerX().toFloat(),
                bounds.centerY().toFloat()
            )
        }
        return false
    }

    private fun tapCoordinate(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Tap at exact screen coordinates from Gemini Vision.
     * Most reliable method — works even when button names change.
     */
    fun tapAt(x: Int, y: Int): Boolean {
        return tapCoordinate(x.toFloat(), y.toFloat())
    }

    /**
     * Execute a full action plan from Gemini Vision.
     * Taps each coordinate in sequence with delay.
     */
    suspend fun executePlan(steps: List<com.example.agenticos.screen.TapStep>): Boolean {
        if (steps.isEmpty()) return false
        var success = true
        for (step in steps) {
            android.util.Log.d("AccessibilityService", "Tapping: ${step.description} at (${step.x}, ${step.y})")
            val tapped = tapCoordinate(step.x.toFloat(), step.y.toFloat())
            if (!tapped) success = false
            kotlinx.coroutines.delay(800) // Wait between taps
        }
        return success
    }
}
