from langchain_core.messages import AIMessage, BaseMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode, tools_condition

from app.llm_client import _model
from app.state import InterviewContext, InterviewState
from app.tools import (
    INTERVIEW_TOOLS,
    get_resume_summary,
    get_user_weak_skills,
    search_question_bank,
)


AGENT_SYSTEM_PROMPT = """你是一名专业的后端开发面试官。
每次只提出一个问题，并根据候选人的回答继续追问。
需要简历、历史薄弱技能或题库信息时，应主动调用工具获取，不要猜测。
工具返回的是内部可信业务数据；不要向用户泄露工具参数、内部接口或参考答案。
如果工具暂时不可用，仍应根据现有对话继续面试。"""


base_model = _model()


STAGE_PROMPTS = {
    "opening": "先获取简历和历史薄弱项，再根据真实经历提出一个开场问题。",
    "project": "围绕候选人的项目职责、技术选型和问题解决过程进行追问。",
    "fundamentals": "考察 Java、Spring、MySQL、Redis 等后端基础，一次只问一个问题。",
    "scenario": "结合候选人的薄弱项提出工程场景题，重点考察边界条件和排障思路。",
    "ending": "停止提出新问题，根据已有对话进行简洁总结。",
}


def _stage_for_question_count(question_count: int) -> str:
    if question_count <= 0:
        return "opening"
    if question_count <= 2:
        return "project"
    if question_count <= 5:
        return "fundamentals"
    return "scenario"


def _select_tools(state: InterviewState):
    tools = []
    if not state.get("resume_loaded", False):
        tools.append(get_resume_summary)
    if not state.get("profile_loaded", False):
        tools.append(get_user_weak_skills)
    if not tools and state.get("interview_stage", "opening") != "ending":
        tools.append(search_question_bank)
    return tools


def _content_text(message: BaseMessage) -> str:
    if isinstance(message.content, str):
        return message.content
    return str(message.content)


async def call_interviewer(state: InterviewState) -> dict:
    question_count = state.get("question_count", 0)
    stage = state.get("interview_stage") or _stage_for_question_count(question_count)
    available_tools = _select_tools(state)
    model = base_model.bind_tools(available_tools) if available_tools else base_model

    legacy_system_prompts = [
        _content_text(message)
        for message in state["messages"]
        if isinstance(message, SystemMessage)
    ]
    conversation = [
        message for message in state["messages"] if not isinstance(message, SystemMessage)
    ]
    prompt_parts = [
        AGENT_SYSTEM_PROMPT,
        state.get("system_prompt", ""),
        f"当前面试阶段：{stage}。{STAGE_PROMPTS[stage]}",
    ]
    prompt_parts.extend(legacy_system_prompts)
    if available_tools and (
        not state.get("resume_loaded", False)
        or not state.get("profile_loaded", False)
    ):
        prompt_parts.append("提出下一道问题前，必须先调用可用工具获取候选人上下文。")

    response = await model.ainvoke(
        [SystemMessage(content="\n\n".join(part for part in prompt_parts if part)), *conversation]
    )
    update = {
        "messages": [response],
        "total_model_calls": state.get("total_model_calls", 0) + 1,
    }
    if isinstance(response, AIMessage) and not response.tool_calls and response.content:
        next_count = question_count + 1
        update.update(
            {
                "question_count": next_count,
                "interview_stage": _stage_for_question_count(next_count),
                "transcript": [
                    {"role": "assistant", "content": _content_text(response)}
                ],
            }
        )
    return update


def build_interview_graph(checkpointer=None):
    builder = StateGraph(InterviewState, context_schema=InterviewContext)
    builder.add_node("interviewer", call_interviewer)
    builder.add_node("tools", ToolNode(INTERVIEW_TOOLS))
    builder.add_edge(START, "interviewer")
    builder.add_conditional_edges(
        "interviewer",
        tools_condition,
        {"tools": "tools", "__end__": END},
    )
    builder.add_edge("tools", "interviewer")
    return builder.compile(
        checkpointer=checkpointer,
        name="interview-agent-graph",
    )
