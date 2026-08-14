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
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Captures phone screen using MediaProjection API.
 * Uses continuous frame listener to ensure screen bitmap is ALWAYS available.
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 1001
        private const val TAG = "ScreenCaptureManager"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val metrics = DisplayMetrics()
    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDpi    = 0

    @Volatile
    private var latestBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                stopInternal()
            }
        }

        mediaProjection = mgr.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(callback, mainHandler)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 3
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage() ?: reader.acquireNextImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride   = planes[0].rowStride
                    val rowPadding  = (rowStride - pixelStride * screenWidth).coerceAtLeast(0)

                    buffer.rewind()

                    val bmp = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)

                    val cropped = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
                    if (bmp != cropped) {
                        bmp.recycle()
                    }

                    synchronized(this) {
                        val old = latestBitmap
                        latestBitmap = cropped
                        old?.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}")
            } finally {
                image?.close()
            }
        }, mainHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AgentCapture",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, mainHandler
        )

        Log.d(TAG, "Screen projection started: ${screenWidth}x${screenHeight}")
    }

    private fun stopInternal() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
        synchronized(this) {
            latestBitmap?.recycle()
            latestBitmap = null
        }
    }

    /** Stop screen capture and release resources */
    fun stop() {
        mediaProjection?.stop()
        stopInternal()
        mediaProjection = null
    }

    val isRunning: Boolean get() = mediaProjection != null && virtualDisplay != null

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Capture current screen as Bitmap.
     * Guaranteed to return latest cached frame or acquire fresh frame.
     */
    fun captureScreen(): Bitmap? {
        synchronized(this) {
            val current = latestBitmap
            if (current != null && !current.isRecycled) {
                return current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
            }
        }

        // Fallback directly from reader if listener hasn't fired yet
        val reader = imageReader ?: return null
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: reader.acquireNextImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = (rowStride - pixelStride * screenWidth).coerceAtLeast(0)

            buffer.rewind()

            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
            if (bmp != cropped) bmp.recycle()
            cropped
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen fallback failed: ${e.message}")
            null
        } finally {
            image?.close()
        }
    }

    /**
     * Capture screen and return as JPEG byte array.
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

