#!/usr/bin/env python3
"""Validate and compare synchronous and asynchronous resume MQ benchmark runs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def compare(baseline: dict, candidate: dict) -> dict:
    tasks = baseline["tasks"]
    if tasks != candidate["tasks"]:
        raise AssertionError("Both benchmark runs must use the same number of tasks")
    for label, run in (("synchronous", baseline), ("asynchronous", candidate)):
        if run["success"] != tasks or run["failed"] or run["unfinished"]:
            raise AssertionError(f"{label} run did not complete all tasks successfully")
        if run["mockAiRequests"] != tasks:
            raise AssertionError(f"{label} run made an unexpected number of AI requests")
    if candidate["rabbitConsumers"] != 1 or candidate["rabbitPrefetch"] != [3]:
        raise AssertionError("Async run must use one consumer with prefetch=3")
    if candidate["mockAiMaxConcurrency"] < 3:
        raise AssertionError("Async run did not reach three concurrent model requests")
    if candidate.get("rabbitReady", 0) or candidate.get("rabbitUnacked", 0):
        raise AssertionError("Async run left messages ready or unacknowledged")

    baseline_elapsed = baseline["elapsedSeconds"]
    candidate_elapsed = candidate["elapsedSeconds"]
    return {
        "tasks": tasks,
        "syncElapsedSeconds": baseline_elapsed,
        "asyncElapsedSeconds": candidate_elapsed,
        "elapsedChangePercent": round(
            (candidate_elapsed / baseline_elapsed - 1) * 100, 2
        ),
        "syncThroughputTasksPerSecond": baseline["throughputTasksPerSecond"],
        "asyncThroughputTasksPerSecond": candidate["throughputTasksPerSecond"],
        "syncRabbitConsumers": baseline["rabbitConsumers"],
        "asyncRabbitConsumers": candidate["rabbitConsumers"],
        "syncPrefetch": baseline["rabbitPrefetch"],
        "asyncPrefetch": candidate["rabbitPrefetch"],
        "syncModelMaxConcurrency": baseline["mockAiMaxConcurrency"],
        "asyncModelMaxConcurrency": candidate["mockAiMaxConcurrency"],
        "asyncRabbitReady": candidate.get("rabbitReady", 0),
        "asyncRabbitUnacked": candidate.get("rabbitUnacked", 0),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = compare(
        json.loads(args.baseline.read_text(encoding="utf-8")),
        json.loads(args.candidate.read_text(encoding="utf-8")),
    )
    report = json.dumps(result, ensure_ascii=False, indent=2)
    print(report)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
