package com.example.agenticos.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Dedicated foreground service for screen capture.
 * Must be separate from AgentFloatingService because
 * mediaProjection type requires its own foreground service
 * that is started AFTER user grants screen permission.
 */
class ScreenProjectionService : Service() {

    companion object {
        const val CHANNEL_ID      = "screen_projection"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA        = "data"

        // Singleton access
        var screenCapture: ScreenCaptureManager? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        screenCapture = ScreenCaptureManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA) ?: return START_NOT_STICKY

        createChannel()

        // Start foreground with mediaProjection type — required by Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Start screen capture
        screenCapture?.start(resultCode, data)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCapture?.stop()
        screenCapture = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agentic OS")
            .setContentText("Screen capture active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).setSilent(true).build()
}
