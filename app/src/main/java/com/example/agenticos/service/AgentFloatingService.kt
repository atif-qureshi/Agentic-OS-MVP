package com.example.agenticos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.agenticos.R
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
import com.example.agenticos.screen.ScreenCaptureManager
import com.example.agenticos.screen.ScreenPermissionActivity
import com.example.agenticos.voice.VoiceCallback
import com.example.agenticos.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Floating bubble service — runs fully in background.
 * Agent listens, processes commands, executes actions.
 * App never needs to open.
 *
 * + Screen capture via MediaProjection (Gemini Vision)
 * + Wake word listening
 * + Instagram automation
 * + Conversation TTS
 */
class AgentFloatingService : Service(), VoiceCallback {

    companion object {
        const val CHANNEL_ID      = "agentic_os_bubble"
        const val NOTIFICATION_ID = 1001
        const val ACTION_BUBBLE_TAP = "com.example.agenticos.BUBBLE_TAP"
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private lateinit var windowManager: WindowManager
    private lateinit var agentSpeaker: AgentSpeaker
    private lateinit var intentExecutor: AndroidIntentExecutor
    private lateinit var instagramController: InstagramController
    private lateinit var instagramPostController: InstagramPostController
    private lateinit var repository: CommandRepository
    private lateinit var screenCapture: ScreenCaptureManager
    private lateinit var screenAnalyzer: ScreenAnalyzer

    private var bubbleView: View? = null
    private var voiceManager: VoiceManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Bubble drag tracking
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    // Screen permission broadcast receiver
    private val screenPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultCode = intent?.getIntExtra(
                ScreenPermissionActivity.EXTRA_RESULT_CODE, -1) ?: return
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>(
                ScreenPermissionActivity.EXTRA_DATA) ?: return
            screenCapture.start(resultCode, data)
            agentSpeaker.speak("I can now see your screen.")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        windowManager          = getSystemService(WINDOW_SERVICE) as WindowManager
        agentSpeaker           = AgentSpeaker(this)
        intentExecutor         = AndroidIntentExecutor(this)
        instagramController    = InstagramController(this, agentSpeaker)
        instagramPostController = InstagramPostController(this, agentSpeaker, instagramController)
        repository             = CommandRepository()
        screenCapture          = ScreenCaptureManager(this)
        screenAnalyzer         = ScreenAnalyzer()
        voiceManager           = VoiceManager(this, this)

        // Register screen permission receiver
        registerReceiver(
            screenPermissionReceiver,
            IntentFilter(ScreenPermissionActivity.ACTION_GRANTED),
            RECEIVER_NOT_EXPORTED
        )

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Tap bubble or say Hey Agentic"))
        showBubble()

        // Ask for screen capture permission after short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            requestScreenPermission()
        }, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BUBBLE_TAP) onBubbleTapped()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenPermissionReceiver) } catch (_: Exception) {}
        screenCapture.stop()
        removeBubble()
        voiceManager?.destroy()
        agentSpeaker.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Screen Permission ─────────────────────────────────────────────────────

    private fun requestScreenPermission() {
        if (screenCapture.isRunning) return
        val intent = Intent(this, ScreenPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
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

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                    params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                    windowManager.updateViewLayout(bubbleView, params); true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) onBubbleTapped()
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
            findViewById<View>(R.id.bubbleBg)?.setBackgroundResource(R.drawable.bubble_bg)
            val mic = findViewById<ImageView>(R.id.ivBubbleMic) ?: return
            mic.clearAnimation()
            mic.startAnimation(AnimationUtils.loadAnimation(
                this@AgentFloatingService, R.anim.bubble_idle))
        }
        updateNotification("Tap bubble or say Hey Agentic")
    }

    private fun setListeningState() {
        bubbleView?.apply {
            findViewById<View>(R.id.bubbleBg)?.setBackgroundResource(R.drawable.bubble_listening_bg)
            val mic = findViewById<ImageView>(R.id.ivBubbleMic) ?: return
            mic.clearAnimation()
            mic.startAnimation(AnimationUtils.loadAnimation(
                this@AgentFloatingService, R.anim.pulse_scale))
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
        setListeningState()
        agentSpeaker.speak("Yes?")
        voiceManager?.startCommandListening()
    }

    // ── VoiceCallback ─────────────────────────────────────────────────────────

    override fun onWakeWordDetected() = onBubbleTapped()

    override fun onCommandReceived(text: String) {
        setProcessingState()
        updateNotification("Processing: $text")
        serviceScope.launch { processCommandInBackground(text) }
    }

    override fun onPartialResult(text: String)  { updateNotification("Heard: $text") }
    override fun onListeningStarted()           { setListeningState() }
    override fun onListeningEnded()             { setProcessingState() }
    override fun onVolumeChanged(rms: Float)    {}
    override fun onVoiceError(message: String)  {
        setIdleState()
        agentSpeaker.speak("I didn't catch that. Tap the bubble to try again.")
    }

    // ── Command Processing ────────────────────────────────────────────────────

    private suspend fun processCommandInBackground(text: String) {
        try {
            // Capture screen for context if available
            val screenBitmap = if (screenCapture.isRunning) {
                screenCapture.captureScreen()
            } else null

            val result = repository.processCommand(Command(text))

            when (result) {
                is RepositoryResult.Success -> handleDecision(result.decision, text, screenBitmap)
                is RepositoryResult.Error   -> agentSpeaker.speak("Error: ${result.message}")
            }
        } catch (e: Exception) {
            agentSpeaker.speak("Something went wrong. Please try again.")
        } finally {
            setIdleState()
        }
    }

    private suspend fun handleDecision(
        decision: DecisionResult,
        originalText: String,
        screenBitmap: android.graphics.Bitmap?
    ) {
        when (decision.executionType) {

            ExecutionType.ANDROID_INTENT -> {
                val appName = decision.entities["app"] ?: ""
                if (appName.isNotBlank()) agentSpeaker.speak("Opening $appName")
                val r = intentExecutor.execute(decision)
                when (r) {
                    is ExecutionResult.Success      -> {}
                    is ExecutionResult.Failure      -> agentSpeaker.speak(r.message)
                    is ExecutionResult.NeedsPermission -> agentSpeaker.speak("Phone permission needed.")
                    is ExecutionResult.NotSupported -> agentSpeaker.speak(r.message)
                }
            }

            ExecutionType.CONVERSATION -> {
                var spokenText = decision.actionSteps
                    .firstOrNull { it.action == "SPEAK" }?.query
                    ?: "I'm not sure about that."

                // Special: "what's on screen" / "what do you see"
                val q = (decision.entities["query"] ?: "").lowercase()
                if (screenBitmap != null && (q.contains("screen") ||
                    q.contains("see") || q.contains("what is") || q.contains("describe"))) {
                    spokenText = screenAnalyzer.describeScreen(screenBitmap)
                }

                agentSpeaker.speak(spokenText)
            }

            ExecutionType.GEMINI -> {
                val steps = decision.actionSteps
                if (steps.isEmpty()) agentSpeaker.speak("I'll work on that.")
                else agentSpeaker.speak("Here's the plan: ${steps.take(3).joinToString(", ") { it.action }}")
            }

            ExecutionType.INSTAGRAM -> {
                // Use screen-aware execution if screen is available
                if (screenBitmap != null) {
                    executeInstagramWithScreen(decision, screenBitmap, originalText)
                } else {
                    executeInstagram(decision, null)
                }
            }

            ExecutionType.UNKNOWN -> {
                if (screenBitmap != null) {
                    // Ask Gemini what's on screen and what to do
                    val plan = screenAnalyzer.planAction(screenBitmap, originalText)
                    if (plan.possible && plan.steps.isNotEmpty()) {
                        agentSpeaker.speak(plan.message)
                        val svc = com.example.agenticos.accessibility
                            .InstagramAccessibilityService.instance
                        svc?.executePlan(plan.steps)
                    } else {
                        agentSpeaker.speak("I don't know how to do that yet.")
                    }
                } else {
                    agentSpeaker.speak("I didn't understand. Please try again.")
                }
            }
        }
    }

    /**
     * Screen-aware Instagram execution.
     * Uses Gemini Vision to find exact coordinates before tapping.
     */
    private suspend fun executeInstagramWithScreen(
        decision: DecisionResult,
        bitmap: android.graphics.Bitmap,
        originalText: String
    ) {
        val svc = com.example.agenticos.accessibility
            .InstagramAccessibilityService.instance

        // Map command to what element to look for
        val targetElement = when (decision.intent) {
            "INSTAGRAM_LIKE"    -> "Like button or heart icon"
            "INSTAGRAM_COMMENT" -> "Comment button or speech bubble icon"
            "INSTAGRAM_FOLLOW"  -> "Follow button"
            "INSTAGRAM_UNFOLLOW"-> "Following button"
            "INSTAGRAM_POST"    -> "New post button or plus icon"
            "INSTAGRAM_STORY"   -> "Add to story button"
            "INSTAGRAM_REELS",
            "INSTAGRAM_REEL"    -> "Reels tab"
            "INSTAGRAM_SCROLL"  -> null // No element needed
            "INSTAGRAM_DM"      -> "Direct message or send button"
            else                -> null
        }

        if (targetElement != null && svc != null) {
            // Find exact coordinates with Gemini Vision
            val coord = screenAnalyzer.findCoordinates(bitmap, targetElement)

            if (coord.found && coord.confidence > 50) {
                agentSpeaker.speak("Found ${targetElement}. Tapping it.")
                svc.tapAt(coord.x, coord.y)

                // Wait and verify
                kotlinx.coroutines.delay(1000)
                val newScreen = screenCapture.captureScreen()
                if (newScreen != null) {
                    val done = screenAnalyzer.verifyAction(newScreen, "action completed on Instagram")
                    if (done) agentSpeaker.speak("Done!")
                    else {
                        // Fallback to name-based tap
                        executeInstagram(decision, bitmap)
                    }
                } else {
                    agentSpeaker.speak("Done!")
                }
                return
            } else {
                agentSpeaker.speak("I can't see the $targetElement clearly. Trying anyway.")
            }
        }

        // Fallback to name-based execution
        executeInstagram(decision, bitmap)
    }

    // ── Instagram Execution ───────────────────────────────────────────────────

    private suspend fun executeInstagram(
        decision: DecisionResult,
        screenBitmap: android.graphics.Bitmap?
    ) {
        val caption = decision.entities["caption"] ?: ""
        val account = decision.entities["account"] ?: ""
        val message = decision.entities["message"] ?: ""
        val text    = decision.entities["text"]    ?: ""
        val query   = decision.entities["query"]   ?: ""
        val dir     = decision.entities["direction"] ?: "down"

        // Ensure Instagram is open
        if (!instagramController.isInstagramInstalled()) {
            agentSpeaker.speak("Instagram is not installed.")
            return
        }

        val isOpen = com.example.agenticos.accessibility
            .InstagramAccessibilityService.instance?.isInstagramOpen() == true

        if (!isOpen) {
            agentSpeaker.speak("Opening Instagram.")
            instagramController.openInstagram()
        }

        // Use screen analysis to verify state if screen capture is running
        if (screenBitmap != null && decision.intent == "INSTAGRAM_LIKE") {
            val element = screenAnalyzer.findElement(screenBitmap, "Like button")
            if (!element.found) {
                agentSpeaker.speak("I can't see a like button. Make sure a post is visible.")
                return
            }
        }

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
            else -> agentSpeaker.speak("Instagram action not supported yet.")
        }

        // Verify action completed if screen available
        if (screenBitmap != null) {
            android.os.SystemClock.sleep(1500)
            val newScreen = screenCapture.captureScreen()
            if (newScreen != null) {
                val verified = screenAnalyzer.verifyAction(newScreen, "Instagram")
                if (!verified) {
                    agentSpeaker.speak("Action may not have completed. Please check Instagram.")
                }
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Agentic OS", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
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
