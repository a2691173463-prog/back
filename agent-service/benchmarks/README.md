# Agent concurrency benchmark

The benchmark uses a deterministic OpenAI-compatible mock server so before/after
runs use the same first-token delay and token cadence without consuming a real
model quota.

## Start the mock model

```powershell
$env:MOCK_FIRST_TOKEN_DELAY_MS = "150"
$env:MOCK_TOKEN_DELAY_MS = "40"
$env:MOCK_TOKEN_COUNT = "12"
uv run uvicorn benchmarks.mock_openai_server:app --host 127.0.0.1 --port 18000
```

## Start the agent against the mock

```powershell
$env:MODEL_BASE_URL = "http://127.0.0.1:18000/v1"
$env:MODEL_API_KEY = "benchmark-key"
$env:MODEL_NAME = "benchmark-model"
$env:LANGSMITH_TRACING = "false"
uv run uvicorn app.main:app --host 127.0.0.1 --port 18001
```

Use one Uvicorn worker for both before and after measurements. Run the test at
least three times after one warm-up run and compare the median reports.

```powershell
uv run python benchmarks/concurrency_benchmark.py `
  --url http://127.0.0.1:18001/llm/chat/stream `
  --requests 100 `
  --concurrency 20 `
  --label before-sync `
  --output benchmarks/results/before-sync.json
```

After the async migration, run the identical command with `--label after-async`
and a different output file. Compare throughput, success rate, TTFT P95, and
total-latency P95. This test measures service concurrency, not real-model
quality or provider rate limits; run a smaller real-model test separately.

For a high-concurrency comparison used by the project report, use:

```powershell
uv run python benchmarks/concurrency_benchmark.py `
  --url http://127.0.0.1:18002/llm/chat/stream `
  --requests 400 `
  --concurrency 200 `
  --label before-sync

uv run python benchmarks/concurrency_benchmark.py `
  --url http://127.0.0.1:18001/llm/chat/stream `
  --requests 400 `
  --concurrency 200 `
  --label after-async
```

Run one warm-up and three measured rounds for each endpoint. The measured
report from 2026-07-14 is in `docs/concurrency-benchmark-report.md`.

`benchmarks.sync_stream_server:app` preserves the pre-migration synchronous
streaming implementation for repeatable side-by-side tests after the main code
has moved forward. Start it on a different port against the same mock model and
compare it with `app.main:app` using identical benchmark arguments.
