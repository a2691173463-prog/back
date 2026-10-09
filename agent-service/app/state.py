import operator
from dataclasses import dataclass
from typing import Annotated, Literal, NotRequired

from langgraph.graph import MessagesState


@dataclass(frozen=True)
class InterviewContext:
    user_id: int
    resume_id: int
    interview_mode: Literal["standard", "practice"] = "standard"
    position_code: str = "GENERAL"
    thread_id: str = ""


class InterviewState(MessagesState):
    system_prompt: NotRequired[str]
    interview_mode: NotRequired[Literal["standard", "practice"]]
    interview_stage: NotRequired[str]
    question_count: NotRequired[int]
    project_question_count: NotRequired[int]
    fundamental_question_count: NotRequired[int]
    project_coverage: NotRequired[dict[str, bool]]
    stage_transition_pending: NotRequired[bool]
    current_skill: NotRequired[str]
    current_topic: NotRequired[str]
    covered_skills: NotRequired[list[str]]
    covered_topics: NotRequired[list[str]]
    skipped_topics: NotRequired[list[str]]
    fundamental_skill_plan: NotRequired[list[str]]
    fundamental_skill_cursor: NotRequired[int]
    skill_question_counts: NotRequired[dict[str, int]]
    technical_profile: NotRequired[str]
    asked_questions: Annotated[list[dict[str, object]], operator.add]
    resume_loaded: NotRequired[bool]
    profile_loaded: NotRequired[bool]
    context_prepared: NotRequired[bool]
    resume_context: NotRequired[str]
    profile_context: NotRequired[str]
    prepared_context: NotRequired[str]
    position_code: NotRequired[str]
    rag_context: NotRequired[str]
    rag_query: NotRequired[str]
    rag_query_plan: NotRequired[dict[str, object]]
    question_candidates: NotRequired[list[dict[str, object]]]
    selected_question: NotRequired[dict[str, object]]
    rag_retrieved: NotRequired[bool]
    rag_result_count: NotRequired[int]
    context_errors: Annotated[list[str], operator.add]
    summary: NotRequired[str]
    summary_model_calls: Annotated[int, operator.add]
    transcript: Annotated[list[dict[str, str]], operator.add]
    total_model_calls: NotRequired[int]
    finalize_requested: NotRequired[bool]
    evaluation_dimensions: NotRequired[list[str]]
    evaluation_transcript: NotRequired[str]
    evaluation_evidence: NotRequired[list[dict[str, str]]]
    evaluation_summary: NotRequired[str]
    dimension_scores: Annotated[list[dict[str, object]], operator.add]
    evaluation_model_calls: Annotated[int, operator.add]
    final_report: NotRequired[dict[str, object]]
