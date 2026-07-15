# Interview Agent Service

FastAPI + LangChain/LangGraph service for the Java interview backend.

It provides:

- An explicit LangGraph workflow with interviewer and tool nodes
- SQLite Checkpointer short-term Agent state, keyed by `interviewId`
- Tool Calling for resume, weak-skill, and question-bank data
- Scoped LangSmith tracing for Agent runs
- Streaming interview responses
- Resume diagnosis and final interview evaluation

## Run

### Run with the Java backend

```powershell
cd agent-service
uv sync
copy .env.example .env
powershell -ExecutionPolicy Bypass -File .\start.ps1
```

This starts the FastAPI integration service at `http://127.0.0.1:8000`.
Spring Boot calls this service, so keep this process running during normal
end-to-end development.

### Run LangGraph Studio

```powershell
cd agent-service
powershell -ExecutionPolicy Bypass -File .\start-studio.ps1
```

This reads `langgraph.json`, starts the local Agent Server at
`http://127.0.0.1:2024`, and exposes:

- Studio: `https://smith.langchain.com/studio/?baseUrl=http://127.0.0.1:2024`
- API docs: `http://127.0.0.1:2024/docs`

The Studio server is a development/debugging entry point. It does not replace
the FastAPI service used by Java. Studio uses its local development persistence,
while FastAPI uses `data/checkpoints.sqlite` for the application workflow.

Set the same random secret in both services:

```dotenv
# agent-service/.env
INTERNAL_AGENT_SECRET=your-random-secret
```

```powershell
# Environment variable used by Spring Boot
$env:AI_INTERNAL_SECRET="your-random-secret"
```

Enable LangSmith in `agent-service/.env` when you need traces:

```dotenv
LANGSMITH_TRACING=true
LANGSMITH_API_KEY=your-langsmith-api-key
LANGSMITH_PROJECT=interview-agent-service
```

Tracing is scoped to the interview LangGraph. A trace can contain conversation
messages and tool results, including resume text, so use test data or configure
your LangSmith retention and access controls appropriately.

Checkpoint data is stored in `data/checkpoints.sqlite` by default. SQLite is
appropriate for local development and a single Agent process; use a production
checkpointer such as PostgreSQL before deploying multiple Agent instances.
