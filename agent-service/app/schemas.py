from typing import Any, Literal

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
    interview_mode: Literal["standard", "practice"] = "standard"
    position_code: str = Field(default="GENERAL", max_length=40)
    system_prompt: str
    greeting: str


class AgentChatRequest(BaseModel):
    thread_id: str
    user_id: int
    resume_id: int
    interview_mode: Literal["standard", "practice"] = "standard"
    position_code: str = Field(default="GENERAL", max_length=40)
    message: str


class AgentFinalizeRequest(BaseModel):
    thread_id: str
    user_id: int
    resume_id: int
    interview_mode: Literal["standard", "practice"] = "standard"
    position_code: str = Field(default="GENERAL", max_length=40)


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


class KnowledgeDocumentRequest(BaseModel):
    owner_user_id: int = Field(gt=0)
    title: str = Field(min_length=1, max_length=200)
    file_name: str = Field(min_length=1, max_length=255)
    category: str = Field(default="通用", min_length=1, max_length=80)
    scope: Literal["PRIVATE", "GLOBAL", "POSITION"] = "PRIVATE"
    position_code: str = Field(default="GENERAL", min_length=1, max_length=40)
    source_type: str | None = Field(default=None, max_length=24)
    content: str = Field(min_length=20, max_length=10_000_000)


class InterviewSummaryRequest(BaseModel):
    round_content: str
    existing_summary: str | None = None
    max_chars: int = 150


class ResumeDiagnosisResponse(BaseModel):
    advantages: list[str] = Field(
        default_factory=list,
        description="Evidence-based strengths visible in the supplied resume layout or text",
    )
    disadvantages: list[str] = Field(
        default_factory=list,
        description="Specific weaknesses tied to a visible resume section or missing evidence",
    )
    suggestions: list[str] = Field(
        default_factory=list,
        description="Actionable edits corresponding to the weaknesses without inventing facts",
    )
    extracted_text: str = Field(
        default="",
        description="OCR text only when extracted PDF text is unavailable; otherwise an empty string",
    )


class DimensionEvaluation(BaseModel):
    score: float = Field(
        ge=1.0,
        le=5.0,
        description="Score for the requested interview dimension",
    )
    comment: str = Field(
        min_length=1,
        description="Comment formatted as evidence, judgment, and one concrete improvement action",
    )


class InterviewRetrievalQuery(BaseModel):
    skill: str = Field(
        min_length=1,
        max_length=80,
        description="One technical skill to retrieve, such as Redis or RabbitMQ",
    )
    topic: str = Field(
        min_length=1,
        max_length=120,
        description="One concrete technical topic without conversational wording",
    )
    difficulty: Literal["basic", "medium", "advanced"] = Field(
        description="Difficulty appropriate for the current interview stage and evidence",
    )
    question_type: Literal["principle"] = Field(
        default="principle",
        description="Fundamental knowledge questions use principle retrieval",
    )
    retrieval_goal: str = Field(
        min_length=1,
        max_length=180,
        description="Facts needed to generate one accurate interview question",
    )
    keywords: list[str] = Field(
        min_length=2,
        max_length=8,
        description="Concise technical terms useful for hybrid retrieval",
    )


class SkillScore(BaseModel):
    skillName: str = Field(description="Skill name, for example Java or Redis")
    score: float = Field(ge=1.0, le=5.0)
    comment: str


class InterviewEvaluationBatch(BaseModel):
    evaluation: str = Field(
        min_length=1,
        max_length=800,
        description="Concise overall conclusion based only on answered interview questions",
    )
    skills: list[SkillScore] = Field(
        min_length=1,
        max_length=8,
        description="One evidence-based score for each requested interview dimension",
    )


class EvaluationResponse(BaseModel):
    score: int = Field(ge=0, le=100)
    evaluation: str = Field(description="Overall evidence-based interview evaluation")
    skills: list[SkillScore] = Field(
        default_factory=list,
        description="Scores for technical and general ability dimensions",
    )


class ExtractedQAItem(BaseModel):
    question: str = Field(
        min_length=5,
        max_length=300,
        description="面试问题的完整标题，必须以问号（？或?）结尾",
    )
    reference_answer: str = Field(
        min_length=1,
        description="与该问题对应的完整参考答案，保留原文解释、代码、注意事项与示例",
    )
    has_code: bool = Field(
        default=False,
        description="参考答案中是否包含代码示例",
    )
    skill: str = Field(
        min_length=1,
        max_length=80,
        description="核心技术大类，例如：Java 集合, MySQL, Redis, JVM, Spring Boot, 计算机网络等",
    )
    topic: str = Field(
        min_length=1,
        max_length=120,
        description="具体的考点主题，例如：ConcurrentModificationException, MVCC, 缓存雪崩",
    )
    difficulty: Literal["basic", "medium", "advanced"] = Field(
        default="medium",
        description="难度等级：basic / medium / advanced",
    )
    keywords: list[str] = Field(
        default_factory=list,
        max_length=8,
        description="3到6个核心技术名词关键字",
    )


class DocumentQAExtractionResult(BaseModel):
    items: list[ExtractedQAItem] = Field(
        default_factory=list,
        description="从文本中提取的所有完整问答题目列表",
    )
