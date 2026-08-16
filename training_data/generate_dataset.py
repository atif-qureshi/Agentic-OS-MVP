"""
Dataset Generator for Agentic OS Instagram Fine-tuning
Generates 500+ training examples covering all Instagram features
Run: python generate_dataset.py
Output: full_training_dataset.jsonl
"""

import json

def make_example(user_input: str, intent: str, entities: dict) -> dict:
    return {
        "messages": [
            {
                "role": "system",
                "content": "Extract Android intent. Return ONLY JSON. No explanation.\nFormat: {\"intent\":\"X\",\"entities\":{}}"
            },
            {"role": "user", "content": user_input},
            {"role": "assistant", "content": json.dumps({"intent": intent, "entities": entities}, ensure_ascii=False)}
        ]
    }

examples = []

# ── INSTAGRAM LIKE ────────────────────────────────────────────────────────────
like_commands = [
    # English
    "like the post", "like this photo", "like this", "hit like",
    "double tap", "heart this post", "tap like", "like this image",
    "give a like", "like the photo", "like this picture", "like it",
    "like this reel", "like this video", "press like",
    # Roman Urdu
    "like karo", "like kro", "like kar do", "is post ko like karo",
    "pasand karo", "dil de do", "dil lagao", "like lagao",
    "ya photo like karo", "insta par like karo", "like maar do",
    "is ko like karo", "post like karo", "like kr do",
    "dil do is ko", "tasveer like karo", "like karna hai",
    "like dena", "like dedo", "like button dabao",
    # Urdu
    "پسند کرو", "لائک کرو", "دل دو",
]
for cmd in like_commands:
    examples.append(make_example(cmd, "INSTAGRAM_LIKE", {}))

# ── INSTAGRAM COMMENT ─────────────────────────────────────────────────────────
comment_commands = [
    ("comment Nice photo", "Nice photo"),
    ("comment Great shot", "Great shot"),
    ("write a comment Love it", "Love it"),
    ("add comment Mashallah", "Mashallah"),
    ("comment Awesome", "Awesome"),
    ("post comment Beautiful", "Beautiful"),
    ("leave a comment Stunning", "Stunning"),
    ("type comment Wow", "Wow"),
    ("comment on this photo", ""),
    ("add a comment here", ""),
    # Roman Urdu
    ("comment karo Bahut acha", "Bahut acha"),
    ("comment likho Zabardast", "Zabardast"),
    ("is post par comment karo Wah wah", "Wah wah"),
    ("comment kar do Superb", "Superb"),
    ("yeh comment dalo Masha Allah", "Masha Allah"),
    ("comment kro Kya baat hai", "Kya baat hai"),
    ("comment dalo Acha hai", "Acha hai"),
    ("comment likhna hai", ""),
    ("comment kar", ""),
    ("comment lagao", ""),
    ("neeche comment karo", ""),
    ("comment daalo Shandar", "Shandar"),
    ("comment karo Ekdum sahi", "Ekdum sahi"),
]
for cmd, text in comment_commands:
    examples.append(make_example(cmd, "INSTAGRAM_COMMENT", {"text": text}))

# ── INSTAGRAM FOLLOW ──────────────────────────────────────────────────────────
follow_commands = [
    ("follow this account", ""),
    ("follow Ali", "Ali"),
    ("follow Ahmed on instagram", "Ahmed"),
    ("start following this user", ""),
    ("follow this person", ""),
    ("follow back", ""),
    ("click follow", ""),
    ("follow Sara", "Sara"),
    ("follow this profile", ""),
    ("follow them", ""),
    # Roman Urdu
    ("follow karo", ""),
    ("follow kro is ko", ""),
    ("Ali ko follow karo", "Ali"),
    ("is account ko follow kar do", ""),
    ("Sara ko follow kro", "Sara"),
    ("follow kar lo", ""),
    ("follow button dabao", ""),
    ("Ahmed ko follow karna hai", "Ahmed"),
    ("is profile ko follow karo", ""),
    ("follow lagao", ""),
]
for cmd, acc in follow_commands:
    ent = {"account": acc} if acc else {}
    examples.append(make_example(cmd, "INSTAGRAM_FOLLOW", ent))

# ── INSTAGRAM UNFOLLOW ────────────────────────────────────────────────────────
unfollow_commands = [
    ("unfollow this account", ""),
    ("unfollow Ali", "Ali"),
    ("stop following", ""),
    ("unfollow Ahmed", "Ahmed"),
    ("remove this follow", ""),
    ("unfollow this person", ""),
    # Roman Urdu
    ("unfollow karo", ""),
    ("unfollow kro is ko", ""),
    ("Ahmed ko unfollow karo", "Ahmed"),
    ("is account ko unfollow kar do", ""),
    ("Sara ko unfollow kro", "Sara"),
    ("unfollow kar lo", ""),
    ("following hatao", ""),
    ("is ko unfollow karna hai", ""),
]
for cmd, acc in unfollow_commands:
    ent = {"account": acc} if acc else {}
    examples.append(make_example(cmd, "INSTAGRAM_UNFOLLOW", ent))

# ── INSTAGRAM POST ────────────────────────────────────────────────────────────
post_commands = [
    ("post this photo with caption Good morning", "Good morning"),
    ("post a photo", ""),
    ("share this image caption Vacation vibes", "Vacation vibes"),
    ("upload photo caption Beautiful day", "Beautiful day"),
    ("post on instagram", ""),
    ("share photo caption Sunset", "Sunset"),
    ("create a post", ""),
    ("new instagram post caption Happy", "Happy"),
    ("post picture caption Friday vibes", "Friday vibes"),
    ("instagram post caption Nature", "Nature"),
    # Roman Urdu
    ("photo post karo caption Good morning", "Good morning"),
    ("insta par post karo", ""),
    ("photo share karo", ""),
    ("ya tasveer post karo", ""),
    ("instagram par photo dalo caption Aaj ka din", "Aaj ka din"),
    ("post karo caption Khubsurat", "Khubsurat"),
    ("tasveer share karo caption Maza aa gaya", "Maza aa gaya"),
    ("photo upload karo", ""),
    ("insta post karo caption Zindagi", "Zindagi"),
    ("photo lagao instagram par", ""),
]
for cmd, cap in post_commands:
    examples.append(make_example(cmd, "INSTAGRAM_POST", {"caption": cap, "type": "post"}))

# ── INSTAGRAM STORY ───────────────────────────────────────────────────────────
story_commands = [
    "add to story", "post to story", "share on story",
    "add photo to my story", "create a story", "new story",
    "put on story", "add this to story", "story update",
    "share to story", "post story", "update story",
    # Roman Urdu
    "story par dalo", "story mein add karo", "story post karo",
    "story lagao", "apni story par share karo", "story banana hai",
    "story dalo", "story update karo", "story add kro",
    "is photo ko story mein dalo",
]
for cmd in story_commands:
    examples.append(make_example(cmd, "INSTAGRAM_STORY", {"caption": ""}))

# ── INSTAGRAM REEL ────────────────────────────────────────────────────────────
reel_post_commands = [
    ("post a reel with caption Check this out", "Check this out"),
    ("share a reel caption Fun video", "Fun video"),
    ("upload reel caption Trending", "Trending"),
    ("create reel caption Dance", "Dance"),
    ("post reel caption Viral", "Viral"),
    ("reel post karo caption Dekhte jaao", "Dekhte jaao"),
    ("reel share karo caption Mazedaar", "Mazedaar"),
    ("reel banao caption Naya", "Naya"),
    ("reel upload karo", ""),
    ("insta reel post karo", ""),
]
for cmd, cap in reel_post_commands:
    examples.append(make_example(cmd, "INSTAGRAM_REEL", {"caption": cap}))

# ── OPEN REELS TAB ────────────────────────────────────────────────────────────
reels_tab_commands = [
    "open reels", "go to reels", "show reels", "watch reels",
    "reels tab", "see reels feed", "browse reels",
    "reels kholo", "reels dekhna hai", "reels par jao",
    "reels section kholo", "reels feed",
]
for cmd in reels_tab_commands:
    examples.append(make_example(cmd, "INSTAGRAM_REELS", {}))

# ── INSTAGRAM DM ──────────────────────────────────────────────────────────────
dm_commands = [
    ("send message to Ali saying Hello", "Ali", "Hello"),
    ("dm Sara Hi there", "Sara", "Hi there"),
    ("message Ahmed on instagram", "Ahmed", ""),
    ("send direct message to Ali", "Ali", ""),
    ("dm Ali Kya haal", "Ali", "Kya haal"),
    ("message Sara Good morning", "Sara", "Good morning"),
    ("dm Ahmed Are you coming", "Ahmed", "Are you coming"),
    ("send dm to Ali", "Ali", ""),
    ("direct message Sara", "Sara", ""),
    ("inbox Ali", "Ali", ""),
    # Roman Urdu
    ("Ali ko message karo Hello", "Ali", "Hello"),
    ("Sara ko DM karo Kya haal hai", "Sara", "Kya haal hai"),
    ("Ali ko msg bhejo Aa jao", "Ali", "Aa jao"),
    ("Ahmed ko direct message karo", "Ahmed", ""),
    ("Ali ko DM karna hai", "Ali", ""),
    ("Sara ko message bhejo Acha", "Sara", "Acha"),
    ("Ali ko likhna hai", "Ali", ""),
    ("dm karo Ali ko", "Ali", ""),
    ("inbox mein jao Ali se baat karo", "Ali", ""),
    ("Sara ko msg karo Ji haan", "Sara", "Ji haan"),
]
for cmd, acc, msg in dm_commands:
    examples.append(make_example(cmd, "INSTAGRAM_DM", {"account": acc, "message": msg}))

# ── INSTAGRAM SCROLL ──────────────────────────────────────────────────────────
scroll_commands = [
    ("scroll down", "down"), ("scroll up", "up"),
    ("swipe down", "down"), ("swipe up", "up"),
    ("next post", "down"), ("previous post", "up"),
    ("go down", "down"), ("go up", "up"),
    ("feed scroll down", "down"), ("feed scroll up", "up"),
    ("neeche scroll karo", "down"), ("upar scroll karo", "up"),
    ("neeche jao", "down"), ("upar jao", "up"),
    ("aage barhao", "down"), ("peeche jao", "up"),
    ("feed neeche karo", "down"), ("feed upar karo", "up"),
    ("neeche khisko", "down"), ("upar khisko", "up"),
]
for cmd, dir in scroll_commands:
    examples.append(make_example(cmd, "INSTAGRAM_SCROLL", {"direction": dir}))

# ── INSTAGRAM SEARCH ──────────────────────────────────────────────────────────
search_commands = [
    ("search for food on instagram", "food"),
    ("search hashtag travel", "travel"),
    ("find user Ali", "Ali"),
    ("look up photography", "photography"),
    ("search for fitness", "fitness"),
    ("find account sara123", "sara123"),
    ("search trending", "trending"),
    ("find posts about cars", "cars"),
    ("instagram par search karo food", "food"),
    ("dhundo travel", "travel"),
    ("Ali ka account dhundo", "Ali"),
    ("search karo photography", "photography"),
]
for cmd, q in search_commands:
    examples.append(make_example(cmd, "INSTAGRAM_SEARCH", {"query": q}))

# ── INSTAGRAM OPEN PROFILE ────────────────────────────────────────────────────
profile_commands = [
    ("go to Ali profile", "Ali"),
    ("open my profile", ""),
    ("visit Sara profile", "Sara"),
    ("view Ahmed profile", "Ahmed"),
    ("show profile", ""),
    ("open profile page", ""),
    ("Ali ki profile kholo", "Ali"),
    ("meri profile dekhao", ""),
    ("Sara ki profile par jao", "Sara"),
    ("Ahmed ka account kholo", "Ahmed"),
    ("profile dekhna hai", ""),
    ("apni profile kholo", ""),
]
for cmd, acc in profile_commands:
    examples.append(make_example(cmd, "INSTAGRAM_OPEN_PROFILE", {"account": acc}))

# ── OPEN APP ──────────────────────────────────────────────────────────────────
apps = [
    ("Instagram", ["open instagram", "launch instagram", "insta kholo", "instagram chalao", "instagram start karo"]),
    ("YouTube", ["open youtube", "youtube kholo", "launch youtube", "youtube start karo", "youtube chalao"]),
    ("WhatsApp", ["open whatsapp", "whatsapp kholo", "launch whatsapp", "whatsapp chalao"]),
    ("Google Maps", ["open maps", "maps kholo", "open google maps", "maps chalao"]),
    ("Settings", ["open settings", "settings kholo", "settings chalao"]),
    ("Gmail", ["open gmail", "gmail kholo", "gmail chalao"]),
    ("Chrome", ["open chrome", "chrome kholo", "browser kholo"]),
    ("Spotify", ["open spotify", "spotify kholo", "music app kholo"]),
    ("Netflix", ["open netflix", "netflix kholo", "netflix chalao"]),
    ("Snapchat", ["open snapchat", "snapchat kholo"]),
    ("TikTok", ["open tiktok", "tiktok kholo"]),
    ("Telegram", ["open telegram", "telegram kholo"]),
    ("Facebook", ["open facebook", "facebook kholo"]),
    ("Twitter", ["open twitter", "twitter kholo"]),
    ("Calculator", ["open calculator", "calculator kholo"]),
    ("Camera", ["open camera", "camera kholo"]),
]
for app, cmds in apps:
    for cmd in cmds:
        examples.append(make_example(cmd, "OPEN_APP", {"app": app}))

# ── CALL CONTACT ──────────────────────────────────────────────────────────────
call_commands = [
    ("call Ali", "Ali"), ("call Sara", "Sara"), ("call Ahmed", "Ahmed"),
    ("phone karo Ali ko", "Ali"), ("Ali ko call karo", "Ali"),
    ("ring Sara", "Sara"), ("Sara ko phone karo", "Sara"),
    ("call mom", "mom"), ("call dad", "dad"),
    ("Ahmed ko call karna hai", "Ahmed"),
]
for cmd, contact in call_commands:
    examples.append(make_example(cmd, "CALL_CONTACT", {"contact": contact}))

# ── SEARCH ────────────────────────────────────────────────────────────────────
search_web_commands = [
    ("search for restaurants", "restaurants"),
    ("search hospitals near me", "hospitals near me"),
    ("google biryani recipe", "biryani recipe"),
    ("search weather today", "weather today"),
    ("find pizza near me", "pizza near me"),
    ("search for news", "news"),
    ("dhundo hospital", "hospital"),
    ("google karo recipe", "recipe"),
]
for cmd, q in search_web_commands:
    examples.append(make_example(cmd, "SEARCH", {"query": q}))

# ── CONVERSATION ──────────────────────────────────────────────────────────────
conv_commands = [
    ("what time is it", "what time is it"),
    ("who are you", "who are you"),
    ("tell me a joke", "tell me a joke"),
    ("what is 25 times 4", "what is 25 times 4"),
    ("what can you do", "what can you do"),
    ("how are you", "how are you"),
    ("good morning", "good morning"),
    ("hello", "hello"),
    ("thank you", "thank you"),
    ("what is the weather", "what is the weather"),
    ("kya haal hai", "kya haal hai"),
    ("aaj ka mausam", "aaj ka mausam"),
    ("kon ho tum", "kon ho tum"),
    ("kya kar sakte ho", "kya kar sakte ho"),
    ("kitne baje hain", "kitne baje hain"),
    ("kal kaun sa din hai", "kal kaun sa din hai"),
    ("aaj ki date kya hai", "aaj ki date kya hai"),
    ("shukriya", "shukriya"),
    ("what is 100 divided by 5", "what is 100 divided by 5"),
    ("describe my screen", "describe my screen"),
]
for cmd, q in conv_commands:
    examples.append(make_example(cmd, "CONVERSATION", {"query": q}))

# ── OPEN SETTINGS ─────────────────────────────────────────────────────────────
for cmd in ["open settings", "settings kholo", "go to settings", "phone settings"]:
    examples.append(make_example(cmd, "OPEN_SETTINGS", {}))

# ── UNKNOWN ───────────────────────────────────────────────────────────────────
for cmd in ["xyzzy boo", "asdfgh qwerty", "random words here", "blah blah", "123 abc xyz"]:
    examples.append(make_example(cmd, "UNKNOWN", {}))

# ── Write output ──────────────────────────────────────────────────────────────
output_file = "full_training_dataset.jsonl"
with open(output_file, "w", encoding="utf-8") as f:
    for ex in examples:
        f.write(json.dumps(ex, ensure_ascii=False) + "\n")

print(f"✅ Generated {len(examples)} training examples")
print(f"📁 Saved to: {output_file}")

# Count by intent
from collections import Counter
intent_counts = Counter()
for ex in examples:
    assistant_msg = ex["messages"][2]["content"]
    intent = json.loads(assistant_msg)["intent"]
    intent_counts[intent] += 1

print("\n📊 Examples per intent:")
for intent, count in sorted(intent_counts.items(), key=lambda x: -x[1]):
    print(f"  {intent}: {count}")
