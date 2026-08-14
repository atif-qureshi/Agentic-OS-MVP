package com.example.agenticos.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Helper class for finding UI elements in Accessibility Node Tree.
 */
class UIElementFinder {

    /** Find node by visible text (case-insensitive substring match) */
    fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val textStr = root.text?.toString() ?: ""
        val descStr = root.contentDescription?.toString() ?: ""
        if (textStr.contains(text, ignoreCase = true) || descStr.contains(text, ignoreCase = true)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findByText(child, text)
            if (found != null) return found
        }
        return null
    }

    /** Find node by content description (case-insensitive substring match) */
    fun findByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val descStr = root.contentDescription?.toString() ?: ""
        val textStr = root.text?.toString() ?: ""
        if (descStr.contains(desc, ignoreCase = true) || textStr.contains(desc, ignoreCase = true)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findByContentDesc(child, desc)
            if (found != null) return found
        }
        return null
    }

    /** Find node by Android view resource ID */
    fun findById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val id = root.viewIdResourceName ?: ""
        if (id.endsWith(viewId, ignoreCase = true)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findById(child, viewId)
            if (found != null) return found
        }
        return null
    }

    /** Find editable text input field */
    fun findInputField(root: AccessibilityNodeInfo, hint: String = ""): AccessibilityNodeInfo? {
        if (root.isEditable) {
            if (hint.isBlank()) return root
            val textStr = root.text?.toString() ?: ""
            val descStr = root.contentDescription?.toString() ?: ""
            val hintStr = root.hintText?.toString() ?: ""
            if (textStr.contains(hint, ignoreCase = true) ||
                descStr.contains(hint, ignoreCase = true) ||
                hintStr.contains(hint, ignoreCase = true)) {
                return root
            }
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findInputField(child, hint)
            if (found != null) return found
        }

        // Fallback: return first editable field if specific hint match not found
        if (hint.isNotBlank() && root.isEditable) return root
        return null
    }

    /** Find first image or video item in gallery layout */
    fun findFirstGalleryItem(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = root.contentDescription?.toString() ?: ""
        val className = root.className?.toString() ?: ""

        if (desc.contains("photo", ignoreCase = true) ||
            desc.contains("image", ignoreCase = true) ||
            desc.contains("video", ignoreCase = true) ||
            desc.contains("gallery", ignoreCase = true) ||
            desc.contains("media", ignoreCase = true) ||
            className.contains("ImageView", ignoreCase = true)) {
            if (root.isClickable) return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstGalleryItem(child)
            if (found != null) return found
        }
        return null
    }
}
