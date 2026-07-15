from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

import aiosqlite
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    HumanMessage,
    ToolMessage,
)
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

from app.config import settings
from app.graph import build_interview_graph
from app.observability import trace_interview
from app.state import InterviewContext
from app.tools import close_http_client, start_http_client


checkpoint_path = Path(settings.checkpoint_db_path)
checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
connection: aiosqlite.Connection | None = None
checkpointer: AsyncSqliteSaver | None = None
interview_graph = None


def _config(thread_id: str) -> dict:
    return {"configurable": {"thread_id": thread_id}}


async def startup() -> None:
    global connection, checkpointer, interview_graph
    if interview_graph is not None:
        return
    connection = await aiosqlite.connect(str(checkpoint_path))
    checkpointer = AsyncSqliteSaver(connection)
    await checkpointer.setup()
    interview_graph = build_interview_graph(checkpointer=checkpointer)
    await start_http_client()


async def shutdown() -> None:
    global connection, checkpointer, interview_graph
    await close_http_client()
    if connection is not None:
        await connection.close()
    connection = None
    checkpointer = None
    interview_graph = None


def _graph():
    if interview_graph is None:
        raise RuntimeError("Interview agent is not initialized")
    return interview_graph


async def initialize_thread(thread_id: str, system_prompt: str, greeting: str) -> None:
    config = _config(thread_id)
    if (await _graph().aget_state(config)).values.get("messages"):
        return
    await _graph().aupdate_state(
        config,
        {
            "messages": [AIMessage(content=greeting)],
            "system_prompt": system_prompt,
            "interview_stage": "opening",
            "question_count": 0,
            "covered_skills": [],
            "resume_loaded": False,
            "profile_loaded": False,
            "summary": "",
            "transcript": [{"role": "assistant", "content": greeting}],
            "total_model_calls": 0,
        },
    )


async def stream_reply(
    thread_id: str,
    user_id: int,
    resume_id: int,
    message: str,
) -> AsyncIterator[dict[str, Any]]:
    context = InterviewContext(user_id=user_id, resume_id=resume_id)
    seen_tool_calls: set[str] = set()
    with trace_interview(thread_id):
        async for chunk, metadata in _graph().astream(
            {
                "messages": [HumanMessage(content=message)],
                "transcript": [{"role": "user", "content": message}],
            },
            config=_config(thread_id),
            context=context,
            stream_mode="messages",
        ):
            if (
                isinstance(chunk, AIMessageChunk)
                and metadata.get("langgraph_node") == "interviewer"
                and isinstance(chunk.content, str)
                and chunk.content
            ):
                yield {"type": "token", "content": chunk.content}
            if isinstance(chunk, AIMessageChunk):
                for tool_call in chunk.tool_call_chunks:
                    tool_name = tool_call.get("name")
                    tool_id = tool_call.get("id") or tool_name
                    if tool_name and tool_id and tool_id not in seen_tool_calls:
                        seen_tool_calls.add(tool_id)
                        yield {"type": "tool_start", "tool": tool_name}
            if isinstance(chunk, ToolMessage):
                yield {"type": "tool_done", "tool": chunk.name or "tool"}

    snapshot = await _graph().aget_state(_config(thread_id))
    values = snapshot.values
    yield {
        "type": "state",
        "stage": values.get("interview_stage", "opening"),
        "question_count": values.get("question_count", 0),
        "current_skill": values.get("current_skill"),
    }


async def delete_thread(thread_id: str) -> None:
    if checkpointer is None:
        raise RuntimeError("Interview checkpointer is not initialized")
    await checkpointer.adelete_thread(thread_id)


async def thread_exists(thread_id: str) -> bool:
    return bool((await _graph().aget_state(_config(thread_id))).values.get("messages"))


def _messages_as_transcript(messages: list[Any]) -> list[dict[str, str]]:
    transcript: list[dict[str, str]] = []
    for message in messages:
        if isinstance(message, HumanMessage) and message.content:
            transcript.append({"role": "user", "content": str(message.content)})
        elif (
            isinstance(message, AIMessage)
            and not message.tool_calls
            and message.content
        ):
            transcript.append({"role": "assistant", "content": str(message.content)})
    return transcript


async def get_thread_state(thread_id: str) -> dict[str, Any]:
    snapshot = await _graph().aget_state(_config(thread_id))
    values = snapshot.values
    transcript = values.get("transcript") or _messages_as_transcript(
        values.get("messages", [])
    )
    return {
        "thread_id": thread_id,
        "exists": bool(values.get("messages")),
        "stage": values.get("interview_stage", "opening"),
        "question_count": values.get("question_count", 0),
        "current_skill": values.get("current_skill"),
        "covered_skills": values.get("covered_skills", []),
        "resume_loaded": values.get("resume_loaded", False),
        "profile_loaded": values.get("profile_loaded", False),
        "messages": transcript,
        "has_interrupt": bool(snapshot.interrupts),
    }
