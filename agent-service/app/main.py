import json
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse

from app import llm_client
from app import interview_agent
from app.config import settings
from app.observability import tracing_enabled
from app.schemas import (
    AgentChatRequest,
    AgentThreadRequest,
    ChatRequest,
    ChatResponse,
    EvaluationRequest,
    EvaluationResponse,
    InterviewEvaluationRequest,
    InterviewSummaryRequest,
    ResumeDiagnosisRequest,
    SummaryRequest,
    VisionRequest,
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await interview_agent.startup()
    try:
        yield
    finally:
        await interview_agent.shutdown()


app = FastAPI(title="Interview Agent Service", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {
        "status": "ok",
        "model": settings.model_name,
        "api_key_configured": bool(settings.model_api_key),
        "langgraph": "explicit-state-graph",
        "checkpointer": "sqlite",
        "langsmith_tracing": tracing_enabled(),
    }


@app.post("/llm/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    return ChatResponse(content=llm_client.chat(request.messages, request.model))


@app.post("/llm/chat/stream")
async def chat_stream(request: Request) -> StreamingResponse:
    body = await request.body()
    try:
        if not body:
            raise ValueError("empty request body")
        payload = json.loads(body.decode("utf-8"))
        messages = llm_client.normalize_messages(payload.get("messages", []))
        model = payload.get("model")
        if not messages:
            raise ValueError("messages is empty")
    except Exception as exc:
        preview = body[:300].decode("utf-8", errors="replace") if body else "<empty>"
        logger.exception("Invalid stream request body, preview=%s", preview)
        safe_error = str(exc).replace("\r", "").replace("\n", " ")

        def error_stream():
            yield f"data: [AI_ERROR] Invalid stream request body: {safe_error}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(error_stream(), media_type="text/event-stream")

    async def event_stream():
        try:
            async for chunk in llm_client.stream_chat(messages, model):
                safe_chunk = chunk.replace("\r", "").replace("\n", "\\n")
                yield f"data: {safe_chunk}\n\n"
        except Exception as exc:
            safe_error = str(exc).replace("\r", "").replace("\n", " ")
            yield f"data: [AI_ERROR] {safe_error}\n\n"
        finally:
            yield "data: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/agent/threads")
async def initialize_agent_thread(request: AgentThreadRequest) -> dict[str, str]:
    await interview_agent.initialize_thread(
        request.thread_id,
        request.system_prompt,
        request.greeting,
    )
    return {"status": "initialized", "thread_id": request.thread_id}


@app.delete("/agent/threads/{thread_id}")
async def delete_agent_thread(thread_id: str) -> dict[str, str]:
    await interview_agent.delete_thread(thread_id)
    return {"status": "deleted", "thread_id": thread_id}


@app.get("/agent/threads/{thread_id}")
async def get_agent_thread(thread_id: str) -> dict:
    return await interview_agent.get_thread_state(thread_id)


@app.post("/agent/interview/stream")
async def interview_stream(request: AgentChatRequest) -> StreamingResponse:
    async def event_stream():
        try:
            async for event in interview_agent.stream_reply(
                request.thread_id,
                request.user_id,
                request.resume_id,
                request.message,
            ):
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
        except Exception as exc:
            logger.exception("Interview agent stream failed, thread_id=%s", request.thread_id)
            safe_error = str(exc).replace("\r", "").replace("\n", " ")
            yield f"data: {json.dumps({'type': 'error', 'message': safe_error}, ensure_ascii=False)}\n\n"
        finally:
            yield 'data: {"type":"done"}\n\n'

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/llm/vision", response_model=ChatResponse)
def vision(request: VisionRequest) -> ChatResponse:
    return ChatResponse(content=llm_client.vision_chat(request.messages, request.model))


@app.post("/resume/diagnose", response_model=ChatResponse)
def diagnose_resume(request: ResumeDiagnosisRequest) -> ChatResponse:
    return ChatResponse(
        content=llm_client.resume_diagnosis(
            request.parsed_text,
            request.image_base64,
            request.image_mime_type,
        )
    )


@app.post("/llm/summarize", response_model=ChatResponse)
def summarize(request: SummaryRequest) -> ChatResponse:
    return ChatResponse(content=llm_client.chat(request.messages))


@app.post("/interview/summarize", response_model=ChatResponse)
def summarize_interview(request: InterviewSummaryRequest) -> ChatResponse:
    return ChatResponse(
        content=llm_client.summarize_interview(
            request.round_content,
            request.existing_summary,
            request.max_chars,
        )
    )


@app.post("/llm/evaluate", response_model=EvaluationResponse)
def evaluate(request: EvaluationRequest) -> EvaluationResponse:
    return llm_client.evaluation(request.messages)


@app.post("/interview/evaluate", response_model=ChatResponse)
def evaluate_interview(request: InterviewEvaluationRequest) -> ChatResponse:
    return ChatResponse(content=llm_client.interview_evaluation(request.messages))
