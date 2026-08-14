package com.example.agenticos.screen

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Transparent activity that requests MediaProjection permission.
 * Launches instantly, asks for permission, then closes.
 *
 * Result is broadcast to AgentFloatingService.
 */
class ScreenPermissionActivity : Activity() {

    companion object {
        const val ACTION_GRANTED = "com.example.agenticos.SCREEN_GRANTED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA        = "data"
    }

    private lateinit var captureManager: ScreenCaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureManager = ScreenCaptureManager(this)

        // Request screen capture permission
        val permIntent = captureManager.createPermissionIntent(this)
        startActivityForResult(permIntent, ScreenCaptureManager.REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ScreenCaptureManager.REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Permission granted — broadcast to service
                val broadcast = Intent(ACTION_GRANTED).apply {
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_DATA, data)
                    setPackage(packageName)
                }
                sendBroadcast(broadcast)
            }
            finish()
        }
    }
}
