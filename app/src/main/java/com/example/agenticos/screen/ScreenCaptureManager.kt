package com.example.agenticos.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Captures phone screen using MediaProjection API.
 * No root required — user grants permission once.
 *
 * Usage:
 * 1. Call requestPermission() from Activity to get intent
 * 2. Pass result to start()
 * 3. Call capture() to get current screen as Bitmap
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 1001
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val metrics = DisplayMetrics()
    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDpi    = 0

    // ── Setup ─────────────────────────────────────────────────────────────────

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth  = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDpi    = metrics.densityDpi
    }

    /** Call this from Activity to show the system permission dialog */
    fun createPermissionIntent(activity: Activity): Intent {
        val mgr = activity.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /** Start screen capture with permission result */
    fun start(resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = mgr.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AgentCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    /** Stop screen capture and release resources */
    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay   = null
        imageReader      = null
        mediaProjection  = null
    }

    val isRunning: Boolean get() = mediaProjection != null

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Capture current screen as Bitmap.
     * Returns null if capture not started or failed.
     */
    fun captureScreen(): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to exact screen size
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } catch (e: Exception) {
            null
        } finally {
            image?.close()
        }
    }

    /**
     * Capture screen and return as JPEG byte array.
     * Compressed to reduce size for AI processing.
     */
    fun captureAsJpeg(quality: Int = 70): ByteArray? {
        val bitmap = captureScreen() ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Capture screen and return as Base64 string for AI APIs.
     */
    fun captureAsBase64(quality: Int = 60): String? {
        val bytes = captureAsJpeg(quality) ?: return null
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
