# Structured Output Migration

## Scope

Structured output is applied only where application code consumes model fields:

- resume diagnosis
- per-dimension interview evaluation
- legacy aggregate interview evaluation

Streaming interviewer replies, resume-section rewriting, and conversation
summaries remain plain text.

## Before and after

| Concern | Before | After |
| --- | --- | --- |
| Model contract | Prompt asks for JSON | Pydantic schema is passed through `with_structured_output` |
| Parsing | Strip Markdown and call `json.loads`/`model_validate_json` | Receive and validate a typed Pydantic object |
| Score bounds | Manual clamping or endpoint-specific fallback | Schema enforces `1..5` and `0..100` |
| Provider compatibility | Plain JSON only | Native structured output with plain-JSON fallback |
| Java API contract | JSON string/object | Unchanged |
| Failure behavior | Each caller implements its own fallback | Shared validation error plus business fallback |

## Deterministic comparison tests

`tests/test_structured_output.py` covers:

1. A raw response that the legacy JSON parser rejects while the provider's
   schema result succeeds.
2. Compatibility with providers that only return fenced JSON.
3. Rejection of an out-of-range score.
4. Fallback when a provider reports that structured output is unsupported.
5. Preservation of the resume-diagnosis JSON string contract.
6. Async structured output used by parallel evaluation workers.

Run:

```powershell
$env:UV_CACHE_DIR='D:\简历项目\.uv-cache'
uv run python -m unittest discover -s tests -v
```

Current result: 16 tests passed.

This is a deterministic contract test, not a live model quality benchmark.
A live A/B evaluation should use a fixed anonymized dataset and record valid
schema rate, fallback rate, latency, token use, and semantic evaluation quality
in LangSmith.
