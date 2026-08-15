package com.example.agenticos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.agenticos.R
import com.example.agenticos.accessibility.InstagramController
import com.example.agenticos.accessibility.InstagramPostController
import com.example.agenticos.controller.CommandController
import com.example.agenticos.controller.CommandControllerCallback
import com.example.agenticos.conversation.AgentSpeaker
import com.example.agenticos.databinding.ActivityMainBinding
import com.example.agenticos.decision.DecisionEngine
import com.example.agenticos.executor.AndroidIntentExecutor
import com.example.agenticos.executor.ExecutionResult
import com.example.agenticos.model.ActionStep
import com.example.agenticos.model.DecisionResult
import com.example.agenticos.model.ExecutionType
import com.example.agenticos.repository.CommandRepository
import com.example.agenticos.service.AgentFloatingService
import com.example.agenticos.voice.VoiceCallback
import com.example.agenticos.voice.VoiceDialog
import com.example.agenticos.voice.VoiceManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), CommandControllerCallback, VoiceCallback {

    // ── Fields ────────────────────────────────────────────────────────────────

    private lateinit var binding: ActivityMainBinding
    private lateinit var intentExecutor: AndroidIntentExecutor
    private lateinit var voiceManager: VoiceManager
    private lateinit var agentSpeaker: AgentSpeaker
    private lateinit var instagramController: InstagramController
    private lateinit var instagramPostController: InstagramPostController
    private var voiceDialog: VoiceDialog? = null
    private var pendingDecision: DecisionResult? = null

    private val controller: CommandController by lazy {
        CommandController(repository = CommandRepository(), callback = this)
    }

    // ── Permission Launchers ──────────────────────────────────────────────────

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingDecision?.let { handleExecutionResult(intentExecutor.execute(it), it) }
        else showError("Call permission denied.")
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showWakeWordHint()
            voiceManager.startWakeWordListening()
            startFloatingBubbleService()
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) startFloatingBubbleService()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intentExecutor          = AndroidIntentExecutor(this)
        voiceManager            = VoiceManager(this, this)
        agentSpeaker            = AgentSpeaker(this)
        instagramController     = InstagramController(this, agentSpeaker)
        instagramPostController = InstagramPostController(this, agentSpeaker, instagramController)

        setupClickListeners()
        lifecycleScope.launch { controller.onCheckConnectionRequested() }
        requestMicAndStartWakeWord()
        requestOverlayAndStartBubble()

        if (intent?.getBooleanExtra("START_VOICE", false) == true) {
            startVoiceCommand()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("START_VOICE", false) == true) {
            startVoiceCommand()
        }
    }

    override fun onResume() {
        super.onResume()
        // Do NOT auto-start mic on resume — user must tap or say wake word manually
    }

    override fun onPause() {
        super.onPause()
        voiceManager.stopAll()
        voiceDialog?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
        agentSpeaker.destroy()
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnUnderstand.setOnClickListener {
            hideKeyboard()
            val input = binding.etCommand.text?.toString() ?: ""
            lifecycleScope.launch { controller.onCommandSubmitted(input) }
        }

        binding.etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                val input = binding.etCommand.text?.toString() ?: ""
                lifecycleScope.launch { controller.onCommandSubmitted(input) }
                true
            } else false
        }

        binding.btnCheckConnection.setOnClickListener {
            lifecycleScope.launch { controller.onCheckConnectionRequested() }
        }

        binding.btnMic.setOnClickListener {
            // Send tap event to background service — NO app switch
            val intent = Intent(this, AgentFloatingService::class.java)
            intent.action = "com.example.agenticos.BUBBLE_TAP"
            startForegroundService(intent)
        }

        binding.btnReset.setOnClickListener { controller.onResetRequested() }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    private fun requestMicAndStartWakeWord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            showWakeWordHint()
            voiceManager.startWakeWordListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestOverlayAndStartBubble() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingBubbleService()
        } else {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        }
    }

    private fun startFloatingBubbleService() {
        startForegroundService(Intent(this, AgentFloatingService::class.java))
    }

    private fun showWakeWordHint() {
        Toast.makeText(this, "Say \"Hey Agentic\" to start", Toast.LENGTH_LONG).show()
    }

    private fun startVoiceCommand() {
        voiceManager.stopAll()
        showVoiceDialog()
        voiceManager.startCommandListening()
    }

    // ── VoiceCallback ─────────────────────────────────────────────────────────

    override fun onWakeWordDetected() {
        // VoiceManager starts command listening after this — only show UI
        runOnUiThread {
            showVoiceDialog()
            voiceDialog?.setListening()
        }
    }

    override fun onCommandReceived(text: String) {
        runOnUiThread {
            voiceDialog?.setProcessing()
            binding.etCommand.setText(text)
            lifecycleScope.launch { controller.onCommandSubmitted(text) }
            Handler(Looper.getMainLooper()).postDelayed({ dismissVoiceDialog() }, 800)
        }
    }

    override fun onPartialResult(text: String) {
        runOnUiThread { voiceDialog?.updateLiveText(text) }
    }

    override fun onListeningStarted() {
        runOnUiThread { voiceDialog?.setListening() }
    }

    override fun onListeningEnded() {
        runOnUiThread { voiceDialog?.updateLiveText("Processing…") }
    }

    override fun onVolumeChanged(rms: Float) {
        runOnUiThread { voiceDialog?.onVolumeChanged(rms) }
    }

    override fun onVoiceError(message: String) {
        runOnUiThread {
            dismissVoiceDialog()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                voiceManager.startWakeWordListening()
            }, 600)
        }
    }

    private fun showVoiceDialog() {
        voiceDialog?.dismiss()
        voiceDialog = VoiceDialog(this) {
            voiceManager.stopAll()
            voiceManager.startWakeWordListening()
        }
        voiceDialog?.show()
    }

    private fun dismissVoiceDialog() {
        voiceDialog?.dismiss()
        voiceDialog = null
    }

    // ── CommandControllerCallback ─────────────────────────────────────────────

    override fun onValidationError(message: String) = runOnUiThread {
        hideAllResultCards()
        showError(message)
        binding.btnUnderstand.isEnabled = true
    }

    override fun onProcessingStarted() = runOnUiThread {
        hideAllResultCards()
        binding.layoutLoading.isVisible = true
        binding.btnUnderstand.isEnabled = false
        binding.btnReset.isVisible      = false
    }

    override fun onSimpleCommandReady(decision: DecisionResult) = runOnUiThread {
        binding.layoutLoading.isVisible   = false
        binding.btnUnderstand.isEnabled   = true
        binding.btnReset.isVisible        = true
        binding.cardActionSteps.isVisible = false
        renderResultCard(decision)
        executeAndroidIntent(decision)
        // Mic stays OFF — user taps mic or says wake word to activate again
    }

    override fun onDifficultCommandReady(decision: DecisionResult) = runOnUiThread {
        binding.layoutLoading.isVisible = false
        binding.btnUnderstand.isEnabled = true
        binding.btnReset.isVisible      = true
        renderResultCard(decision)
        renderActionSteps(decision.actionSteps)
    }

    override fun onUnknownCommand(decision: DecisionResult) = runOnUiThread {
        binding.layoutLoading.isVisible   = false
        binding.btnUnderstand.isEnabled   = true
        binding.btnReset.isVisible        = true
        binding.cardActionSteps.isVisible = false
        renderResultCard(decision)
        agentSpeaker.speak("Sorry, I didn't understand that. Tap mic to try again.")
    }

    override fun onConversationReady(decision: DecisionResult) = runOnUiThread {
        binding.layoutLoading.isVisible = false
        binding.btnUnderstand.isEnabled = true
        binding.btnReset.isVisible      = true

        val spokenText = decision.actionSteps
            .firstOrNull { it.action == "SPEAK" }?.query
            ?: "I'm not sure about that."

        binding.cardResult.isVisible      = true
        binding.cardError.isVisible       = false
        binding.cardActionSteps.isVisible = true
        binding.tvIntent.text             = "CONVERSATION"
        binding.tvEntities.text           = decision.entities["query"] ?: ""
        binding.tvDecision.text           = "AGENT RESPONSE"
        binding.tvDecision.setTextColor(getColor(R.color.primary))
        binding.tvExecution.text          = spokenText
        binding.tvActionSteps.text        = "💬  $spokenText"

        agentSpeaker.speak(spokenText)
        // Mic stays OFF after speaking
    }

    override fun onInstagramCommandReady(decision: DecisionResult) = runOnUiThread {
        binding.layoutLoading.isVisible   = false
        binding.btnUnderstand.isEnabled   = true
        binding.btnReset.isVisible        = true
        binding.cardActionSteps.isVisible = false
        renderResultCard(decision)
        lifecycleScope.launch { executeInstagramAction(decision) }
    }

    override fun onError(message: String) = runOnUiThread {
        binding.layoutLoading.isVisible = false
        binding.btnUnderstand.isEnabled = true
        binding.btnReset.isVisible      = true
        hideAllResultCards()
        showError(message)
    }

    override fun onReset() = runOnUiThread {
        hideAllResultCards()
        binding.etCommand.text?.clear()
        binding.btnReset.isVisible      = false
        binding.btnUnderstand.isEnabled = true
    }

    override fun onConnectionChecking() = runOnUiThread {
        binding.tvConnectionStatus.text = " Checking Ollama…"
        binding.connectionDot.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.grey))
    }

    override fun onConnectionSuccess() = runOnUiThread {
        binding.tvConnectionStatus.text = " Ollama: connected"
        binding.connectionDot.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.simple_green))
    }

    override fun onConnectionFailed(reason: String) = runOnUiThread {
        binding.tvConnectionStatus.text = " Ollama: offline"
        binding.connectionDot.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.error))
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    private fun executeAndroidIntent(decision: DecisionResult) {
        pendingDecision = decision
        val appName = decision.entities["app"]
            ?: decision.entities["contact"]
            ?: decision.entities["query"]
            ?: decision.intent

        val desc = when (decision.intent) {
            "OPEN_APP"      -> "Open \"$appName\""
            "CALL_CONTACT"  -> "Call \"$appName\""
            "OPEN_SETTINGS" -> "Open Settings"
            "SEARCH"        -> "Search for \"$appName\""
            "OPEN_URL"      -> "Open URL: $appName"
            else            -> "Execute: ${decision.intent}"
        }

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Agentic OS wants to:\n\n$desc\n\nDo you allow this?")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Allow") { _, _ ->
                handleExecutionResult(intentExecutor.execute(decision), decision)
            }
            .setNegativeButton("Deny") { _, _ ->
                showError("Permission denied. Action cancelled.")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleExecutionResult(result: ExecutionResult, decision: DecisionResult) {
        when (result) {
            is ExecutionResult.Success ->
                Toast.makeText(this, "✓ ${result.message}", Toast.LENGTH_SHORT).show()
            is ExecutionResult.NeedsPermission ->
                callPermissionLauncher.launch(result.permission)
            is ExecutionResult.Failure ->
                showError(result.message)
            is ExecutionResult.NotSupported ->
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun executeInstagramAction(decision: DecisionResult) {
        val caption = decision.entities["caption"] ?: ""
        val account = decision.entities["account"] ?: ""
        val message = decision.entities["message"] ?: ""
        val text    = decision.entities["text"]    ?: ""
        val query   = decision.entities["query"]   ?: ""
        val dir     = decision.entities["direction"] ?: "down"

        // Always open Instagram first if not open
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
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderResultCard(decision: DecisionResult) {
        binding.cardResult.isVisible = true
        binding.cardError.isVisible  = false
        binding.tvIntent.text        = decision.intent
        binding.tvEntities.text      = if (decision.entities.isEmpty()) "(none)"
            else decision.entities.entries.joinToString("\n") { (k, v) -> "$k = $v" }

        val (label, colorRes) = when (decision.executionType) {
            ExecutionType.ANDROID_INTENT -> "SIMPLE"       to R.color.simple_green
            ExecutionType.GEMINI         -> "DIFFICULT"    to R.color.gemini_accent
            ExecutionType.CONVERSATION   -> "CONVERSATION" to R.color.primary
            ExecutionType.INSTAGRAM      -> "INSTAGRAM"    to R.color.gemini_accent
            ExecutionType.UNKNOWN        -> "UNKNOWN"      to R.color.error
        }
        binding.tvDecision.text = label
        binding.tvDecision.setTextColor(getColor(colorRes))
        binding.tvExecution.text = DecisionEngine.describeDecision(decision)
    }

    private fun renderActionSteps(steps: List<ActionStep>) {
        if (steps.isEmpty()) { binding.cardActionSteps.isVisible = false; return }
        binding.cardActionSteps.isVisible = true
        binding.tvActionSteps.text = steps.mapIndexed { i, step ->
            val detail = listOfNotNull(
                step.query.takeIf  { it.isNotBlank() }?.let { "query: $it"  },
                step.target.takeIf { it.isNotBlank() }?.let { "target: $it" }
            ).joinToString(", ")
            "${i + 1}. ${step.action}${if (detail.isNotBlank()) " — $detail" else ""}"
        }.joinToString("\n")
    }

    private fun showError(message: String) {
        binding.cardError.isVisible = true
        binding.tvError.text        = message
    }

    private fun hideAllResultCards() {
        binding.cardResult.isVisible      = false
        binding.cardActionSteps.isVisible = false
        binding.cardError.isVisible       = false
        binding.layoutLoading.isVisible   = false
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
