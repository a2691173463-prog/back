from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="allow")

    role: str
    content: Any


class ChatRequest(BaseModel):
    messages: list[ChatMessage]
    model: str | None = None


class AgentThreadRequest(BaseModel):
    thread_id: str
    user_id: int
    resume_id: int
    system_prompt: str
    greeting: str


class AgentChatRequest(BaseModel):
    thread_id: str
    user_id: int
    resume_id: int
    message: str


class ChatResponse(BaseModel):
    content: str


class VisionRequest(BaseModel):
    messages: list[ChatMessage]
    model: str | None = None


class SummaryRequest(BaseModel):
    messages: list[ChatMessage]


class EvaluationRequest(BaseModel):
    messages: list[ChatMessage]


class InterviewEvaluationRequest(BaseModel):
    messages: list[ChatMessage]


class ResumeDiagnosisRequest(BaseModel):
    parsed_text: str = ""
    image_base64: str | None = None
    image_mime_type: str = "image/png"


class ResumeOptimizationRequest(BaseModel):
    section_type: str = Field(default="项目经历", max_length=32)
    target_role: str = Field(default="校招岗位", max_length=100)
    content: str = Field(min_length=1, max_length=6000)


class InterviewSummaryRequest(BaseModel):
    round_content: str
    existing_summary: str | None = None
    max_chars: int = 150


class SkillScore(BaseModel):
    skillName: str = Field(description="Skill name, for example Java or Redis")
    score: float = Field(ge=1.0, le=5.0)
    comment: str


class EvaluationResponse(BaseModel):
    score: int = Field(ge=0, le=100)
    evaluation: str
    skills: list[SkillScore] = Field(default_factory=list)
