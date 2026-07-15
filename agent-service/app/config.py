from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    model_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    model_api_key: str = ""
    model_name: str = "qwen-plus"
    vision_model_name: str = "qwen-vl-plus"
    java_backend_url: str = "http://127.0.0.1:8080"
    internal_agent_secret: str = "local-agent-secret-change-me"
    checkpoint_db_path: str = "data/checkpoints.sqlite"
    langsmith_tracing: bool = False
    langsmith_api_key: str = ""
    langsmith_project: str = "interview-agent-service"
    langsmith_endpoint: str = "https://api.smith.langchain.com"


settings = Settings()
