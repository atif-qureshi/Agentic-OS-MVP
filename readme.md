# 🤖 Agentic OS MVP

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android"/>
  <img src="https://img.shields.io/badge/AI-Qwen3%201.7B-blue?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Vision-Gemini%201.5%20Flash-orange?style=for-the-badge&logo=google"/>
  <img src="https://img.shields.io/badge/Architecture-MVC-purple?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-blue?style=for-the-badge&logo=kotlin"/>
</p>

<p align="center">
  <b>An AI-powered Android assistant that understands voice commands, sees your screen, and takes real actions — all running locally.</b>
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎤 **Voice Commands** | Say "Hey Agentic" wake word — fully hands-free |
| 🧠 **Local AI** | Qwen3 1.7B via Ollama — no cloud required for NLU |
| 👁️ **Screen Vision** | Gemini Vision sees your screen and finds UI elements |
| 📱 **App Control** | Opens any app — YouTube, Maps, WhatsApp, Instagram |
| 📸 **Instagram Automation** | Post photos, like, comment, follow via voice |
| 💬 **Conversations** | Ask questions — agent speaks answers aloud |
| 🫧 **Floating Bubble** | Gemini-style bubble on every screen |
| 🔐 **Permission Dialog** | Asks before every action |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                   USER                          │
│            (Voice / Text Input)                 │
└──────────────────┬──────────────────────────────┘
                   │
         ┌─────────▼──────────┐
         │   VoiceManager     │  ← Wake word + command listening
         │  "Hey Agentic"     │
         └─────────┬──────────┘
                   │
         ┌─────────▼──────────┐
         │   OllamaClient     │  ← HTTP to local Ollama server
         │   Qwen3 1.7B       │  ← Intent + Entity extraction
         └─────────┬──────────┘
                   │
         ┌─────────▼──────────┐
         │  DecisionEngine    │  ← Routes to correct handler
         └──────┬──────┬──────┘
                │      │
     ┌──────────▼─┐  ┌─▼──────────────┐
     │  SIMPLE    │  │   DIFFICULT     │
     │  INSTAGRAM │  │  CONVERSATION  │
     │  UNKNOWN   │  │                │
     └──────┬─────┘  └─┬──────────────┘
            │           │
   ┌────────▼─────┐  ┌──▼───────────┐
   │AndroidIntent │  │ GeminiClient │
   │ Executor     │  │ + TTS        │
   └────────┬─────┘  └──────────────┘
            │
   ┌────────▼─────────────────────────┐
   │     ScreenCaptureManager         │  ← MediaProjection API
   │     ScreenAnalyzer (Gemini)      │  ← Finds UI coordinates
   │     InstagramAccessibility       │  ← Taps exact pixels
   └──────────────────────────────────┘
```

### MVC Pattern

```
MODEL                   CONTROLLER              VIEW
─────────────────       ──────────────────      ────────────────
OllamaClient      ←→   CommandController  ←→   MainActivity
GeminiClient            (all logic)             (render only)
DecisionEngine
CommandRepository
ScreenAnalyzer
```

---

## 🚀 Quick Start

### Prerequisites

- Android phone (API 26+)
- Windows/Mac/Linux PC on same WiFi
- [Ollama](https://ollama.com) installed on PC
- Android Studio

### 1. Setup Ollama (PC)

```bash
# Install Ollama from https://ollama.com
ollama pull qwen3:1.7b
ollama serve
```

### 2. Clone & Open Project

```bash
git clone https://github.com/atif-qureshi/Agentic-OS-MVP-Instagram.git
cd Agentic-OS-MVP-Instagram
```

Open in Android Studio: `File → Open → Agentic-OS-MVP-Instagram`

### 3. Configure IP Address

Open `OllamaClient.kt` and add your PC's IP:

```kotlin
private val CANDIDATE_HOSTS = listOf(
    "YOUR_PC_IP_HERE",    // e.g. "192.168.1.5"
    "10.0.2.2"            // emulator fallback
)
```

Find your PC IP:
- **Windows:** `ipconfig` → IPv4 Address
- **Mac/Linux:** `ifconfig` → inet

### 4. Add Gemini API Key

Get free key at [aistudio.google.com](https://aistudio.google.com)

Open `GeminiClient.kt`:
```kotlin
private const val GEMINI_API_KEY = "YOUR_KEY_HERE"
```

### 5. Build & Run

Connect phone via USB, enable USB Debugging, press ▶ Run.

---

## 📱 One-Time Phone Setup

### Enable Accessibility Service (Required for Instagram)
```
Settings → Accessibility → Installed Services → Agentic OS → Enable
```

### Allow Overlay Permission (Required for Floating Bubble)
App will ask automatically on first launch. Tap **Allow**.

### Allow Screen Recording (Required for Screen Vision)
App will ask automatically. Tap **Start Now**.

---

## 🎯 Voice Commands

### App Control
```
"Open YouTube"
"Open Instagram"
"Open Google Maps"
"Open Settings"
"Open WhatsApp"
```

### Phone
```
"Call Ali"
"Search for restaurants"
```

### Instagram
```
"Like the post"
"Comment Nice photo"
"Follow this account"
"Unfollow Ali"
"Post this photo with caption Good morning"
"Post to story"
"Post a reel with caption Check this out"
"Send message to Ali saying Hello"
"Open reels"
"Search for food on Instagram"
"Scroll down"
```

### Conversation
```
"Who are you?"
"What time is it?"
"What's today's date?"
"What can you do?"
"What's on my screen?"
"25 times 4"
"Tell me a joke"
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | API 26 (Android 8.0) |
| Local AI | Ollama + Qwen3 1.7B |
| Cloud AI | Google Gemini 1.5 Flash |
| Vision AI | Gemini Vision (screen analysis) |
| HTTP | OkHttp 4.12 |
| Architecture | MVC |
| Voice | Android SpeechRecognizer |
| TTS | Android TextToSpeech |
| Screen Capture | MediaProjection API |
| App Control | Android Accessibility Service |
| UI | Material Design 3 |

---

## 📁 Project Structure

```
app/src/main/java/com/example/agenticos/
│
├── ai/
│   ├── OllamaClient.kt          ← Qwen3 API calls
│   ├── GeminiClient.kt          ← Gemini action planning
│   ├── PromptBuilder.kt         ← System prompts
│   └── JsonValidator.kt         ← Response validation
│
├── accessibility/
│   ├── InstagramAccessibilityService.kt  ← UI control
│   ├── InstagramController.kt            ← Like, Follow, DM
│   └── InstagramPostController.kt        ← Post, Story, Reel
│
├── screen/
│   ├── ScreenCaptureManager.kt   ← MediaProjection capture
│   ├── ScreenAnalyzer.kt         ← Gemini Vision coordinates
│   └── ScreenPermissionActivity.kt
│
├── conversation/
│   ├── ConversationEngine.kt     ← Q&A + context memory
│   └── AgentSpeaker.kt           ← Text-to-Speech
│
├── voice/
│   ├── VoiceManager.kt           ← Wake word + commands
│   └── VoiceDialog.kt            ← Animated listening UI
│
├── service/
│   └── AgentFloatingService.kt   ← Background bubble service
│
├── decision/
│   └── DecisionEngine.kt         ← SIMPLE/DIFFICULT/INSTAGRAM/CONVERSATION
│
├── executor/
│   └── AndroidIntentExecutor.kt  ← Launch apps
│
├── controller/
│   └── CommandController.kt      ← MVC Controller
│
├── repository/
│   └── CommandRepository.kt      ← Orchestration
│
├── model/
│   ├── Command.kt
│   ├── CommandResult.kt
│   ├── DecisionResult.kt
│   └── ActionStep.kt
│
└── ui/
    └── MainActivity.kt           ← View only
```

---

## 🔑 Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Ollama + Gemini API |
| `RECORD_AUDIO` | Voice commands |
| `CALL_PHONE` | Phone calls |
| `SYSTEM_ALERT_WINDOW` | Floating bubble |
| `FOREGROUND_SERVICE` | Background service |
| `BIND_ACCESSIBILITY_SERVICE` | Instagram control |
| Screen Recording | See screen content (user grants at runtime) |

---

## ⚡ How Screen Vision Works

```
User: "Like the post"
        ↓
MediaProjection captures screenshot
        ↓
Sent to Gemini Vision API:
"Find Like button. Image: 1080x2400px"
        ↓
Gemini: {"found":true, "x":156, "y":1890, "confidence":94}
        ↓
Gesture API taps exact pixel (156, 1890) ✅
        ↓
Screenshot taken again to verify
        ↓
Agent speaks: "Done!" 🎉
```

---

## 📊 Test Results

| # | Command | Intent | Decision | Result |
|---|---------|--------|----------|--------|
| 1 | Open YouTube | OPEN_APP | SIMPLE | ✅ PASS |
| 2 | Open Google Maps | OPEN_APP | SIMPLE | ✅ PASS |
| 3 | Open Settings | OPEN_SETTINGS | SIMPLE | ✅ PASS |
| 4 | Call Ali | CALL_CONTACT | SIMPLE | ✅ PASS |
| 5 | Search restaurants | SEARCH | DIFFICULT | ✅ PASS |
| 6 | Like the post | INSTAGRAM_LIKE | INSTAGRAM | ✅ PASS |
| 7 | Post photo caption Hello | INSTAGRAM_POST | INSTAGRAM | ✅ PASS |
| 8 | Who are you | CONVERSATION | CONVERSATION | ✅ PASS |
| 9 | What time is it | CONVERSATION | CONVERSATION | ✅ PASS |
| 10 | xyzzy boo | UNKNOWN | UNKNOWN | ✅ PASS |

**Success Rate: 100% (10/10)**

---

## 🗺️ Roadmap

- [x] Phase 1 — Core MVP (Voice + Intent + App Control)
- [x] Phase 2A — Instagram Automation (Accessibility Service)
- [x] Phase 2B — Agent Talks Back (TTS + Conversations)
- [x] Phase 2C — Screen Vision (Gemini Vision + Coordinates)
- [ ] Phase 3 — Session Manager (Multi-turn conversations)
- [ ] Phase 4 — App Capability Registry (Dynamic app discovery)
- [ ] Phase 5 — Workflow Memory (Learn from past commands)
- [ ] Phase 6 — On-device model (No PC required)

---

## 📄 License

MIT License — feel free to use, modify, and distribute.

---

## 👤 Author

**Atif Qureshi**
- GitHub: [@atif-qureshi](https://github.com/atif-qureshi)

---

<p align="center">Built with ❤️ using Kotlin, Ollama, and Gemini</p>
