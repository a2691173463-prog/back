from contextlib import contextmanager
from collections.abc import Iterator

from langsmith import Client, tracing_context

from app.config import settings


_client: Client | None = None


def tracing_enabled() -> bool:
    return settings.langsmith_tracing and bool(settings.langsmith_api_key)


def _client_instance() -> Client:
    global _client
    if _client is None:
        _client = Client(
            api_url=settings.langsmith_endpoint,
            api_key=settings.langsmith_api_key,
        )
    return _client


@contextmanager
def trace_interview(thread_id: str) -> Iterator[None]:
    if not tracing_enabled():
        yield
        return

    with tracing_context(
        enabled=True,
        client=_client_instance(),
        project_name=settings.langsmith_project,
        tags=["interview-agent", "langgraph"],
        metadata={"thread_id": thread_id, "workflow": "interview"},
    ):
        yield
