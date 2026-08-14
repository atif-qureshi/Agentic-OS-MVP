# Agentic OS — MVP Requirements
## AI Module — Ollama + Kiro/Kotlin

---

# 1. Objective

Implement the AI portion of the Agentic OS MVP according to the
"Two Days MVP Roadmap".

The complete flow is:

User Command
      ↓
Intent + Entity Extraction
      ↓
Decision Engine
      ↓
   ┌───────────────┐
   │               │
 Simple        Difficult
   │               │
   ↓               ↓
Android Intent   Gemini
                   ↓
            Structured Action Steps
                   ↓
              Android Action


The work is divided into two environments:

1. OLLAMA
   - Local Qwen3 1.7B model
   - Model testing
   - Prompt testing
   - AI output testing

2. KIRO / KOTLIN
   - Android application
   - Ollama API integration
   - Intent/entity processing
   - Decision Engine
   - Gemini integration
   - UI
   - Validation
   - Testing
   - Final integration


==================================================
2. PART A — OLLAMA WORK
==================================================

# 2.1 Install Ollama

Install Ollama on the development computer.

Verify installation:

ollama --version


# 2.2 Download Qwen3 1.7B

Run:

ollama pull qwen3:1.7b


# 2.3 Verify Model

Run:

ollama list


Expected:

qwen3:1.7b


# 2.4 Run Model

Run:

ollama run qwen3:1.7b


The model must start successfully.


# 2.5 Basic Model Test

Test simple commands such as:

Open YouTube

Open Google Maps

Call Ali

Search for restaurants


The purpose of this stage is only to confirm that Qwen3 can understand
basic commands.


# 2.6 Test Intent Extraction

Give Qwen commands and check whether it can identify the intent.

Example:

Input:

Open YouTube


Expected conceptual result:

{
  "intent": "OPEN_APP",
  "entities": {
    "app": "YouTube"
  }
}


# 2.7 Test Entity Extraction

Test entities such as:

- app
- query
- contact
- message
- url


Example:

Input:

Call Ali


Expected:

{
  "intent": "CALL_CONTACT",
  "entities": {
    "contact": "Ali"
  }
}


# 2.8 Create/Test System Prompt

Create a system prompt that instructs Qwen to:

- Understand the command.
- Detect intent.
- Extract entities.
- Return structured output.
- Use supported intents.
- Return UNKNOWN for unsupported commands.

The model should not execute Android actions.


# 2.9 Test Structured JSON

Verify that Qwen consistently produces structured JSON.

Example:

{
  "intent": "OPEN_APP",
  "entities": {
    "app": "Google Maps"
  }
}


# 2.10 Test Different Natural-Language Commands

Test variations such as:

Open YouTube

Launch YouTube

Start YouTube

Can you open YouTube?

I want to use YouTube.


The purpose is to determine whether Qwen can understand natural
variations of the same command.


# 2.11 Ollama API Verification

Verify that Ollama's API is available.

Base URL:

http://localhost:11434

Chat endpoint:

http://localhost:11434/api/chat


The API must respond before Kotlin integration begins.


# 2.12 Ollama Completion Criteria

Ollama-side work is complete when:

[ ] Ollama installed
[ ] Qwen3 1.7B downloaded
[ ] Qwen3 runs successfully
[ ] Basic commands tested
[ ] Intent extraction tested
[ ] Entity extraction tested
[ ] System prompt tested
[ ] JSON output tested
[ ] Natural-language variations tested
[ ] Ollama API confirmed working


After this point, development moves to Kiro.


==================================================
3. PART B — KIRO / KOTLIN WORK
==================================================

# 3.1 Create/Open Kotlin Android Project

Open the Agentic OS Android project in Kiro.

All application-side implementation will be done here.


# 3.2 Add Internet Permission

AndroidManifest.xml:

<uses-permission android:name="android.permission.INTERNET"/>


# 3.3 Configure Ollama Connection

The Kotlin application must communicate with Ollama through HTTP.

For desktop/local testing:

http://localhost:11434


For Android Emulator:

http://10.0.2.2:11434


Chat endpoint:

http://10.0.2.2:11434/api/chat


# 3.4 Create OllamaClient

File:

OllamaClient.kt


Responsibilities:

- Connect to Ollama.
- Send user command.
- Send system prompt.
- Request Qwen processing.
- Receive response.
- Return model output.


# 3.5 Create PromptBuilder

File:

PromptBuilder.kt


Responsibilities:

- Store system prompt.
- Define supported intents.
- Define entity types.
- Insert user command.
- Prepare the model request.


# 3.6 Create Data Models

Create Kotlin models for:

- Command
- IntentResult
- CommandResult
- DecisionResult
- ActionStep


Example:

data class CommandResult(
    val intent: String,
    val entities: Map<String, String>
)


# 3.7 Implement Intent + Entity Extraction

Flow:

User Command
      ↓
Kotlin
      ↓
OllamaClient
      ↓
Qwen3
      ↓
Intent + Entities


Example:

User:

Open Google Maps


Result:

{
  "intent": "OPEN_APP",
  "entities": {
    "app": "Google Maps"
  }
}


# 3.8 JSON Validation

Validate the Qwen response before passing it to the Decision Engine.

Check:

- Valid JSON
- Intent exists
- Entities exist
- Intent is supported
- Required entity is present


# 3.9 Unknown Command Handling

If the command cannot be understood:

{
  "intent": "UNKNOWN",
  "entities": {}
}


The application must not attempt to execute unknown commands.


# 3.10 Create DecisionEngine

File:

DecisionEngine.kt


Responsibilities:

Determine whether the command is:

SIMPLE

or

DIFFICULT


Flow:

Intent + Entities
       ↓
DecisionEngine
       ↓
 ┌───────────────┐
 │               │
Simple        Difficult
 │               │
 ↓               ↓
Android         Gemini
Intent


# 3.11 Simple Command Processing

Simple commands should be routed toward Android Intent execution.

Examples:

Open YouTube

Open Google Maps

Open Settings


Decision:

{
  "execution_type": "ANDROID_INTENT"
}


The downstream Android execution layer will consume this decision.


# 3.12 Gemini Integration

Create:

GeminiClient.kt


Gemini is used for difficult commands.

Flow:

Difficult Command
      ↓
Gemini
      ↓
Structured Action Steps


Do not send simple commands to Gemini unnecessarily.


# 3.13 Structured Gemini Action Steps

Gemini should return structured action information.

Example:

{
  "execution_type": "GEMINI",
  "steps": [
    {
      "action": "SEARCH",
      "query": "restaurants near me"
    },
    {
      "action": "OPEN_MAP",
      "target": "selected restaurant"
    }
  ]
}


# 3.14 Repository Layer

Create:

CommandRepository.kt


Flow:

UI
 ↓
ViewModel
 ↓
Repository
 ↓
OllamaClient
 ↓
Qwen3
 ↓
Intent + Entities
 ↓
DecisionEngine
 ↓
Gemini / Android Intent


# 3.15 ViewModel

Create:

CommandViewModel.kt


Responsibilities:

- Receive command from UI.
- Call repository.
- Manage loading state.
- Manage success state.
- Manage error state.
- Display result.


==================================================
4. UI REQUIREMENTS
==================================================

# 4.1 Basic MVP UI

The application must contain:

- Command input field
- Process/Understand button
- Loading indicator
- Intent display
- Entity display
- Decision display
- Action-step display when applicable
- Error display


Example:

--------------------------------
       Agentic OS MVP

Command:

[ Open Google Maps             ]

[ Understand ]

Intent:
OPEN_APP

Entity:
app = Google Maps

Decision:
SIMPLE

Execution:
ANDROID_INTENT
--------------------------------


For a difficult command:

--------------------------------
Command:
Find a restaurant near me and
open it in Maps.

Intent:
...

Decision:
DIFFICULT

Execution:
GEMINI

Action Steps:
1. Search restaurants
2. Select restaurant
3. Open location in Maps
--------------------------------


==================================================
5. ERROR HANDLING
==================================================

The Kotlin application must handle:

[ ] Ollama unavailable
[ ] Qwen unavailable
[ ] Gemini unavailable
[ ] API failure
[ ] Timeout
[ ] Invalid JSON
[ ] Unknown intent
[ ] Missing entity
[ ] Empty command
[ ] Invalid Gemini response

The application must not crash because of an AI/API failure.


==================================================
6. TESTING REQUIREMENTS
==================================================

The MVP roadmap requires testing with:

5–10 representative commands.


Testing should include:

- Simple commands
- Difficult commands
- Intent extraction
- Entity extraction
- Decision Engine
- Gemini processing
- Structured action steps


Example test commands:

1. Open YouTube
2. Open Google Maps
3. Open Settings
4. Call Ali
5. Search for restaurants
6. Send a message to Ali
7. Find a restaurant near me and open it in Maps
8. Search for information and perform a follow-up action


==================================================
7. SUCCESS / FAILURE MEASUREMENT
==================================================

Record:

- Total commands
- Successful commands
- Failed commands
- Intent failures
- Entity failures
- Decision failures
- Gemini failures
- Action-step failures


Success Rate:

Successful Commands
-------------------
Total Commands
× 100


Failure Rate:

Failed Commands
---------------
Total Commands
× 100


Example:

Total = 10
Successful = 8
Failed = 2

Success Rate = 80%
Failure Rate = 20%


==================================================
8. TEST REPORT
==================================================

Create:

member1_test_results.md


Format:

| # | Command | Intent | Entities | Decision | Result |
|---|---------|--------|----------|----------|--------|
| 1 | Open YouTube | OPEN_APP | YouTube | SIMPLE | PASS |
| 2 | Open Maps | OPEN_APP | Maps | SIMPLE | PASS |
| 3 | Call Ali | CALL_CONTACT | Ali | SIMPLE | PASS |
| 4 | Difficult command | ... | ... | DIFFICULT | PASS |


For every failure record:

- Command
- Expected result
- Actual result
- Failure type
- Reason
- Fix


==================================================
9. RECOMMENDED KIRO PROJECT STRUCTURE
==================================================

app/
└── src/
    └── main/
        ├── java/
        │   └── com/example/agenticos/
        │
        │       ├── ai/
        │       │   ├── OllamaClient.kt
        │       │   ├── GeminiClient.kt
        │       │   └── PromptBuilder.kt
        │       │
        │       ├── model/
        │       │   ├── Command.kt
        │       │   ├── IntentResult.kt
        │       │   ├── CommandResult.kt
        │       │   ├── DecisionResult.kt
        │       │   └── ActionStep.kt
        │       │
        │       ├── decision/
        │       │   └── DecisionEngine.kt
        │       │
        │       ├── repository/
        │       │   └── CommandRepository.kt
        │       │
        │       ├── viewmodel/
        │       │   └── CommandViewModel.kt
        │       │
        │       └── ui/
        │
        └── assets/
            └── command_schema.json


==================================================
10. COMPLETE DEVELOPMENT FLOW
==================================================

PHASE A — OLLAMA

Install Ollama
      ↓
Pull Qwen3 1.7B
      ↓
Run Qwen
      ↓
Test commands
      ↓
Test prompt
      ↓
Test Intent
      ↓
Test Entities
      ↓
Test JSON
      ↓
Verify API


PHASE B — KIRO

Create/Open Kotlin Project
      ↓
Add Internet Permission
      ↓
Configure Ollama URL
      ↓
Create OllamaClient
      ↓
Connect Kotlin → Ollama
      ↓
Connect Ollama → Qwen
      ↓
Receive Intent + Entities
      ↓
Validate JSON
      ↓
Create DecisionEngine
      ↓
Simple / Difficult
      ↓
Simple → Android Intent
      ↓
Difficult → Gemini
      ↓
Gemini → Structured Action Steps
      ↓
Connect UI
      ↓
Add Error Handling
      ↓
Test 5–10 Commands
      ↓
Measure Success/Failure
      ↓
Document Results


==================================================
11. FINAL ARCHITECTURE
==================================================

                     USER
                       │
                       ↓
                  Kotlin UI
                       │
                       ↓
                Command Input
                       │
                       ↓
                 OllamaClient
                       │
                       ↓
                 Ollama API
                       │
                       ↓
                 Qwen3 1.7B
                       │
                       ↓
               Intent + Entities
                       │
                       ↓
               JSON Validation
                       │
                       ↓
               Decision Engine
                  /         \
                 /           \
            SIMPLE         DIFFICULT
               │               │
               ↓               ↓
        Android Intent       Gemini
                               │
                               ↓
                      Action Steps
                               │
                               ↓
                       Android Action


==================================================
12. COMPLETION CRITERIA
==================================================

OLLAMA:

[ ] Ollama installed
[ ] Qwen3 1.7B installed
[ ] Qwen3 runs
[ ] Commands tested
[ ] Prompt tested
[ ] Intent tested
[ ] Entity extraction tested
[ ] JSON tested
[ ] Ollama API verified


KIRO:

[ ] Kotlin project ready
[ ] Internet permission added
[ ] Ollama connection working
[ ] OllamaClient implemented
[ ] PromptBuilder implemented
[ ] Intent/entity extraction integrated
[ ] JSON validation implemented
[ ] DecisionEngine implemented
[ ] Simple path implemented
[ ] GeminiClient implemented
[ ] Difficult path implemented
[ ] Structured action steps implemented
[ ] UI connected
[ ] Error handling implemented
[ ] 5–10 commands tested
[ ] Success/failure recorded
[ ] Final MVP demonstration working


==================================================
13. FINAL MVP RESULT
==================================================

The final demonstration must show:

User enters a command
        ↓
Qwen understands command
        ↓
Intent + Entities generated
        ↓
Decision Engine decides
        ↓
Simple command
        OR
Difficult command
        ↓
Android Intent
        OR
Gemini
        ↓
Structured Action
        ↓
Android Action


FINAL MEMBER 1 MVP OUTPUT:

Command
   ↓
Intent + Entities
   ↓
Decision
   ↓
Action


==================================================
14. PHASE 2 — INSTAGRAM AUTOMATION
==================================================

# 14.1 Objective

Instagram ko Agentic OS se automate karna — user voice/text command
dega aur agent khud Instagram par action lega.

# 14.2 Instagram Automation Flow

```
User Command (voice/text)
         ↓
Intent Extraction (Qwen3)
         ↓
Instagram Intent Detected
         ↓
Accessibility Service
         ↓
Instagram App Control
         ↓
Action Complete
```

# 14.3 Supported Instagram Actions

| # | Command | Action |
|---|---------|--------|
| 1 | "Open Instagram" | Instagram launch |
| 2 | "Like the post" | Current post like |
| 3 | "Comment 'Nice photo'" | Comment karo |
| 4 | "Follow this account" | Follow button tap |
| 5 | "Unfollow this account" | Unfollow |
| 6 | "Go to profile of Ali" | Search & open profile |
| 7 | "Send message to Ali" | DM kholo |
| 8 | "Post a photo" | New post flow |
| 9 | "Search for food" | Search bar use karo |
| 10 | "Open reels" | Reels tab kholo |
| 11 | "Open stories" | Stories dekhne jao |
| 12 | "Scroll down" | Feed scroll |

# 14.4 Technical Implementation

## 14.4.1 Accessibility Service

Android Accessibility Service use karni hogi — yeh bina root ke
dusri apps ko control kar sakti hai.

```
File: InstagramAccessibilityService.kt

Responsibilities:
- Instagram UI elements detect karna
- Buttons tap karna (Like, Follow, Comment)
- Text input karna (Comment, DM)
- Scroll karna
- Navigate karna
```

## 14.4.2 New Intents (Instagram)

```
INSTAGRAM_LIKE          → entities: {}
INSTAGRAM_COMMENT       → entities: { "text": "<comment>" }
INSTAGRAM_FOLLOW        → entities: { "account": "<name>" }
INSTAGRAM_UNFOLLOW      → entities: { "account": "<name>" }
INSTAGRAM_OPEN_PROFILE  → entities: { "account": "<name>" }
INSTAGRAM_DM            → entities: { "account": "<name>", "message": "<text>" }
INSTAGRAM_SEARCH        → entities: { "query": "<search>" }
INSTAGRAM_SCROLL        → entities: { "direction": "up/down" }
INSTAGRAM_POST          → entities: {}
INSTAGRAM_REELS         → entities: {}
```

## 14.4.3 AndroidManifest Changes

```xml
<service
    android:name=".accessibility.InstagramAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config"/>
</service>
```

## 14.4.4 Project Structure Addition

```
app/src/main/java/com/example/agenticos/
    └── accessibility/
        ├── InstagramAccessibilityService.kt
        ├── InstagramController.kt
        └── UIElementFinder.kt
```

# 14.5 Completion Criteria

- [ ] Accessibility Service created
- [ ] Instagram package integrated
- [ ] Like action working
- [ ] Comment action working
- [ ] Follow/Unfollow working
- [ ] DM working
- [ ] Search working
- [ ] Voice → Instagram action working end-to-end


==================================================
15. PHASE 2 — CONVERSATIONAL AI (AGENT TALKS BACK)
==================================================

# 15.1 Objective

Agent ko conversational banana — user kuch bhi pooche ya baat kare
to agent jawab de. Sirf commands nahi, full conversation bhi.

# 15.2 Conversation Flow

```
User: "What is the weather today?"
         ↓
Agent detects: CONVERSATION intent (not a command)
         ↓
Qwen3 / Gemini generates response
         ↓
Text-to-Speech (TTS)
         ↓
Agent speaks the answer out loud
         ↓
Response also shown on screen
```

# 15.3 Two Modes

| Mode | Trigger | Action |
|------|---------|--------|
| COMMAND mode | "Open YouTube", "Call Ali" | App action |
| CONVERSATION mode | "What is 2+2?", "Tell me a joke" | Spoken answer |

## Detection Logic

```kotlin
// DecisionEngine update
when (intent) {
    "OPEN_APP", "CALL_CONTACT" ... → ANDROID_INTENT
    "SEARCH", "SEND_MESSAGE"   ... → GEMINI
    "CONVERSATION"             ... → TTS_RESPONSE  ← NEW
    "UNKNOWN"                  ... → Ask for clarification
}
```

# 15.4 Supported Conversation Types

| # | Example | Response Type |
|---|---------|---------------|
| 1 | "What time is it?" | Current time |
| 2 | "What is the weather?" | Weather API |
| 3 | "Tell me a joke" | Gemini joke |
| 4 | "What is 25 * 4?" | Math answer |
| 5 | "Who are you?" | Agent intro |
| 6 | "What can you do?" | Capabilities list |
| 7 | "Remind me to drink water" | Reminder set |
| 8 | "What did I just open?" | Context aware |
| 9 | General questions | Qwen3 answer |

# 15.5 Technical Implementation

## 15.5.1 Text-to-Speech (TTS)

Android built-in TTS use karein — koi extra library nahi chahiye.

```kotlin
File: AgentSpeaker.kt

class AgentSpeaker(context: Context) {
    private val tts = TextToSpeech(context) { ... }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stop() = tts.stop()
    fun destroy() = tts.shutdown()
}
```

## 15.5.2 New Intent

```
CONVERSATION → entities: { "query": "<user question>" }
```

## 15.5.3 ConversationEngine

```kotlin
File: ConversationEngine.kt

Responsibilities:
- Detect conversation vs command
- Call Qwen3 for answer generation
- Return spoken response text
- Maintain conversation history (context)
```

## 15.5.4 Conversation Prompt

```
You are Agentic, an AI assistant on Android.
Answer the user's question in 1-2 sentences.
Be helpful, friendly, and concise.
```

## 15.5.5 Context Memory

```kotlin
data class ConversationTurn(
    val userMessage: String,
    val agentResponse: String,
    val timestamp: Long
)

// Last 5 turns remembered for context
val conversationHistory = ArrayDeque<ConversationTurn>(5)
```

# 15.6 Updated Flow (Phase 2)

```
User speaks: "Hey Agentic" → "What is the capital of Pakistan?"
                    ↓
VoiceManager captures text
                    ↓
Qwen3: intent = CONVERSATION, query = "capital of Pakistan"
                    ↓
ConversationEngine → Qwen3/Gemini generates answer
                    ↓
Answer: "The capital of Pakistan is Islamabad"
                    ↓
AgentSpeaker.speak("The capital of Pakistan is Islamabad")
                    ↓
Screen: shows answer text
                    ↓
Agent speaks answer out loud 🔊
```

# 15.7 Completion Criteria

- [ ] CONVERSATION intent added to Qwen3 prompt
- [ ] ConversationEngine created
- [ ] AgentSpeaker (TTS) created
- [ ] Agent responds to general questions
- [ ] Agent speaks answer out loud
- [ ] Conversation history maintained (5 turns)
- [ ] Context-aware responses
- [ ] "Who are you?" returns agent intro
- [ ] Math questions answered
- [ ] Weather API integrated (optional)


==================================================
16. COMBINED PHASE 2 ARCHITECTURE
==================================================

```
User Voice/Text
      ↓
VoiceManager / Input
      ↓
Qwen3 Intent Extraction
      ↓
┌─────────────────────────────────┐
│         Intent Router           │
├──────────────┬──────────────────┤
│   COMMAND    │  INSTAGRAM       │  CONVERSATION
│   (Phase 1)  │  (Phase 2A)      │  (Phase 2B)
│      ↓       │       ↓          │      ↓
│  Android     │ Accessibility    │  ConversationEngine
│  Intent      │ Service          │      ↓
│      ↓       │       ↓          │  Qwen3/Gemini Answer
│  App Opens   │ Instagram Action │      ↓
│              │                  │  AgentSpeaker (TTS)
└──────────────┴──────────────────┘
                    ↓
              Action Complete
                    ↓
         Agent speaks confirmation
```

==================================================
17. PHASE 2 PRIORITY ORDER
==================================================

Implement in this order:

1. AgentSpeaker (TTS)          — Easy, high impact
2. CONVERSATION intent          — Makes agent feel alive
3. ConversationEngine           — Core conversation logic
4. Context memory               — Makes conversations smart
5. Accessibility Service setup  — Instagram foundation
6. Instagram basic actions      — Like, Follow, Comment
7. Instagram DM                 — Advanced action
8. Instagram post/stories       — Complex automation


==================================================
18. INSTAGRAM POST AUTOMATION (Accessibility Service)
==================================================

# 18.1 Objective

User voice/text se Instagram par photo/video post kar sake —
Accessibility Service ke zariye bina manually kuch kiye.

# 18.2 Post Flow

```
User: "Post this photo with caption Beautiful day"
              ↓
Qwen3: INSTAGRAM_POST
       entities: { "caption": "Beautiful day" }
              ↓
InstagramPostController
              ↓
Step 1: Instagram open karo
Step 2: + (New Post) button tap karo
Step 3: Gallery se photo select karo
Step 4: Next button tap karo
Step 5: Filter screen → Next tap karo
Step 6: Caption field mein text type karo
Step 7: Share button tap karo
              ↓
Post Published ✅
Agent speaks: "Your post has been shared successfully"
```

# 18.3 Supported Post Commands

| # | Command | Action |
|---|---------|--------|
| 1 | "Post this photo with caption [text]" | Gallery → post |
| 2 | "Post a photo" | Gallery kholo, caption optional |
| 3 | "Post with caption [text]" | Caption ke saath post |
| 4 | "Post a reel" | Video post flow |
| 5 | "Post to story" | Story post flow |
| 6 | "Add photo to story" | Story mein add |

# 18.4 New Intent

```
INSTAGRAM_POST → entities: {
    "caption"  : "<post caption text>",
    "type"     : "post / reel / story",
    "media"    : "photo / video / latest"
}
```

Examples:
```json
{"intent":"INSTAGRAM_POST","entities":{"caption":"Beautiful day","type":"post","media":"latest"}}
{"intent":"INSTAGRAM_STORY","entities":{"media":"latest"}}
{"intent":"INSTAGRAM_REEL","entities":{"caption":"Fun video","media":"latest"}}
```

# 18.5 Technical Implementation

## 18.5.1 InstagramPostController.kt

```kotlin
File: accessibility/InstagramPostController.kt

class InstagramPostController(
    private val service: InstagramAccessibilityService
) {

    suspend fun createPost(caption: String, mediaType: String) {
        // Step 1: Open Instagram
        openInstagram()
        delay(2000)

        // Step 2: Tap + button (new post)
        service.tapByContentDescription("New post")
        delay(1500)

        // Step 3: Select POST tab (not reel/story)
        service.tapByText("POST")
        delay(1000)

        // Step 4: Select latest photo from gallery
        service.tapFirstGalleryItem()
        delay(1000)

        // Step 5: Tap Next
        service.tapByText("Next")
        delay(1500)

        // Step 6: Skip filters → tap Next again
        service.tapByText("Next")
        delay(1500)

        // Step 7: Type caption
        service.typeInField("Write a caption...", caption)
        delay(500)

        // Step 8: Tap Share
        service.tapByText("Share")
        delay(2000)

        // Step 9: Confirm
        agentSpeaker.speak("Your post has been shared successfully")
    }

    suspend fun createStory(mediaType: String) {
        openInstagram()
        delay(2000)
        service.tapByContentDescription("New post")
        delay(1500)
        service.tapByText("STORY")
        delay(1000)
        service.tapFirstGalleryItem()
        delay(1000)
        service.tapByContentDescription("Send to")
        delay(1500)
        service.tapByText("Your Story")
        delay(1000)
        service.tapByText("Share")
        agentSpeaker.speak("Story posted successfully")
    }

    suspend fun createReel(caption: String) {
        openInstagram()
        delay(2000)
        service.tapByContentDescription("New post")
        delay(1500)
        service.tapByText("REELS")
        delay(1000)
        service.tapFirstGalleryItem()
        delay(1000)
        service.tapByText("Next")
        delay(1500)
        service.typeInField("Write a caption...", caption)
        delay(500)
        service.tapByText("Share")
        agentSpeaker.speak("Reel posted successfully")
    }
}
```

## 18.5.2 UIElementFinder.kt

```kotlin
File: accessibility/UIElementFinder.kt

class UIElementFinder {

    // Find element by text
    fun findByText(root: AccessibilityNodeInfo, text: String)
        : AccessibilityNodeInfo?

    // Find element by content description
    fun findByContentDesc(root: AccessibilityNodeInfo, desc: String)
        : AccessibilityNodeInfo?

    // Find element by resource ID
    fun findById(root: AccessibilityNodeInfo, id: String)
        : AccessibilityNodeInfo?

    // Find first image/video in gallery
    fun findFirstGalleryItem(root: AccessibilityNodeInfo)
        : AccessibilityNodeInfo?

    // Find text input field by hint
    fun findInputField(root: AccessibilityNodeInfo, hint: String)
        : AccessibilityNodeInfo?
}
```

## 18.5.3 InstagramAccessibilityService.kt

```kotlin
File: accessibility/InstagramAccessibilityService.kt

class InstagramAccessibilityService : AccessibilityService() {

    private val finder = UIElementFinder()

    // Tap element by text
    fun tapByText(text: String): Boolean

    // Tap element by content description
    fun tapByContentDescription(desc: String): Boolean

    // Type text in a field
    fun typeInField(hint: String, text: String): Boolean

    // Scroll down in current view
    fun scrollDown(): Boolean

    // Tap first item in gallery
    fun tapFirstGalleryItem(): Boolean

    // Wait for element to appear (timeout)
    suspend fun waitForElement(text: String, timeoutMs: Long = 5000): Boolean

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Handle Instagram UI events
    }

    override fun onInterrupt() {}
}
```

## 18.5.4 accessibility_service_config.xml

```xml
File: res/xml/accessibility_service_config.xml

<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackAllMask"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="com.instagram.android"/>
```

# 18.6 Permission Required

User ko ek baar manually Accessibility Permission deni hogi:

```
Settings → Accessibility → Agentic OS → Enable
```

App khud is screen par le jaayegi aur guide karegi.

# 18.7 Media Selection Modes

| Mode | Description |
|------|-------------|
| `latest` | Gallery ki sabse pehli (latest) photo/video |
| `specific` | User ne specify ki — "post the sunset photo" |
| `camera` | Pehle camera kholo, photo lo, phir post karo |

# 18.8 Error Handling

| Error | Handling |
|-------|---------|
| Instagram not installed | Play Store par le jao |
| Accessibility not enabled | Settings screen kholo |
| Post failed | "Post failed, please try again" |
| Caption too long | "Caption is too long, shortening..." |
| No media in gallery | "No photos found in gallery" |
| Network error | "Check your internet connection" |

# 18.9 Complete Post Voice Commands

```
"Post this photo"
"Post with caption Good morning everyone"
"Post a photo to my story"
"Share a reel with caption Check this out"
"Post the latest photo with caption Vacation vibes"
"Add photo to story"
"Post without caption"
```

# 18.10 Project Structure (Complete Phase 2)

```
app/src/main/java/com/example/agenticos/
│
├── accessibility/
│   ├── InstagramAccessibilityService.kt  ← Core service
│   ├── InstagramController.kt            ← Actions (like, follow, etc.)
│   ├── InstagramPostController.kt        ← Post/Story/Reel
│   └── UIElementFinder.kt               ← UI element helper
│
├── conversation/
│   ├── ConversationEngine.kt             ← Talk back logic
│   └── AgentSpeaker.kt                  ← Text-to-Speech
│
└── (existing Phase 1 files)
```

# 18.11 Completion Criteria

- [ ] Accessibility Service registered in Manifest
- [ ] Permission screen shown to user on first launch
- [ ] Instagram Post working (latest photo + caption)
- [ ] Instagram Story working
- [ ] Instagram Reel working
- [ ] Caption voice input working
- [ ] Error handling complete
- [ ] Agent speaks confirmation after post
- [ ] End-to-end voice → post working
