import asyncio
import json
import os
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse


app = FastAPI(title="Deterministic OpenAI-compatible benchmark server")


def _chunk(model: str, content: str, finish_reason: str | None = None) -> str:
    payload = {
        "id": f"chatcmpl-{uuid.uuid4().hex}",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": model,
        "choices": [
            {
                "index": 0,
                "delta": {"content": content} if content else {},
                "finish_reason": finish_reason,
            }
        ],
    }
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


@app.post("/v1/chat/completions")
async def chat_completions(request: Request):
    body = await request.json()
    model = body.get("model", "benchmark-model")
    first_delay = float(os.getenv("MOCK_FIRST_TOKEN_DELAY_MS", "150")) / 1000
    token_delay = float(os.getenv("MOCK_TOKEN_DELAY_MS", "40")) / 1000
    token_count = int(os.getenv("MOCK_TOKEN_COUNT", "12"))
    tokens = [f"t{i:02d}" for i in range(token_count)]

    if not body.get("stream"):
        await asyncio.sleep(first_delay + token_delay * token_count)
        return JSONResponse(
            {
                "id": f"chatcmpl-{uuid.uuid4().hex}",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": model,
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": "".join(tokens)},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": token_count,
                    "total_tokens": 10 + token_count,
                },
            }
        )

    async def event_stream():
        await asyncio.sleep(first_delay)
        for token in tokens:
            yield _chunk(model, token)
            await asyncio.sleep(token_delay)
        yield _chunk(model, "", "stop")
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")

