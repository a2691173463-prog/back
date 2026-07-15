import json
from urllib.parse import quote

import httpx
from langchain_core.messages import ToolMessage
from langchain.tools import ToolRuntime, tool
from langgraph.types import Command

from app.config import settings
from app.state import InterviewContext, InterviewState


_http_client: httpx.AsyncClient | None = None


async def start_http_client() -> None:
    global _http_client
    if _http_client is not None:
        return
    _http_client = httpx.AsyncClient(
        base_url=settings.java_backend_url.rstrip("/"),
        timeout=httpx.Timeout(10.0),
        limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
        headers={
            "Accept": "application/json",
            "X-Agent-Secret": settings.internal_agent_secret,
        },
    )


async def close_http_client() -> None:
    global _http_client
    if _http_client is None:
        return
    await _http_client.aclose()
    _http_client = None


async def _get_json(path: str) -> object:
    if _http_client is None:
        await start_http_client()
    try:
        response = await _http_client.get(path)
        response.raise_for_status()
        return response.json()
    except httpx.HTTPStatusError as exc:
        detail = exc.response.text[:300]
        return {
            "error": f"Java backend returned HTTP {exc.response.status_code}: {detail}"
        }
    except httpx.RequestError as exc:
        return {"error": f"Java backend is unavailable: {exc}"}


@tool
async def get_resume_summary(
    runtime: ToolRuntime[InterviewContext, InterviewState],
) -> Command:
    """获取当前候选人的简历摘要、项目经历和技术栈。在根据简历提问前使用。"""
    result = await _get_json(
        f"/internal/agent/resumes/{runtime.context.resume_id}/summary"
    )
    content = json.dumps(result, ensure_ascii=False)
    return Command(
        update={
            "resume_loaded": "error" not in result if isinstance(result, dict) else True,
            "messages": [
                ToolMessage(content=content, tool_call_id=runtime.tool_call_id)
            ],
        }
    )


@tool
async def get_user_weak_skills(
    runtime: ToolRuntime[InterviewContext, InterviewState],
) -> Command:
    """查询当前候选人历史面试中的薄弱技能、评分和评语，用于针对性追问。"""
    result = await _get_json(
        f"/internal/agent/users/{runtime.context.user_id}/weak-skills"
    )
    content = json.dumps(result, ensure_ascii=False)
    return Command(
        update={
            "profile_loaded": "error" not in result if isinstance(result, dict) else True,
            "messages": [
                ToolMessage(content=content, tool_call_id=runtime.tool_call_id)
            ],
        }
    )


@tool
async def search_question_bank(
    keyword: str,
    runtime: ToolRuntime[InterviewContext, InterviewState],
) -> Command:
    """根据技术关键词检索相关面试题和参考答案。keyword 应是 Java、Redis 等技术词。"""
    result = await _get_json(
        f"/internal/agent/questions/search?keyword={quote(keyword)}"
    )
    covered_skills = list(runtime.state.get("covered_skills", []))
    if keyword not in covered_skills:
        covered_skills.append(keyword)
    return Command(
        update={
            "current_skill": keyword,
            "covered_skills": covered_skills,
            "messages": [
                ToolMessage(
                    content=json.dumps(result, ensure_ascii=False),
                    tool_call_id=runtime.tool_call_id,
                )
            ],
        }
    )


INTERVIEW_TOOLS = [
    get_resume_summary,
    get_user_weak_skills,
    search_question_bank,
]
