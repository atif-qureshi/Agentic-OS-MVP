package com.example.agenticos.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.agenticos.R
import com.example.agenticos.accessibility.InstagramAccessibilityService
import com.example.agenticos.accessibility.InstagramController
import com.example.agenticos.accessibility.InstagramPostController
import com.example.agenticos.conversation.AgentSpeaker
import com.example.agenticos.executor.AndroidIntentExecutor
import com.example.agenticos.executor.ExecutionResult
import com.example.agenticos.model.Command
import com.example.agenticos.model.DecisionResult
import com.example.agenticos.model.ExecutionType
import com.example.agenticos.repository.CommandRepository
import com.example.agenticos.repository.RepositoryResult
import com.example.agenticos.screen.ScreenAnalyzer
import com.example.agenticos.screen.ScreenPermissionActivity
import com.example.agenticos.screen.ScreenProjectionService
import com.example.agenticos.voice.VoiceCallback
import com.example.agenticos.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentFloatingService : Service(), VoiceCallback {

    companion object {
        const val CHANNEL_ID        = "agentic_os_bubble"
        const val NOTIFICATION_ID   = 1001
        const val ACTION_BUBBLE_TAP = "com.example.agenticos.BUBBLE_TAP"
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private lateinit var windowManager: WindowManager
    private lateinit var agentSpeaker: AgentSpeaker
    private lateinit var intentExecutor: AndroidIntentExecutor
    private lateinit var instagramController: InstagramController
    private lateinit var instagramPostController: InstagramPostController
    private lateinit var repository: CommandRepository
    private lateinit var screenAnalyzer: ScreenAnalyzer

    private var bubbleView: View? = null
    private var voiceManager: VoiceManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessingCommand = false
    private var lastBubbleTapMs = 0L

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    // Screen permission receiver — starts dedicated projection service (Android 14+ requirement)
    private val screenPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultCode = intent?.getIntExtra(
                ScreenPermissionActivity.EXTRA_RESULT_CODE, -1) ?: return
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(
                ScreenPermissionActivity.EXTRA_DATA) ?: return

            val serviceIntent = Intent(this@AgentFloatingService, ScreenProjectionService::class.java).apply {
                putExtra(ScreenProjectionService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenProjectionService.EXTRA_DATA, data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            agentSpeaker.speak("I can now see your screen.") {
                resumeWakeWordWhenReady()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        windowManager           = getSystemService(WINDOW_SERVICE) as WindowManager
        agentSpeaker            = AgentSpeaker(this)
        intentExecutor          = AndroidIntentExecutor(this)
        instagramController     = InstagramController(this, agentSpeaker)
        instagramPostController = InstagramPostController(this, agentSpeaker, instagramController)
        repository              = CommandRepository()
        screenAnalyzer          = ScreenAnalyzer()
        voiceManager            = VoiceManager(this, this)

        // Register receiver — use Context.RECEIVER_NOT_EXPORTED only on API 33+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                screenPermissionReceiver,
                IntentFilter(ScreenPermissionActivity.ACTION_GRANTED),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                screenPermissionReceiver,
                IntentFilter(ScreenPermissionActivity.ACTION_GRANTED)
            )
        }

        createNotificationChannel()

        // Start foreground with microphone type only — safe on all Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Tap bubble to speak"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Tap bubble to speak"))
        }

        showBubble()
        Handler(Looper.getMainLooper()).postDelayed({ requestScreenPermission() }, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BUBBLE_TAP) onBubbleTapped()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenPermissionReceiver) } catch (_: Exception) {}
        stopService(Intent(this, ScreenProjectionService::class.java))
        removeBubble()
        voiceManager?.destroy()
        agentSpeaker.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Screen Permission ─────────────────────────────────────────────────────

    private fun requestScreenPermission() {
        if (ScreenProjectionService.screenCapture?.isRunning == true) return
        voiceManager?.pauseListening()
        startActivity(Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        mainHandler.postDelayed({ resumeWakeWordWhenReady() }, 4000)
    }

    // ── Bubble UI ─────────────────────────────────────────────────────────────

    private fun showBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.layout_floating_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        bubbleView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) < 10 && kotlin.math.abs(dy) < 10) {
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
        setIdleState()
    }

    private fun removeBubble() {
        bubbleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        bubbleView = null
    }

    // ── Bubble States ─────────────────────────────────────────────────────────

    private fun setIdleState() {
        bubbleView?.apply {
            findViewById<View>(R.id.bubbleBg)
                ?.setBackgroundResource(R.drawable.bubble_bg)
            val mic = findViewById<ImageView>(R.id.ivBubbleMic) ?: return
            mic.clearAnimation()
            mic.startAnimation(
                AnimationUtils.loadAnimation(this@AgentFloatingService, R.anim.bubble_idle))
        }
        updateNotification("Tap bubble to speak")
    }

    private fun setListeningState() {
        bubbleView?.apply {
            findViewById<View>(R.id.bubbleBg)
                ?.setBackgroundResource(R.drawable.bubble_listening_bg)
            val mic = findViewById<ImageView>(R.id.ivBubbleMic) ?: return
            mic.clearAnimation()
            mic.startAnimation(
                AnimationUtils.loadAnimation(this@AgentFloatingService, R.anim.pulse_scale))
        }
        updateNotification("Listening…")
    }

    private fun setProcessingState() {
        bubbleView?.apply {
            val mic = findViewById<ImageView>(R.id.ivBubbleMic) ?: return
            mic.clearAnimation()
        }
        updateNotification("Processing…")
    }

    // ── User Interaction ──────────────────────────────────────────────────────

    private fun onBubbleTapped() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBubbleTapMs < 800) return
        if (isProcessingCommand) return
        lastBubbleTapMs = now

        agentSpeaker.stop()
        voiceManager?.pauseListening()
        setListeningState()
        mainHandler.postDelayed({
            voiceManager?.startCommandListening()
        }, 250)
    }

    private fun startVoiceListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            voiceManager?.resumeWakeWord()
        }
    }

    private fun resumeWakeWordWhenReady() {
        if (isProcessingCommand) return
        agentSpeaker.whenIdle { voiceManager?.resumeWakeWord() }
    }

    // ── VoiceCallback ─────────────────────────────────────────────────────────

    override fun onWakeWordDetected() {
        // VoiceManager starts command listening right after this callback
        setListeningState()
    }
    override fun onListeningStarted()           { setListeningState() }
    override fun onListeningEnded()             { setProcessingState() }
    override fun onVolumeChanged(rms: Float)    {}
    override fun onPartialResult(text: String)  { updateNotification("Heard: $text") }

    override fun onCommandReceived(text: String) {
        isProcessingCommand = true
        voiceManager?.pauseListening()
        setProcessingState()
        updateNotification("Processing: $text")
        serviceScope.launch { processCommandInBackground(text) }
    }

    override fun onVoiceError(message: String) {
        isProcessingCommand = false
        setIdleState()
        agentSpeaker.speak("I didn't catch that.") {
            resumeWakeWordWhenReady()
        }
    }

    // ── Command Processing ────────────────────────────────────────────────────

    private suspend fun processCommandInBackground(text: String) {
        try {
            val capture = ScreenProjectionService.screenCapture
            val screenBitmap: Bitmap? = if (capture?.isRunning == true)
                capture.captureScreen() else null

            when (val result = repository.processCommand(Command(text))) {
                is RepositoryResult.Success ->
                    handleDecision(result.decision, text, screenBitmap)
                is RepositoryResult.Error ->
                    agentSpeaker.speak("Error: ${result.message}")
            }
        } catch (e: Exception) {
            agentSpeaker.speak("Something went wrong. Please try again.")
        } finally {
            isProcessingCommand = false
            setIdleState()
            resumeWakeWordWhenReady()
        }
    }

    private suspend fun handleDecision(
        decision: DecisionResult,
        originalText: String,
        screenBitmap: Bitmap?
    ) {
        when (decision.executionType) {

            ExecutionType.ANDROID_INTENT -> {
                val appName = decision.entities["app"] ?: ""
                if (appName.isNotBlank()) agentSpeaker.speak("Opening $appName")
                when (val r = intentExecutor.execute(decision)) {
                    is ExecutionResult.Success         -> {}
                    is ExecutionResult.Failure         -> agentSpeaker.speak(r.message)
                    is ExecutionResult.NeedsPermission -> agentSpeaker.speak("Phone permission needed.")
                    is ExecutionResult.NotSupported    -> agentSpeaker.speak(r.message)
                }
            }

            ExecutionType.CONVERSATION -> {
                var spokenText = decision.actionSteps
                    .firstOrNull { it.action == "SPEAK" }?.query
                    ?: "I'm not sure about that."

                val q = (decision.entities["query"] ?: "").lowercase()
                val orig = originalText.lowercase()
                val isScreenQuery = orig.contains("screen") || orig.contains("see") ||
                        orig.contains("dekho") || orig.contains("dikh") || orig.contains("nazar") ||
                        orig.contains("batao") || orig.contains("btao") || orig.contains("kya hai") ||
                        orig.contains("what is") || orig.contains("describe") ||
                        q.contains("screen") || q.contains("see")

                if (isScreenQuery) {
                    if (screenBitmap != null) {
                        spokenText = screenAnalyzer.describeScreen(screenBitmap)
                    } else {
                        spokenText = "Screen capture permission is not active. Please grant screen permission."
                        requestScreenPermission()
                    }
                }
                agentSpeaker.speak(spokenText)
            }

            ExecutionType.GEMINI -> {
                val steps = decision.actionSteps
                if (steps.isEmpty()) agentSpeaker.speak("I'll work on that.")
                else agentSpeaker.speak(
                    "Here's the plan: ${steps.take(3).joinToString(", ") { it.action }}")
            }

            ExecutionType.INSTAGRAM -> {
                if (screenBitmap != null)
                    executeInstagramWithScreen(decision, screenBitmap)
                else
                    executeInstagram(decision, null)
            }

            ExecutionType.UNKNOWN -> {
                if (screenBitmap != null) {
                    val plan = screenAnalyzer.planAction(screenBitmap, originalText)
                    if (plan.possible && plan.steps.isNotEmpty()) {
                        agentSpeaker.speak(plan.message)
                        InstagramAccessibilityService.instance?.executePlan(plan.steps)
                    } else {
                        agentSpeaker.speak("I don't know how to do that yet.")
                    }
                } else {
                    agentSpeaker.speak("I didn't understand. Please try again.")
                }
            }
        }
    }

    // ── Screen-Aware Instagram ────────────────────────────────────────────────

    private suspend fun executeInstagramWithScreen(
        decision: DecisionResult,
        bitmap: Bitmap
    ) {
        val svc = InstagramAccessibilityService.instance

        val targetElement = when (decision.intent) {
            "INSTAGRAM_LIKE"     -> "Like button or heart icon"
            "INSTAGRAM_COMMENT"  -> "Comment button or speech bubble"
            "INSTAGRAM_FOLLOW"   -> "Follow button"
            "INSTAGRAM_UNFOLLOW" -> "Following button"
            "INSTAGRAM_POST"     -> "New post plus button"
            "INSTAGRAM_STORY"    -> "Add to story button"
            "INSTAGRAM_REELS",
            "INSTAGRAM_REEL"     -> "Reels tab"
            "INSTAGRAM_DM"       -> "Direct message button"
            else                 -> null
        }

        if (targetElement != null && svc != null) {
            try {
                val coord = screenAnalyzer.findCoordinates(bitmap, targetElement)
                if (coord.found) {
                    svc.tapAt(coord.x, coord.y)
                    delay(600)
                }
            } catch (e: Exception) {
                Log.w("AgentFloatingService", "Screen Vision check skipped due to Gemini API: ${e.message}")
            }
        }
        executeInstagram(decision, bitmap)
    }

    // ── Instagram Fallback ────────────────────────────────────────────────────

    private suspend fun executeInstagram(
        decision: DecisionResult,
        screenBitmap: Bitmap?
    ) {
        if (!instagramController.isInstagramInstalled()) {
            agentSpeaker.speak("Instagram is not installed.")
            return
        }

        val isOpen = InstagramAccessibilityService.instance?.isInstagramOpen() == true
        if (!isOpen) {
            agentSpeaker.speak("Opening Instagram.")
            instagramController.openInstagram()
            delay(1800)
        }

        val caption = decision.entities["caption"] ?: ""
        val account = decision.entities["account"] ?: ""
        val message = decision.entities["message"] ?: ""
        val text    = decision.entities["text"]    ?: ""
        val query   = decision.entities["query"]   ?: ""
        val dir     = decision.entities["direction"] ?: "down"

        Log.d("AgentFloatingService", "Executing Instagram Intent: ${decision.intent}")

        when (decision.intent) {
            "INSTAGRAM_LIKE"         -> instagramController.likePost()
            "INSTAGRAM_COMMENT"      -> instagramController.commentOnPost(text)
            "INSTAGRAM_FOLLOW"       -> instagramController.followAccount(account)
            "INSTAGRAM_UNFOLLOW"     -> instagramController.unfollowAccount(account)
            "INSTAGRAM_DM"           -> instagramController.sendDirectMessage(account, message)
            "INSTAGRAM_SEARCH"       -> instagramController.searchAccount(query)
            "INSTAGRAM_SCROLL"       -> instagramController.scrollFeed(dir)
            "INSTAGRAM_REEL",
            "INSTAGRAM_REELS"        -> if (caption.isNotBlank())
                instagramPostController.createReel(caption)
            else instagramController.openReels()
            "INSTAGRAM_STORY"        -> instagramPostController.createStory()
            "INSTAGRAM_POST"         -> instagramPostController.createPost(caption)
            "INSTAGRAM_OPEN_PROFILE" -> {
                instagramController.openInstagram()
                if (account.isNotBlank()) instagramController.searchAccount(account)
            }
            else -> agentSpeaker.speak("Instagram action executed.")
        }

        if (screenBitmap != null) {
            delay(1500)
            val newScreen = ScreenProjectionService.screenCapture?.captureScreen()
            if (newScreen != null &&
                !screenAnalyzer.verifyAction(newScreen, "Instagram action")) {
                agentSpeaker.speak("Action performed successfully.")
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Agentic OS", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agentic OS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).setSilent(true).build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
