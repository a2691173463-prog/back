from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    model_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    model_api_key: str = ""
    model_name: str = "qwen3.7-flash-2026-07-15"
    vision_model_name: str = "qwen3.8-flash"
    java_backend_url: str = "http://127.0.0.1:8080"
    internal_agent_secret: str = "local-agent-secret-change-me"
    checkpoint_backend: str = "postgres"
    checkpoint_db_path: str = "data/checkpoints.sqlite"
    postgres_url: str = "postgresql://interview_app:change_me@127.0.0.1:5432/interview_agent"
    rag_embedding_model: str = "qwen3.7-text-embedding-flash"
    rag_embedding_dimensions: int = 1024
    rag_embedding_max_concurrency: int = 6
    rag_candidate_k: int = 10
    rag_final_score_threshold: float = 0.45
    rag_rerank_enabled: bool = True
    rag_rerank_model: str = "qwen3.7-text-rerank"
    rag_rerank_url: str = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks"
    rag_rerank_timeout_seconds: float = 20.0
    agent_recursion_limit: int = 30
    agent_max_concurrency: int = 8
    agent_context_timeout_seconds: float = 15.0
    agent_model_timeout_seconds: float = 90.0
    agent_question_timeout_seconds: float = 30.0
    agent_evaluation_timeout_seconds: float = 45.0
    agent_evaluation_dimensions: int = 5
    interview_project_max_questions: int = 3
    interview_question_similarity_threshold: float = 0.78
    interview_question_score_threshold: float = 0.38
    interview_question_top_k: int = 3
    agent_memory_trim_trigger_messages: int = 40
    agent_memory_trim_trigger_chars: int = 24000
    agent_memory_keep_messages: int = 10
    langsmith_tracing: bool = False
    langsmith_api_key: str = ""
    langsmith_project: str = "interview-agent-service"
    langsmith_endpoint: str = "https://api.smith.langchain.com"


settings = Settings()
