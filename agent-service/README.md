# Interview Agent Service

FastAPI + LangChain/LangGraph service for the Java interview backend.

It provides:

- Explicit standard/practice mode branches
- Parallel resume and profile context preparation
- An interviewer/tool Agent loop with bounded recursion
- One bounded structured model call for the complete final skill evaluation
- Node retry and timeout policies for model and context calls
- PostgreSQL Checkpointer state and complete transcript, keyed by `interviewId`
- Incremental working-memory summarization while preserving the complete transcript
- Tool Calling for resume, weak-skill, and user-scoped pgvector retrieval
- User-uploaded PDF ingestion with text splitting and embeddings
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

On Windows, prefer this script over calling `uv run langgraph dev` directly.
It enables Python UTF-8 mode before LangGraph starts its worker process. The
same setting is also present in `.env`, so the direct command remains usable:

```powershell
uv run langgraph dev --port 2024
```

This reads `langgraph.json`, starts the local Agent Server at
`http://127.0.0.1:2024`, and exposes:

- Studio: `https://smith.langchain.com/studio/?baseUrl=http://127.0.0.1:2024`
- API docs: `http://127.0.0.1:2024/docs`

The Studio server is a development/debugging entry point. It does not replace
the FastAPI service used by Java. Studio uses its local development persistence,
while FastAPI uses the configured PostgreSQL Checkpointer for the application workflow.

The two Python entry points serve different callers:

- `start.ps1` starts the FastAPI integration service on port `8000`; Spring Boot
  calls its stable HTTP endpoints for resume diagnosis and interview execution.
- `start-studio.ps1` starts the LangGraph development API on port `2024`; it is
  intended for Studio graph inspection, traces, thread experiments, and local
graph debugging rather than as the Java backend's production-facing API.

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

Checkpoint data is stored in PostgreSQL by default. Set
`CHECKPOINT_BACKEND=sqlite` only when a standalone local fallback is needed.

## Agent control flow

```text
START
  -> standard mode -> resume -> context preparation
  -> practice mode -> resume + profile (parallel) -> context join
  -> manage memory -> project deep dive (at most 3 questions)
  -> explicit transition -> skill-planned question retrieval
  -> Top-3 question selection or one plain-text streamed fallback question
                 -> END for the current user turn

Finalize request
  -> prepare evaluation
  -> pair each asked question with its following candidate answer
  -> one structured evaluate_interview call for all answered dimensions
  -> deterministic score aggregation
  -> END
```

The Java `/api/interview/end` workflow calls
`POST /agent/interview/finalize`. If graph finalization fails,
Java falls back to the legacy single-call `/interview/evaluate` endpoint.

During an active interview, LangGraph Checkpointer is the only conversation
state source. `transcript` keeps every visible user/assistant message for resume
and final evaluation. `messages` is the smaller model working set: when it
crosses a configured threshold, only the most recent visible turns remain; this
trim is deterministic and does not call an LLM. Finishing an interview archives the complete
`transcript` to MySQL before Java asks Python to delete the checkpoint. Redis is
still used by Java for locks, quotas, and other caches, but no longer duplicates
interview messages.

Final evaluation only includes question/answer pairs that contain an actual
candidate response. A trailing unanswered interviewer question is preserved in
the archived transcript but excluded from scoring. Project questions use the
canonical `项目工程能力` dimension; broad knowledge-base labels such as `通用`
are replaced by the planned technical skill. All resulting dimensions are sent
in one structured model request, while the overall 0-100 score is aggregated
deterministically from the returned 1-5 skill scores.

## RAG knowledge base

The application does not download or bundle third-party interview PDFs. Admins
upload global documents and position packages, while signed-in users can retain
their own private PDF library. Java extracts PDF text and forwards it to Python;
Python splits the text, creates embeddings, and stores chunks in PostgreSQL
`rag` tables with `GLOBAL`, `POSITION`, or `PRIVATE` access scopes.

Uploaded Q&A documents are converted into an `interview_questions` index. During
fundamentals, a deterministic skill plan rotates across the resume and position
skills. Retrieval first tries the exact skill and difficulty, then relaxes to the
skill family and broad search. pgvector and `pg_trgm` retrieve and optionally
rerank three candidates, and the highest matching unused question is selected
without another model call. Full resume text is not injected in this stage.
Every candidate and final selection records source, reference answer, scores,
rank, and hit counters. Retrieval failures fall back to one generated principle
question without terminating the interview. Image-only scanned PDFs are rejected.

See `docs/RAG_KNOWLEDGE_ARCHITECTURE.md` for the detailed flow and configuration.
See `docs/INTERVIEW_QUESTION_PIPELINE.md` for stage rules and LangSmith A/B metrics.

Start PostgreSQL/pgvector before the Agent service:

```powershell
docker compose --env-file ../infra/postgres/.env -f ../infra/postgres/compose.yml up -d
```

Runtime limits can be adjusted in `.env`:

```dotenv
AGENT_RECURSION_LIMIT=30
AGENT_MAX_CONCURRENCY=8
AGENT_CONTEXT_TIMEOUT_SECONDS=15
AGENT_MODEL_TIMEOUT_SECONDS=90
AGENT_QUESTION_TIMEOUT_SECONDS=30
AGENT_EVALUATION_TIMEOUT_SECONDS=45
AGENT_EVALUATION_DIMENSIONS=5
AGENT_MEMORY_TRIM_TRIGGER_MESSAGES=40
AGENT_MEMORY_TRIM_TRIGGER_CHARS=24000
AGENT_MEMORY_KEEP_MESSAGES=10
```
