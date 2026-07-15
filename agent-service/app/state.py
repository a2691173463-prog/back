import operator
from dataclasses import dataclass
from typing import Annotated, NotRequired

from langgraph.graph import MessagesState


@dataclass(frozen=True)
class InterviewContext:
    user_id: int
    resume_id: int


class InterviewState(MessagesState):
    system_prompt: NotRequired[str]
    interview_stage: NotRequired[str]
    question_count: NotRequired[int]
    current_skill: NotRequired[str]
    covered_skills: NotRequired[list[str]]
    resume_loaded: NotRequired[bool]
    profile_loaded: NotRequired[bool]
    summary: NotRequired[str]
    transcript: Annotated[list[dict[str, str]], operator.add]
    total_model_calls: NotRequired[int]
