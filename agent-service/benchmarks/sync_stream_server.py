import json

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse

from app import llm_client


app = FastAPI(title="Pre-migration synchronous stream benchmark server")


@app.post("/llm/chat/stream")
async def chat_stream(request: Request) -> StreamingResponse:
    payload = await request.json()
    messages = llm_client.normalize_messages(payload.get("messages", []))
    model_name = payload.get("model")

    def event_stream():
        for chunk in llm_client._model(model_name).stream(
            llm_client._to_langchain_messages(messages)
        ):
            content = chunk.content
            if isinstance(content, str) and content:
                safe_chunk = content.replace("\r", "").replace("\n", "\\n")
                yield f"data: {safe_chunk}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")

