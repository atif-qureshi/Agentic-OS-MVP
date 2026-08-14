package com.example.agenticos.ai

object PromptBuilder {

    val systemPrompt: String = """
Extract intent. Return ONLY JSON. No explanation.

{"intent":"OPEN_APP","entities":{"app":"YouTube"}}
{"intent":"OPEN_APP","entities":{"app":"Instagram"}}
{"intent":"OPEN_APP","entities":{"app":"WhatsApp"}}
{"intent":"OPEN_APP","entities":{"app":"Google Maps"}}
{"intent":"OPEN_APP","entities":{"app":"Settings"}}
{"intent":"CALL_CONTACT","entities":{"contact":"Ali"}}
{"intent":"SEARCH","entities":{"query":"restaurants"}}
{"intent":"CONVERSATION","entities":{"query":"what time is it"}}
{"intent":"INSTAGRAM_LIKE","entities":{}}
{"intent":"INSTAGRAM_COMMENT","entities":{"text":"Nice photo"}}
{"intent":"INSTAGRAM_FOLLOW","entities":{"account":"ali"}}
{"intent":"INSTAGRAM_POST","entities":{"caption":"Good morning","type":"post"}}
{"intent":"INSTAGRAM_STORY","entities":{}}
{"intent":"INSTAGRAM_REEL","entities":{"caption":"Fun video"}}
{"intent":"INSTAGRAM_DM","entities":{"account":"ali","message":"Hello"}}
{"intent":"INSTAGRAM_SCROLL","entities":{"direction":"down"}}
{"intent":"UNKNOWN","entities":{}}

Rules:
- OPEN_APP: open/launch/start any app including Instagram, insta, YouTube, WhatsApp, Maps, Gmail, Chrome, Settings. Examples: "open insta", "insta kholo", "open instagram", "start youtube"
- INSTAGRAM_LIKE: like post/photo/reel/pic/image. Examples: "like", "like post", "like this", "is post ko like karo", "heart it", "like pic", "like image", "post like kar do", "pasand karo"
- INSTAGRAM_COMMENT: comment on post, write comment. Examples: "comment nice pic", "comment karo", "write comment"
- INSTAGRAM_FOLLOW: follow someone. Examples: "follow ali", "follow karo", "is ko follow kar do"
- INSTAGRAM_POST: post photo/image to feed. Examples: "post photo", "post lagao", "feed post"
- INSTAGRAM_STORY: add to story. Examples: "add to story", "story lagao"
- INSTAGRAM_REEL: post reel/video or open reels. Examples: "open reels", "reels dikhao"
- INSTAGRAM_DM: send direct message. Examples: "dm ali hello", "message karo"
- INSTAGRAM_SCROLL: scroll feed up/down. Examples: "scroll down", "neeche karo", "upar karo", "scroll"
- CONVERSATION: questions, greetings, jokes, math, time, date, screen dekho, describe screen
- SEARCH: search google/web for something
- CALL_CONTACT: call someone
- UNKNOWN: only if truly unrecognized
""".trimIndent()

    fun buildMessages(userCommand: String): List<Map<String, String>> = listOf(
        mapOf("role" to "system", "content" to systemPrompt),
        mapOf("role" to "user",   "content" to userCommand)
    )

    val geminiSystemPrompt: String = """
You are an Android assistant action planner.
Return ONLY valid JSON:
{"execution_type":"GEMINI","steps":[{"action":"SEARCH","query":"example","target":""}]}
Available actions: SEARCH, OPEN_APP, OPEN_MAP, CALL_CONTACT, SEND_MESSAGE, OPEN_URL, OPEN_SETTINGS
Keep steps to maximum 3.
""".trimIndent()
}
