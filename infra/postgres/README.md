# PostgreSQL + pgvector

This local database supports two independent concerns:

- LangGraph PostgreSQL Checkpointer for durable interview state.
- pgvector for RAG document chunks and embedding search.

## Start

```powershell
cd D:\简历项目\infra\postgres
docker compose up -d
docker compose ps
```

## Verify

```powershell
docker compose exec postgres psql -U interview_app -d interview_agent -c "SELECT version();"
docker compose exec postgres psql -U interview_app -d interview_agent -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"
```

Local connection URL:

```text
postgresql://interview_app:<password>@127.0.0.1:5432/interview_agent
```

The real password is stored in the ignored `.env` file. Keep only
`.env.example` in version control.

## Stop

```powershell
docker compose down
```

`docker compose down` preserves the named data volume. Do not add `-v` unless
you intentionally want to delete all PostgreSQL data.
