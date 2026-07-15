import argparse
import asyncio
import json
import math
import statistics
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import httpx


@dataclass
class RequestResult:
    index: int
    success: bool
    status_code: int | None
    ttft_ms: float | None
    total_ms: float
    event_count: int
    error: str | None = None


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, math.ceil(percent * len(ordered)) - 1)
    return round(ordered[index], 2)


def parse_sse_data(raw: str) -> tuple[str, bool]:
    data = raw.strip()
    if data == "[DONE]":
        return "done", False
    if data.startswith("[AI_ERROR]"):
        return "error", False
    try:
        event = json.loads(data)
    except json.JSONDecodeError:
        return "token", bool(data)
    event_type = str(event.get("type", "token"))
    return event_type, event_type == "token" and bool(event.get("content"))


async def run_request(
    client: httpx.AsyncClient,
    url: str,
    index: int,
    semaphore: asyncio.Semaphore,
) -> RequestResult:
    payload = {
        "messages": [
            {
                "role": "user",
                "content": f"benchmark request {index}; answer briefly",
            }
        ]
    }
    async with semaphore:
        started = time.perf_counter()
        first_token_at: float | None = None
        event_count = 0
        status_code: int | None = None
        try:
            async with client.stream("POST", url, json=payload) as response:
                status_code = response.status_code
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    event_type, is_token = parse_sse_data(line[5:])
                    event_count += 1
                    if is_token and first_token_at is None:
                        first_token_at = time.perf_counter()
                    if event_type == "error":
                        raise RuntimeError(line[5:].strip())
            finished = time.perf_counter()
            return RequestResult(
                index=index,
                success=True,
                status_code=status_code,
                ttft_ms=(first_token_at - started) * 1000 if first_token_at else None,
                total_ms=(finished - started) * 1000,
                event_count=event_count,
            )
        except Exception as exc:
            finished = time.perf_counter()
            return RequestResult(
                index=index,
                success=False,
                status_code=status_code,
                ttft_ms=(first_token_at - started) * 1000 if first_token_at else None,
                total_ms=(finished - started) * 1000,
                event_count=event_count,
                error=str(exc),
            )


def summarize(
    label: str,
    url: str,
    concurrency: int,
    results: list[RequestResult],
    wall_seconds: float,
) -> dict[str, Any]:
    successful = [result for result in results if result.success]
    ttft = [result.ttft_ms for result in successful if result.ttft_ms is not None]
    totals = [result.total_ms for result in successful]
    return {
        "label": label,
        "url": url,
        "requests": len(results),
        "concurrency": concurrency,
        "successful": len(successful),
        "failed": len(results) - len(successful),
        "success_rate_percent": round(len(successful) * 100 / max(1, len(results)), 2),
        "wall_seconds": round(wall_seconds, 3),
        "throughput_requests_per_second": round(len(successful) / max(wall_seconds, 0.001), 3),
        "ttft_ms": {
            "average": round(statistics.mean(ttft), 2) if ttft else None,
            "p50": percentile(ttft, 0.50),
            "p95": percentile(ttft, 0.95),
        },
        "total_latency_ms": {
            "average": round(statistics.mean(totals), 2) if totals else None,
            "p50": percentile(totals, 0.50),
            "p95": percentile(totals, 0.95),
        },
        "errors": [result.error for result in results if result.error][:10],
        "raw_results": [asdict(result) for result in results],
    }


async def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark the agent SSE endpoint")
    parser.add_argument("--url", default="http://127.0.0.1:8000/llm/chat/stream")
    parser.add_argument("--requests", type=int, default=20)
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=60)
    parser.add_argument("--label", default="benchmark")
    parser.add_argument("--output")
    args = parser.parse_args()

    limits = httpx.Limits(
        max_connections=max(args.concurrency, 10),
        max_keepalive_connections=max(args.concurrency, 10),
    )
    semaphore = asyncio.Semaphore(args.concurrency)
    async with httpx.AsyncClient(timeout=args.timeout, limits=limits) as client:
        started = time.perf_counter()
        results = await asyncio.gather(
            *[
                run_request(client, args.url, index, semaphore)
                for index in range(args.requests)
            ]
        )
        wall_seconds = time.perf_counter() - started

    report = summarize(args.label, args.url, args.concurrency, results, wall_seconds)
    print(json.dumps({key: value for key, value in report.items() if key != "raw_results"}, ensure_ascii=False, indent=2))
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    asyncio.run(main())

