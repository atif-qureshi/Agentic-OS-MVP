import json
import re
import os
from typing import Any, Dict, Optional

import requests

MODEL_NAME = "openai/gpt-oss-20b"
OPENAI_API = "https://api.openai.com/v1/responses"

# Strict JSON schema the caller requested for structured action output
RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "android_action",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "app": {"type": "string"},
                "intent": {"type": "string"},
                "action": {"type": "string"}
            },
            "required": ["app", "intent", "action"],
            "additionalProperties": False
        }
    }
}


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


def build_context_prompt(context: Dict[str, Any], command: str) -> str:
    app = context.get("app", "Instagram")
    screen = context.get("screen", "feed")
    last_intent = context.get("last_intent", "none")
    last_account = context.get("last_account", "none")
    memory = context.get("memory", {})
    memory_summary = memory.get("summary", "No personal memory yet.")

    return (
        "You are an intent extractor for an Instagram automation assistant. "
        "Return ONLY valid JSON that matches the provided json_schema exactly (no extra text). "
        "Use only supported intents. "
        f"Current app: {app}. Screen: {screen}. Last intent: {last_intent}. "
        f"Last account: {last_account}. Memory: {memory_summary}. "
        "If the user says short commands like 'like', 'comment', 'follow', 'scroll', 'story', 'dm', 'reel', 'post' while in Instagram, resolve them against the current screen context. "
        "If targeting the currently open profile use the special value CURRENT_PROFILE for the account entity. "
        f"Command: {command}"
    )


def parse_model_response(raw: str) -> Dict[str, Any]:
    text = raw.strip()
    if not text:
        raise ValueError("Empty model response")

    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise ValueError(f"No valid JSON object found in response: {text}")

    candidate = text[start : end + 1]
    try:
        payload = json.loads(candidate)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON from model: {candidate}") from exc

    # Map to expected structure
    intent = payload.get("intent") or payload.get("action") or payload.get("intent_name") or "UNKNOWN"
    entities = payload.get("entities", {})
    return {"intent": intent, "entities": entities}


def call_groq(command: str, context: Dict[str, Any]) -> Dict[str, Any]:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY not set")

    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

    system_prompt = build_context_prompt(context, command)

    payload = {
        "model": MODEL_NAME,
        # using messages-style input to be consistent with chat-style models
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": command}
        ],
        "response_format": RESPONSE_FORMAT,
        "temperature": 0.0,
    }

    resp = requests.post(OPENAI_API, json=payload, headers=headers, timeout=120)
    resp.raise_for_status()
    body = resp.json()

    # Try different response locations for compatibility
    content = ""
    # OpenAI Responses API may return 'output' with content blocks
    out = body.get("output") or body.get("choices") or []
    if isinstance(out, list) and len(out) > 0:
        first = out[0]
        if isinstance(first, dict):
            # common new Responses API shape
            conts = first.get("content") or first.get("message", {}).get("content") if first.get("message") else None
            if isinstance(conts, list) and len(conts) > 0:
                # find a text block or json block
                for c in conts:
                    if isinstance(c, dict) and c.get("type") == "output_text":
                        content = c.get("text", "")
                        break
                    if isinstance(c, dict) and c.get("type") == "output_json":
                        content = json.dumps(c.get("value", {}))
                        break
                    if isinstance(c, str):
                        content = c
                        break
            elif isinstance(conts, str):
                content = conts
            else:
                # fallback to message.content
                content = first.get("message", {}).get("content", "")
    if not content:
        # last-resort: try choices[0].message.content
        choices = body.get("choices") or []
        if isinstance(choices, list) and len(choices) > 0:
            content = choices[0].get("message", {}).get("content", "")

    if not content:
        raise ValueError(f"No content found in model response: {body}")

    return parse_model_response(content)


def extract_local_intent(command: str) -> Optional[Dict[str, Any]]:
    text = normalize_text(command)

    if not text:
        return None

    if re.search(r"\b(reel|reels|watch instagram reels)\b", text):
        return {"intent": "INSTAGRAM_REEL", "entities": {}}

    if re.search(r"\b(open|launch|start)\b.*\b(instagram|insta)\b|\b(instagram|insta)\b", text):
        return {"intent": "OPEN_APP", "entities": {"app": "Instagram"}}

    if re.search(r"\b(like|heart|pasand|pasaand)\b", text) or "like" in text:
        return {"intent": "INSTAGRAM_LIKE", "entities": {}}

    if re.search(r"\b(comment|comment karo|comment likho|reply)\b", text):
        text_value = re.sub(r"^(comment|reply)\s+", "", text)
        text_value = re.sub(r"\b(karo|likho|kar do)\b", "", text_value)
        return {"intent": "INSTAGRAM_COMMENT", "entities": {"text": text_value.strip() or "Nice photo!"}}

    if re.search(r"\b(follow|follow karo|follow kro)\b", text):
        account = re.sub(r"^(follow|follow karo|follow kro)\s+", "", text)
        account = re.sub(r"\b(karo|kro|kar do)\b", "", account).strip()
        if account in {"this account", "this profile", "current profile", "active profile", "same profile", "jis profile par ho"} or not account:
            return {"intent": "INSTAGRAM_FOLLOW", "entities": {}}
        return {"intent": "INSTAGRAM_FOLLOW", "entities": {"account": account}}

    if re.search(r"\b(unfollow|unfollow karo|unfollow kro)\b", text):
        account = re.sub(r"^(unfollow|unfollow karo|unfollow kro)\s+", "", text)
        account = re.sub(r"\b(karo|kro|kar do)\b", "", account).strip()
        if account in {"this account", "this profile", "current profile", "active profile", "same profile", "jis profile par ho"} or not account:
            return {"intent": "INSTAGRAM_UNFOLLOW", "entities": {}}
        return {"intent": "INSTAGRAM_UNFOLLOW", "entities": {"account": account}}

    if re.search(r"\b(scroll|neeche|upar|feed scroll)\b", text):
        direction = "up" if "up" in text or "upar" in text else "down"
        return {"intent": "INSTAGRAM_SCROLL", "entities": {"direction": direction}}

    if re.search(r"\b(dm|message|msg)\b", text):
        account_match = re.search(r"(to|for)\s+([a-z0-9_\-]+)", text)
        account = account_match.group(2) if account_match else "ali"
        return {"intent": "INSTAGRAM_DM", "entities": {"account": account, "message": "hello"}}

    if re.search(r"\b(reel|reels|watch instagram reels)\b", text):
        return {"intent": "INSTAGRAM_REEL", "entities": {}}

    if re.search(r"\b(story|story lagao|share to story)\b", text):
        return {"intent": "INSTAGRAM_STORY", "entities": {}}

    if re.search(r"\b(post|photo|picture|feed post)\b", text):
        return {"intent": "INSTAGRAM_POST", "entities": {}}

    if re.search(r"\b(profile|my profile|mera profile)\b", text):
        return {"intent": "INSTAGRAM_OPEN_PROFILE", "entities": {}}

    if re.search(r"\b(search|search karo)\b", text):
        return {"intent": "INSTAGRAM_SEARCH", "entities": {}}

    return None


def decide_execution(intent: str) -> str:
    routine_actions = {
        "INSTAGRAM_LIKE",
        "INSTAGRAM_COMMENT",
        "INSTAGRAM_FOLLOW",
        "INSTAGRAM_UNFOLLOW",
        "INSTAGRAM_DM",
        "INSTAGRAM_SCROLL",
        "INSTAGRAM_POST",
        "INSTAGRAM_STORY",
        "INSTAGRAM_REEL",
        "INSTAGRAM_OPEN_PROFILE",
        "INSTAGRAM_SEARCH",
        "OPEN_APP",
    }
    if intent in routine_actions:
        return "LOCAL_ANDROID"
    if intent == "UNKNOWN":
        return "UNKNOWN"
    return "GEMINI"


def process_command(command: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    active_context = context or {"app": "Instagram", "screen": "feed", "last_intent": "none", "last_account": "none", "memory": {"summary": "No personal memory yet."}}

    local_match = extract_local_intent(command)
    if local_match:
        intent = local_match["intent"]
        entities = local_match["entities"]
    else:
        try:
            result = call_ollama(command, active_context)
            intent = result.get("intent", "UNKNOWN")
            entities = result.get("entities", {})
        except Exception:
            intent = "UNKNOWN"
            entities = {}

    execution_type = decide_execution(intent)
    return {
        "intent": intent,
        "entities": entities,
        "execution_type": execution_type,
        "needs_gemini": execution_type == "GEMINI",
        "context": active_context,
    }
