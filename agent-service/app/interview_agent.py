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
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

from app.config import settings
from app.graph import build_interview_graph
from app import knowledge_base
from app.observability import trace_interview
from app.state import InterviewContext
from app.tools import close_http_client, start_http_client


checkpoint_path = Path(settings.checkpoint_db_path)
checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
connection: aiosqlite.Connection | None = None
checkpoint_pool: AsyncConnectionPool | None = None
checkpointer: AsyncSqliteSaver | AsyncPostgresSaver | None = None
interview_graph = None


def _config(thread_id: str) -> dict:
    return {
        "configurable": {"thread_id": thread_id},
        "recursion_limit": settings.agent_recursion_limit,
        "max_concurrency": settings.agent_max_concurrency,
    }


async def startup() -> None:
    global connection, checkpoint_pool, checkpointer, interview_graph
    if interview_graph is not None:
        return
    await knowledge_base.startup()
    if settings.checkpoint_backend.lower() == "postgres":
        checkpoint_pool = AsyncConnectionPool(
            conninfo=settings.postgres_url,
            min_size=1,
            max_size=max(4, settings.agent_max_concurrency),
            kwargs={
                "autocommit": True,
                "prepare_threshold": 0,
                "row_factory": dict_row,
            },
            open=False,
        )
        await checkpoint_pool.open()
        checkpointer = AsyncPostgresSaver(checkpoint_pool)
    else:
        connection = await aiosqlite.connect(str(checkpoint_path))
        checkpointer = AsyncSqliteSaver(connection)
    await checkpointer.setup()
    interview_graph = build_interview_graph(checkpointer=checkpointer)
    await start_http_client()


async def shutdown() -> None:
    global connection, checkpoint_pool, checkpointer, interview_graph
    await close_http_client()
    await knowledge_base.shutdown()
    if connection is not None:
        await connection.close()
    if checkpoint_pool is not None:
        await checkpoint_pool.close()
    connection = None
    checkpoint_pool = None
    checkpointer = None
    interview_graph = None


def _graph():
    if interview_graph is None:
        raise RuntimeError("Interview agent is not initialized")
    return interview_graph


def _message_text(message: object) -> str:
    content = getattr(message, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            str(item.get("text", ""))
            for item in content
            if isinstance(item, dict) and item.get("type") == "text"
        )
    return ""


def _stream_chunks(text: str, size: int = 8) -> list[str]:
    """Split a validated question into small SSE chunks without changing it."""
    return [text[index:index + size] for index in range(0, len(text), size)]


async def initialize_thread(
    thread_id: str,
    system_prompt: str,
    greeting: str,
    interview_mode: str = "standard",
    position_code: str = "GENERAL",
) -> None:
    config = _config(thread_id)
    if (await _graph().aget_state(config)).values.get("messages"):
        return
    await _graph().aupdate_state(
        config,
        {
            "messages": [AIMessage(content=greeting)],
            "system_prompt": system_prompt,
            "interview_mode": interview_mode,
            "position_code": position_code,
            "interview_stage": "opening",
            "question_count": 0,
            "project_question_count": 0,
            "fundamental_question_count": 0,
            "project_coverage": {},
            "stage_transition_pending": False,
            "covered_skills": [],
            "covered_topics": [],
            "skipped_topics": [],
            "fundamental_skill_plan": [],
            "fundamental_skill_cursor": 0,
            "skill_question_counts": {},
            "technical_profile": "",
            "asked_questions": [],
            "resume_loaded": False,
            "profile_loaded": False,
            "context_prepared": False,
            "resume_context": "",
            "profile_context": "",
            "prepared_context": "",
            "rag_context": "",
            "rag_query": "",
            "rag_retrieved": False,
            "rag_result_count": 0,
            "question_candidates": [],
            "selected_question": {},
            "context_errors": [],
            "summary": "",
            "summary_model_calls": 0,
            "transcript": [{"role": "assistant", "content": greeting}],
            "total_model_calls": 0,
            "finalize_requested": False,
            "evaluation_dimensions": [],
            "dimension_scores": [],
            "evaluation_model_calls": 0,
        },
    )


async def stream_reply(
    thread_id: str,
    user_id: int,
    resume_id: int,
    interview_mode: str,
    position_code: str,
    message: str,
) -> AsyncIterator[dict[str, Any]]:
    context = InterviewContext(
        user_id=user_id,
        resume_id=resume_id,
        interview_mode=interview_mode,
        position_code=position_code,
        thread_id=thread_id,
    )
    seen_tool_calls: set[str] = set()
    streamed_content = ""
    with trace_interview(thread_id):
        async for stream_mode, payload in _graph().astream(
            {
                "messages": [HumanMessage(content=message)],
                "transcript": [{"role": "user", "content": message}],
                "position_code": position_code,
                "rag_context": "",
                "rag_query": "",
                "rag_retrieved": False,
                "rag_result_count": 0,
            },
            config=_config(thread_id),
            context=context,
            stream_mode=["messages", "custom", "updates"],
        ):
            if stream_mode == "custom":
                if not isinstance(payload, dict):
                    continue
                if payload.get("type") == "token" and payload.get("content"):
                    content = str(payload["content"])
                    streamed_content += content
                    yield {"type": "token", "content": content}
                continue

            if stream_mode == "messages":
                chunk, metadata = payload
                if isinstance(chunk, AIMessageChunk):
                    for tool_call in chunk.tool_call_chunks:
                        tool_name = tool_call.get("name")
                        tool_id = tool_call.get("id") or tool_name
                        if tool_name and tool_id and tool_id not in seen_tool_calls:
                            seen_tool_calls.add(tool_id)
                            yield {"type": "tool_start", "tool": tool_name}
                if isinstance(chunk, ToolMessage):
                    yield {"type": "tool_done", "tool": chunk.name or "tool"}
                continue

            interviewer_update = payload.get("interviewer") if isinstance(payload, dict) else None
            if not isinstance(interviewer_update, dict):
                continue
            messages = interviewer_update.get("messages", [])
            final_message = messages[-1] if messages else None
            final_content = _message_text(final_message)
            if not final_content:
                continue

            # Structured-output calls often have no textual AIMessageChunk. The
            # validated question is available in the node update, so stream only
            # the part that has not already arrived through the model stream.
            remaining = (
                final_content[len(streamed_content):]
                if final_content.startswith(streamed_content)
                else final_content
            )
            if remaining:
                for content in _stream_chunks(remaining):
                    streamed_content += content
                    yield {"type": "token", "content": content}

    snapshot = await _graph().aget_state(_config(thread_id))
    values = snapshot.values
    yield {
        "type": "state",
        "stage": values.get("interview_stage", "opening"),
        "question_count": values.get("question_count", 0),
        "project_question_count": values.get("project_question_count", 0),
        "fundamental_question_count": values.get("fundamental_question_count", 0),
        "current_skill": values.get("current_skill"),
        "current_topic": values.get("current_topic"),
        "rag_retrieved": values.get("rag_retrieved", False),
        "rag_result_count": values.get("rag_result_count", 0),
        "question_source_type": (
            values.get("selected_question", {}).get("source_type")
            if isinstance(values.get("selected_question"), dict)
            else None
        ),
        "retrieval_score": (
            values.get("selected_question", {}).get("retrieval_score")
            if isinstance(values.get("selected_question"), dict)
            else None
        ),
    }


async def finalize_thread(
    thread_id: str,
    user_id: int,
    resume_id: int,
    interview_mode: str,
    position_code: str = "GENERAL",
) -> dict[str, Any]:
    context = InterviewContext(
        user_id=user_id,
        resume_id=resume_id,
        interview_mode=interview_mode,
        position_code=position_code,
        thread_id=thread_id,
    )
    with trace_interview(thread_id):
        result = await _graph().ainvoke(
            {"finalize_requested": True},
            config=_config(thread_id),
            context=context,
        )
    report = result.get("final_report")
    if not isinstance(report, dict):
        raise RuntimeError("Interview graph did not produce a final report")
    return report


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
        "project_question_count": values.get("project_question_count", 0),
        "fundamental_question_count": values.get("fundamental_question_count", 0),
        "current_skill": values.get("current_skill"),
        "current_topic": values.get("current_topic"),
        "rag_retrieved": values.get("rag_retrieved", False),
        "rag_result_count": values.get("rag_result_count", 0),
        "question_source_type": (
            values.get("selected_question", {}).get("source_type")
            if isinstance(values.get("selected_question"), dict)
            else None
        ),
        "retrieval_score": (
            values.get("selected_question", {}).get("retrieval_score")
            if isinstance(values.get("selected_question"), dict)
            else None
        ),
        "selected_question": values.get("selected_question", {}),
        "asked_questions": values.get("asked_questions", []),
        "covered_skills": values.get("covered_skills", []),
        "covered_topics": values.get("covered_topics", []),
        "skipped_topics": values.get("skipped_topics", []),
        "fundamental_skill_plan": values.get("fundamental_skill_plan", []),
        "fundamental_skill_cursor": values.get("fundamental_skill_cursor", 0),
        "skill_question_counts": values.get("skill_question_counts", {}),
        "interview_mode": values.get("interview_mode", "standard"),
        "resume_loaded": values.get("resume_loaded", False),
        "profile_loaded": values.get("profile_loaded", False),
        "context_prepared": values.get("context_prepared", False),
        "evaluation_model_calls": values.get("evaluation_model_calls", 0),
        "summary": values.get("summary", ""),
        "summary_model_calls": values.get("summary_model_calls", 0),
        "final_report": values.get("final_report"),
        "transcript": transcript,
        "messages": transcript,
        "has_interrupt": bool(snapshot.interrupts),
    }
