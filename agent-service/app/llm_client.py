from collections.abc import AsyncIterator
from typing import Any

from langchain.chat_models import init_chat_model
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage

from app.config import settings
from app.schemas import ChatMessage, EvaluationResponse


def _model(model_name: str | None = None):
    return init_chat_model(
        model=model_name or settings.model_name,
        model_provider="openai",
        base_url=settings.model_base_url,
        api_key=settings.model_api_key,
    )


def _to_langchain_messages(messages: list[ChatMessage]) -> list[BaseMessage]:
    result: list[BaseMessage] = []
    for msg in messages:
        if msg.role == "system":
            result.append(SystemMessage(content=msg.content))
        elif msg.role == "assistant":
            result.append(AIMessage(content=msg.content))
        else:
            result.append(HumanMessage(content=msg.content))
    return result


def normalize_messages(raw_messages: list[dict[str, Any]]) -> list[ChatMessage]:
    return [ChatMessage.model_validate(message) for message in raw_messages]


def chat(messages: list[ChatMessage], model_name: str | None = None) -> str:
    response = _model(model_name).invoke(_to_langchain_messages(messages))
    return str(response.content)


async def stream_chat(
    messages: list[ChatMessage], model_name: str | None = None
) -> AsyncIterator[str]:
    async for chunk in _model(model_name).astream(_to_langchain_messages(messages)):
        content = chunk.content
        if isinstance(content, str) and content:
            yield content


def vision_chat(messages: list[ChatMessage], model_name: str | None = None) -> str:
    response = _model(model_name or settings.vision_model_name).invoke(_to_langchain_messages(messages))
    return str(response.content)


def resume_diagnosis(parsed_text: str, image_base64: str | None, image_mime_type: str) -> str:
    prompt = (
        "你是一个资深的程序员面试官和简历视觉排版及内容评估专家。"
        "请根据提供的简历内容，从以下两个维度进行深度分析：\n"
        "1. 视觉排版设计（如排版格式、整体色调、字号间距、模块布局、是否清晰专业美观）；\n"
        "2. 简历实际文字内容与专业度（技术栈、项目深度、经历描述等）。\n\n"
        "请指出它的优点、缺点并给出具体的优化建议。请严格以 JSON 格式返回，不要附带任何 markdown 标记或冗余废话，JSON 结构定义如下：\n"
        "{\n"
        "  \"advantages\": [\"优势1\", \"优势2\"],\n"
        "  \"disadvantages\": [\"缺点1\", \"缺点2\"],\n"
        "  \"suggestions\": [\"建议1\", \"建议2\"],\n"
        "  \"extracted_text\": \"如果是扫描版或图片型 PDF 导致文本为空，请在此提供识别出的完整简历文字内容；如果已经提取成功，可留空\"\n"
        "}\n\n"
        f"PDF 文本提取结果：\n{parsed_text or '（PDFBox 未提取到文本，请重点根据图片识别内容）'}"
    )
    if image_base64:
        message = ChatMessage(
            role="user",
            content=[
                {"type": "text", "text": prompt},
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{image_mime_type};base64,{image_base64}"},
                },
            ],
        )
        return vision_chat([message])
    return chat([ChatMessage(role="user", content=prompt)])


def optimize_resume_section(section_type: str, target_role: str, content: str) -> str:
    prompt = (
        "你是校招简历内容编辑专家。请优化下面的简历段落，使其更适合目标岗位，"
        "突出行动、技术方案、结果和个人贡献。\n"
        "硬性要求：\n"
        "1. 不得虚构原文没有出现的公司、项目、技术、数字或成果；\n"
        "2. 信息不足时保留真实表达，不要擅自补造量化数据；\n"
        "3. 使用简洁的中文简历语言，优先采用动词开头；\n"
        "4. 只输出优化后的正文，不要解释、标题、Markdown 代码块或客套话。\n\n"
        f"目标岗位：{target_role or '校招岗位'}\n"
        f"内容类型：{section_type or '项目经历'}\n"
        f"原始内容：\n{content.strip()}"
    )
    return chat([ChatMessage(role="user", content=prompt)]).strip()


def summarize_interview(round_content: str, existing_summary: str | None, max_chars: int = 150) -> str:
    if existing_summary:
        prompt = (
            "已有面试背景摘要：\n"
            f"{existing_summary}\n\n"
            "新移出的对话内容：\n"
            f"{round_content}\n"
            "请将新移出的对话内容合并更新至已有的摘要中，生成一段更新后的连贯面试背景描述（增量摘要合并）。\n"
            "要求：\n"
            "1. 融合新旧内容，重点体现：用户熟悉的技术、用户回答的特点、AI追问的要点、用户表现出的薄弱项及遗留问题。\n"
            "2. 保持陈述的连贯性，剔除任何客套寒暄。\n"
            f"3. 仅输出最终更新后的摘要陈述，尽量控制在 {max_chars} 字以内，不要包含任何多余说明或 markdown。"
        )
    else:
        prompt = (
            "请将下面这轮面试对话压缩成一句话。\n"
            "要求：\n"
            "1. 重点保留：用户回答特点、AI追问内容、用户存在的问题或薄弱点。\n"
            "2. 不要保留任何寒暄和客套。\n"
            f"3. 仅输出最终精简的一句话，尽量控制在 {max_chars} 字以内，不要包含任何多余文字或 markdown。\n\n"
            "对话内容：\n"
            f"{round_content}"
        )
    summary = chat([ChatMessage(role="user", content=prompt)]).strip()
    if len(summary) <= max_chars * 2:
        return summary
    compress_prompt = (
        "你是一个面试记忆整理专家。当前的面试背景摘要过长，"
        f"请在保留所有核心结论（用户擅长项、薄弱项、表达特点）的前提下，将其精简压缩至 {max_chars} 字以内。\n"
        f"当前摘要：\n{summary}"
    )
    return chat([ChatMessage(role="user", content=compress_prompt)]).strip()


def evaluation(messages: list[ChatMessage]) -> EvaluationResponse:
    raw = chat(messages)
    cleaned = _strip_markdown_json(raw)
    try:
        return EvaluationResponse.model_validate_json(cleaned)
    except Exception:
        return EvaluationResponse(score=60, evaluation=raw, skills=[])


def interview_evaluation(messages: list[ChatMessage]) -> str:
    qwen_messages = list(messages)
    eval_prompt = (
        "请根据以上整个面试的对话历史，对候选人的表现进行综合评估打分（0-100分），并给出面试建议，"
        "同时提取候选人在主要技术栈和通用能力上的技能画像评分（分值为 1.0 - 5.0，请保留一位小数）。"
        "请严格以 JSON 格式返回评估报告，只包含 JSON 内容，不要包含任何 markdown 块或冗余说明。JSON 格式定义如下：\n"
        "{\n"
        "  \"score\": 85,\n"
        "  \"evaluation\": \"本次面试表现...，亮点是...，改进点是...，学习路线是...\",\n"
        "  \"skills\": [\n"
        "    {\"skillName\": \"Java\", \"score\": 4.0, \"comment\": \"基础扎实，但多线程并发理解偏浅\"},\n"
        "    {\"skillName\": \"Redis\", \"score\": 5.0, \"comment\": \"熟悉哨兵、集群、缓存设计与高并发优化\"},\n"
        "    {\"skillName\": \"Docker\", \"score\": 2.5, \"comment\": \"会用基本命令，但不熟悉多阶段构建与网络配置\"},\n"
        "    {\"skillName\": \"Spring Cloud\", \"score\": 1.0, \"comment\": \"无微服务项目实战经历\"},\n"
        "    {\"skillName\": \"算法\", \"score\": 2.0, \"comment\": \"只会简单的数据结构，不熟悉动态规划等算法\"},\n"
        "    {\"skillName\": \"表达\", \"score\": 3.0, \"comment\": \"表达清晰但不够精炼，专业术语组织欠佳\"}\n"
        "  ]\n"
        "}"
    )
    qwen_messages.append(ChatMessage(role="user", content=eval_prompt))
    return chat(qwen_messages)


def _strip_markdown_json(text: str) -> str:
    value = text.strip()
    if value.startswith("```json"):
        return value[7:].removesuffix("```").strip()
    if value.startswith("```"):
        return value[3:].removesuffix("```").strip()
    return value


def to_jsonable_messages(messages: list[ChatMessage]) -> list[dict[str, Any]]:
    return [message.model_dump() for message in messages]
