package com.example.agenticos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
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
        private const val TAG = "InstagramAccessibilityService"
        var instance: InstagramAccessibilityService? = null
            private set

        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "✓ Agentic OS Accessibility Service CONNECTED & ACTIVE!")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility Service Destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (instance == null) {
            instance = this
            Log.d(TAG, "✓ Service instance attached on AccessibilityEvent")
        }
    }
    override fun onInterrupt() {}

    /**
     * Get root node, searching all visible windows if a floating overlay (bubble) is focused.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (root != null && root.packageName == INSTAGRAM_PACKAGE) {
            return root
        }

        val windowList = windows
        if (windowList != null) {
            for (w in windowList) {
                if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val node = w.root
                    if (node != null && node.packageName == INSTAGRAM_PACKAGE) {
                        return node
                    }
                }
            }
            for (w in windowList) {
                if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val node = w.root
                    if (node != null) return node
                }
            }
        }
        return root
    }

    // ── Public Actions ────────────────────────────────────────────────────────

    /** Tap element by visible text */
    fun tapByText(text: String): Boolean {
        val root = getRootNode() ?: return false
        val node = findByText(root, text) ?: return false
        Log.d(TAG, "✓ Found element by text: '$text'")
        return performClick(node)
    }

    /** Tap element by content description */
    fun tapByContentDescription(desc: String): Boolean {
        val root = getRootNode() ?: return false
        val node = findByContentDescription(root, desc) ?: return false
        Log.d(TAG, "✓ Found element by content description: '$desc'")
        return performClick(node)
    }

    /** Type text into focused or hinted input field */
    fun typeText(text: String): Boolean {
        val root = getRootNode() ?: return false
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
        val root = getRootNode() ?: return false
        val node = findByHint(root, hint) ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Scroll down in current view */
    fun scrollDown(): Boolean {
        val root = getRootNode() ?: return false
        val scrollable = findScrollable(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Scroll up in current view */
    fun scrollUp(): Boolean {
        val root = getRootNode() ?: return false
        val scrollable = findScrollable(root) ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /** Tap first image/video item in gallery */
    fun tapFirstGalleryItem(): Boolean {
        val root = getRootNode() ?: return false
        val node = findFirstImageNode(root) ?: return false
        return performClick(node)
    }

    /** Go back */
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Check if Instagram is currently in foreground */
    fun isInstagramOpen(): Boolean {
        val root = getRootNode()
        return root?.packageName == INSTAGRAM_PACKAGE
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

    /** Tap comment icon specifically */
    fun tapCommentIcon(): Boolean {
        val root = getRootNode() ?: return false
        val node = findCommentButton(root)
        if (node != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val tapX = if (bounds.width() > 300) (bounds.left + 220).toFloat() else bounds.centerX().toFloat()
            val tapY = bounds.centerY().toFloat()
            Log.d(TAG, "✓ Tapping comment icon at ($tapX, $tapY)")
            return tapCoordinate(tapX, tapY)
        }
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val defaultY = if (bounds.height() > 0) bounds.height() * 0.55f else 1200f
        Log.d(TAG, "✓ Fallback tapping comment icon at (220, $defaultY)")
        return tapCoordinate(220f, defaultY)
    }

    private fun findCommentButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.equals("Comment", ignoreCase = true) || desc.equals("Add a comment…", ignoreCase = true)) {
            return node
        }
        if (!desc.contains("photo", ignoreCase = true) && !desc.contains("likes", ignoreCase = true)) {
            if (desc.contains("comment", ignoreCase = true)) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findCommentButton(child)
            if (found != null) return found
        }
        return null
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
        val nodeDesc = node.contentDescription?.toString() ?: ""

        // Exclude photo container nodes summarizing post details ("photo 1 of 4 by wahzir in the hood, 22,096 likes, 100 comments")
        if (nodeDesc.contains("photo", ignoreCase = true) ||
            nodeDesc.contains("likes", ignoreCase = true) ||
            nodeDesc.contains("1 of", ignoreCase = true)) {
            if (!desc.contains("photo", ignoreCase = true)) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val found = findByContentDescription(child, desc)
                    if (found != null) return found
                }
                return null
            }
        }

        if (nodeDesc.equals(desc, ignoreCase = true) || nodeDesc.contains(desc, ignoreCase = true)) {
            return node
        }

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

    fun doubleTapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path, 0, 40)
        val stroke2 = GestureDescription.StrokeDescription(path, 100, 40)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        Log.d(TAG, "✓ Dispatching double-tap gesture at ($x, $y)")
        return dispatchGesture(gesture, null, null)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.centerX() > 0 && bounds.centerY() > 0) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            // If container is wide (>300px), calculate exact icon offset based on description
            val tapX = if (bounds.width() > 300) {
                when {
                    desc.contains("comment") || text.contains("comment") -> (bounds.left + 220).toFloat()
                    desc.contains("send") || desc.contains("share") -> (bounds.left + 350).toFloat()
                    else -> (bounds.left + 75).toFloat() // Default like heart
                }
            } else {
                bounds.centerX().toFloat()
            }
            val tapY = bounds.centerY().toFloat()

            Log.d(TAG, "✓ Dispatching touch gesture tap at ($tapX, $tapY) for desc: '$desc'")
            val tapped = tapCoordinate(tapX, tapY)
            if (tapped) return true
        }

        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
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
