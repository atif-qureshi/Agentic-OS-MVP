# Agentic OS MVP — How It Works
### Complete System Documentation

---

## Overview

Agentic OS ek AI-powered Android assistant hai jo:
- Voice ya text command leta hai
- Samajhta hai ke user kya karna chahta hai
- Automatically app open karta hai ya action leta hai

---

## Complete Flow

```
User bolta hai: "Hey Agentic"
         ↓
Wake Word Detected (VoiceManager)
         ↓
Gemini-style Dialog khulta hai 🎤
         ↓
User bolta hai: "Open YouTube"
         ↓
Speech → Text (Android SpeechRecognizer)
         ↓
Text Ollama API ko bheja jaata hai
         ↓
Qwen3 1.7B model process karta hai
         ↓
JSON output: {"intent":"OPEN_APP","entities":{"app":"YouTube"}}
         ↓
JSON Validate hota hai
         ↓
DecisionEngine: SIMPLE ya DIFFICULT?
         ↓
     SIMPLE                DIFFICULT
       ↓                       ↓
Permission Dialog          Gemini API
       ↓                       ↓
User: Allow              Action Steps
       ↓                       ↓
YouTube Opens ✅         Steps Displayed
```

---

## Components

### 1. VoiceManager (`voice/VoiceManager.kt`)

**Kya karta hai:** Hamesha background mein sun raha hota hai

**Do modes:**

| Mode | Description |
|------|-------------|
| Wake Word Mode | "Hey Agentic" sunta hai |
| Command Mode | Actual command sunta hai |

**Wake Words supported:**
- "Hey Agentic"
- "Hey Agent"  
- "Agentic"
- "Hi Agentic"
- "Ok Agentic"
- "Hello Agentic"

**Code:**
```kotlin
voiceManager.startWakeWordListening()  // Start background listening
voiceManager.startCommandListening()   // Listen for command
voiceManager.destroy()                 // Release resources
```

---

### 2. VoiceDialog (`voice/VoiceDialog.kt`)

**Kya karta hai:** Gemini-style animated voice UI dikhata hai

**Features:**
- Pulsing animated rings (2 layers)
- Live transcription text
- Volume-reactive animation
- Cancel button

**Triggered when:**
- Wake word detect hota hai
- Mic button dabaya jaata hai

---

### 3. OllamaClient (`ai/OllamaClient.kt`)

**Kya karta hai:** Phone se PC ke Ollama server se baat karta hai

**Connection:**
```
Android Phone  →  WiFi  →  PC (192.168.100.9:11434)  →  Qwen3 Model
```

**Request bheja jaata hai:**
```json
{
  "model": "qwen3:1.7b",
  "messages": [
    {"role": "system", "content": "Return ONLY JSON..."},
    {"role": "user",   "content": "Open YouTube"}
  ],
  "think": false,
  "options": {"temperature": 0.0, "num_predict": 60}
}
```

**`think: false`** — Qwen3 ki thinking band kar deta hai → 3x faster

---

### 4. PromptBuilder (`ai/PromptBuilder.kt`)

**Kya karta hai:** AI ko instructions deta hai

**System Prompt:**
```
You are an intent extractor. Return ONLY JSON. No thinking.

Intents: OPEN_APP, CALL_CONTACT, SEARCH, UNKNOWN
Output: {"intent":"OPEN_APP","entities":{"app":"YouTube"}}
```

---

### 5. JsonValidator (`ai/JsonValidator.kt`)

**Kya karta hai:** AI ka response validate karta hai

**Checks:**
- Valid JSON hai?
- `intent` field hai?
- `entities` field hai?
- Intent supported hai?
- Required entities hain?

**Agar invalid:** UNKNOWN intent return karta hai

---

### 6. DecisionEngine (`decision/DecisionEngine.kt`)

**Kya karta hai:** Command simple hai ya difficult decide karta hai

| Intent | Decision |
|--------|----------|
| OPEN_APP | SIMPLE → Android Intent |
| CALL_CONTACT | SIMPLE → Android Intent |
| OPEN_SETTINGS | SIMPLE → Android Intent |
| OPEN_URL | SIMPLE → Android Intent |
| GET_TIME | SIMPLE → Android Intent |
| SEARCH | DIFFICULT → Gemini |
| SEND_MESSAGE | DIFFICULT → Gemini |
| UNKNOWN | UNKNOWN → No action |

---

### 7. AndroidIntentExecutor (`executor/AndroidIntentExecutor.kt`)

**Kya karta hai:** Actually app open karta hai phone par

**Supported Apps:**
| Command | App |
|---------|-----|
| Open YouTube | YouTube |
| Open Google Maps | Maps |
| Open WhatsApp | WhatsApp |
| Open Instagram | Instagram |
| Open Settings | Settings |
| Open Chrome | Chrome Browser |
| Open Gmail | Gmail |
| Open Spotify | Spotify |
| Open Netflix | Netflix |
| Open Calculator | Calculator |
| Open Camera | Camera |
| + 15 more | ... |

**Agar app installed nahi:** Play Store par le jaata hai

---

### 8. GeminiClient (`ai/GeminiClient.kt`)

**Kya karta hai:** Mushkil commands ke liye Google Gemini use karta hai

**Example:**
```
Command: "Find a restaurant near me and open in Maps"
         ↓
Gemini returns:
{
  "steps": [
    {"action": "SEARCH", "query": "restaurants near me"},
    {"action": "OPEN_MAP", "target": "selected restaurant"}
  ]
}
```

---

### 9. CommandRepository (`repository/CommandRepository.kt`)

**Kya karta hai:** Sab AI calls ko coordinate karta hai

**Flow:**
```kotlin
// 1. Ollama se intent nikalo
val commandResult = ollamaClient.extractIntent(command)

// 2. Decide karo
val decision = DecisionEngine.decide(commandResult)

// 3. Agar difficult → Gemini
if (decision.executionType == GEMINI) {
    val steps = geminiClient.getActionSteps(...)
}
```

---

## MVC Architecture

```
MODEL                    CONTROLLER              VIEW
─────────────────        ──────────────────      ─────────────────
OllamaClient.kt    ←→   CommandController  ←→   MainActivity.kt
GeminiClient.kt          (all logic here)        (render only)
DecisionEngine.kt
CommandRepository.kt
```

---

## Permission System

| Permission | Purpose | When Asked |
|------------|---------|------------|
| RECORD_AUDIO | Voice commands | App first open |
| CALL_PHONE | Make phone calls | When user says "Call..." |
| INTERNET | Ollama + Gemini API | In Manifest (auto) |

**App Launch Permission Dialog:**
- Har action se pehle user se poochhta hai
- User "Allow" kare toh action hota hai
- User "Deny" kare toh action cancel

---

## Wake Word Flow

```
App starts
    ↓
Mic permission check
    ↓
Background listening starts (silent)
    ↓
User says "Hey Agentic"
    ↓
Wake word detected ✅
    ↓
Voice dialog opens (animated)
    ↓
User speaks command
    ↓
Speech → Text → Qwen3 → Action
    ↓
Background listening resumes (automatic)
```

---

## Error Handling

| Error | What Happens |
|-------|-------------|
| Ollama offline | Red error card shows |
| Qwen3 no JSON | Retry with cleaned response |
| App not installed | Play Store opens |
| Call permission denied | Error message shows |
| Voice not recognized | Toast: "No speech detected" |
| Unknown command | UNKNOWN card shows |
| Network timeout | Error card shows |

---

## How To Run

**PC (every time):**
```cmd
ollama serve
```

**Phone:**
1. Open Agentic OS app
2. Say "Hey Agentic" OR tap mic button OR type command
3. Wait for processing
4. Allow permission
5. Action executes

---

## Project Structure

```
app/src/main/java/com/example/agenticos/
│
├── ai/
│   ├── OllamaClient.kt      → Ollama API calls
│   ├── GeminiClient.kt      → Gemini API calls
│   ├── PromptBuilder.kt     → System prompts
│   └── JsonValidator.kt     → Response validation
│
├── voice/
│   ├── VoiceManager.kt      → Wake word + command listening
│   └── VoiceDialog.kt       → Animated voice UI
│
├── model/
│   ├── Command.kt           → User input
│   ├── IntentResult.kt      → Qwen3 output
│   ├── CommandResult.kt     → Validated result
│   ├── DecisionResult.kt    → SIMPLE/DIFFICULT
│   └── ActionStep.kt        → Gemini steps
│
├── decision/
│   └── DecisionEngine.kt    → SIMPLE vs DIFFICULT logic
│
├── executor/
│   └── AndroidIntentExecutor.kt → Launch apps
│
├── controller/
│   └── CommandController.kt → MVC Controller
│
├── repository/
│   └── CommandRepository.kt → Orchestration
│
├── viewmodel/
│   └── CommandViewModel.kt  → State survival
│
└── ui/
    └── MainActivity.kt      → View only
```

---

## Technologies Used

| Technology | Purpose |
|-----------|---------|
| Kotlin | Android development |
| Ollama | Local AI server |
| Qwen3 1.7B | Intent extraction model |
| Google Gemini | Complex command planning |
| Android SpeechRecognizer | Voice to text |
| OkHttp | HTTP networking |
| Material Design 3 | UI components |
| MVC Pattern | Architecture |

---

*Agentic OS MVP — Built with Kiro + Kotlin*
