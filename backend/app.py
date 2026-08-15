from typing import Any, Dict, Optional
import os

from fastapi import FastAPI
from pydantic import BaseModel, Field

# Load local .env for development (if present) — safe to keep out of version control
try:
    from dotenv import load_dotenv
    load_dotenv()
except Exception:
    # python-dotenv not installed or no .env file present — continue (env vars will be used if set)
    pass

from intent_service import process_command


app = FastAPI(title="Agentic OS Python Backend", version="1.0.0")


class CommandRequest(BaseModel):
    text: str = Field(..., min_length=1)
    context: Optional[Dict[str, Any]] = Field(default_factory=lambda: {
        "app": "Instagram",
        "screen": "feed",
        "last_intent": "none",
        "last_account": "none",
        "memory": {"summary": "No personal memory yet."},
    })


@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "ok", "service": "agentic-os-python-backend"}


@app.post("/process")
def process_endpoint(request: CommandRequest) -> Dict[str, Any]:
    result = process_command(request.text, request.context)
    return {
        "success": True,
        "data": result,
    }


@app.post("/intent")
def intent_endpoint(request: CommandRequest) -> Dict[str, Any]:
    result = process_command(request.text, request.context)
    return {
        "intent": result["intent"],
        "entities": result["entities"],
        "execution_type": result["execution_type"],
        "needs_gemini": result["needs_gemini"],
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=False)
