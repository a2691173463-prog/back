"""Compare interview-question quality exports and trace the report to LangSmith."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from statistics import mean
from typing import Any

from langsmith import traceable


def _tokens(value: str) -> set[str]:
    return {
        token.lower()
        for token in re.findall(r"[A-Za-z][A-Za-z0-9+#.-]*|[\u4e00-\u9fff]{2,}", value or "")
    }


def _single_question_compliant(question: str) -> bool:
    markers = question.count("？") + question.count("?")
    list_like = bool(re.search(r"(?:^|\s)(?:[1-9][.、)]|[-*•])\s*", question))
    return bool(question.strip()) and markers == 1 and not list_like and len(question) <= 300


def _relevance(row: dict[str, Any]) -> float:
    expected = _tokens(" ".join([str(row.get("expected_skill", "")), str(row.get("expected_topic", ""))]))
    actual = _tokens(str(row.get("question", "")))
    if not expected:
        return 0.0
    return len(expected & actual) / len(expected)


def evaluate_rows(rows: list[dict[str, Any]]) -> dict[str, float | int]:
    if not rows:
        return {
            "samples": 0,
            "single_question_rate": 0.0,
            "topic_relevance": 0.0,
            "source_traceability_rate": 0.0,
            "duplicate_rate": 0.0,
            "stage_violation_rate": 0.0,
        }
    normalized_questions = [re.sub(r"\W+", "", str(row.get("question", "")).lower()) for row in rows]
    duplicates = len(normalized_questions) - len(set(normalized_questions))
    traceable_rows = [
        row
        for row in rows
        if row.get("source_type") != "RAG" or row.get("source_question_id")
    ]
    violations = [
        row
        for row in rows
        if row.get("stage") == "scenario"
        or (row.get("stage") == "project" and int(row.get("stage_question_number", 0)) > 3)
    ]
    return {
        "samples": len(rows),
        "single_question_rate": round(mean(_single_question_compliant(str(row.get("question", ""))) for row in rows), 4),
        "topic_relevance": round(mean(_relevance(row) for row in rows), 4),
        "source_traceability_rate": round(len(traceable_rows) / len(rows), 4),
        "duplicate_rate": round(duplicates / len(rows), 4),
        "stage_violation_rate": round(len(violations) / len(rows), 4),
    }


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


@traceable(name="interview-rag-before-after-evaluation", run_type="chain")
def compare_exports(before: list[dict[str, Any]], after: list[dict[str, Any]]) -> dict[str, Any]:
    before_metrics = evaluate_rows(before)
    after_metrics = evaluate_rows(after)
    improvements = {
        key: round(float(after_metrics[key]) - float(before_metrics[key]), 4)
        for key in (
            "single_question_rate",
            "topic_relevance",
            "source_traceability_rate",
        )
    }
    improvements.update(
        {
            f"{key}_reduction": round(float(before_metrics[key]) - float(after_metrics[key]), 4)
            for key in ("duplicate_rate", "stage_violation_rate")
        }
    )
    return {"before": before_metrics, "after": after_metrics, "improvement": improvements}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = compare_exports(_load_jsonl(args.before), _load_jsonl(args.after))
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")


if __name__ == "__main__":
    main()
