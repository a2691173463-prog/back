#!/usr/bin/env python3
"""End-to-end benchmark for the resume parsing RabbitMQ pipeline."""

from __future__ import annotations

import argparse
import base64
import json
import os
import pathlib
import struct
import subprocess
import time
import urllib.parse
import urllib.request
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env"
QUEUE_NAME = "resume.parse.queue"
DEAD_QUEUE_NAME = "resume.parse.dlq"
EXCHANGE_NAME = "resume.exchange"
ROUTING_KEY = "resume.parse"
BENCHMARK_PREFIX = "__mq_benchmark__"
MYSQL_EXE = pathlib.Path(r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe")


def load_env() -> dict[str, str]:
    values: dict[str, str] = {}
    if not ENV_FILE.exists():
        return values
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def mysql(query: str, env_values: dict[str, str]) -> str:
    process_env = os.environ.copy()
    process_env["MYSQL_PWD"] = env_values.get("DB_PASSWORD", "")
    command = [
        str(MYSQL_EXE),
        "--batch",
        "--skip-column-names",
        "--default-character-set=utf8mb4",
        "-u",
        env_values.get("DB_USERNAME", "root"),
        "interview_db",
        "-e",
        query,
    ]
    result = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=process_env,
    )
    return result.stdout.strip()


def rabbit_request(
    path: str,
    username: str,
    password: str,
    payload: dict[str, object] | None = None,
) -> dict[str, object]:
    url = f"http://127.0.0.1:15672/api/{path}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data)
    credentials = base64.b64encode(f"{username}:{password}".encode()).decode()
    request.add_header("Authorization", f"Basic {credentials}")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request, timeout=10) as response:
        body = response.read()
    return json.loads(body) if body else {}


def java_serialized_long(value: int) -> str:
    prefix = bytes.fromhex(
        "aced00057372000e6a6176612e6c616e672e4c6f6e67"
        "3b8be490cc8f23df0200014a000576616c756578720010"
        "6a6176612e6c616e672e4e756d62657286ac951d0b94e08b"
        "0200007870"
    )
    return base64.b64encode(prefix + struct.pack(">q", value)).decode()


def publish(
    resume_id: int,
    message_format: str,
    username: str,
    password: str,
) -> None:
    if message_format == "java-long":
        body = java_serialized_long(resume_id)
        properties: dict[str, object] = {
            "content_type": "application/x-java-serialized-object",
            "delivery_mode": 2,
        }
        payload_encoding = "base64"
    else:
        body = json.dumps(
            {
                "messageId": str(uuid.uuid4()),
                "resumeId": resume_id,
                "createdAt": int(time.time() * 1000),
            }
        )
        properties = {
            "content_type": "application/json",
            "delivery_mode": 2,
        }
        payload_encoding = "string"

    result = rabbit_request(
        "exchanges/%2F/" + urllib.parse.quote(EXCHANGE_NAME, safe="") + "/publish",
        username,
        password,
        {
            "properties": properties,
            "routing_key": ROUTING_KEY,
            "payload": body,
            "payload_encoding": payload_encoding,
        },
    )
    if not result.get("routed"):
        raise RuntimeError(f"Message for resume {resume_id} was not routed")


def mock_metrics() -> dict[str, int]:
    with urllib.request.urlopen("http://127.0.0.1:18000/metrics", timeout=2) as response:
        return json.loads(response.read())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=12)
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument(
        "--message-format",
        choices=("java-long", "json-task"),
        default="java-long",
    )
    parser.add_argument(
        "--submission-mode",
        choices=("direct", "outbox"),
        default="direct",
    )
    parser.add_argument(
        "--file-mode",
        choices=("valid", "missing"),
        default="valid",
    )
    parser.add_argument("--rabbit-user", default="guest")
    parser.add_argument("--rabbit-password", default="guest")
    parser.add_argument("--output")
    args = parser.parse_args()

    env_values = load_env()
    queue = rabbit_request(
        "queues/%2F/" + urllib.parse.quote(QUEUE_NAME, safe=""),
        args.rabbit_user,
        args.rabbit_password,
    )
    dead_queue_before = rabbit_request(
        "queues/%2F/" + urllib.parse.quote(DEAD_QUEUE_NAME, safe=""),
        args.rabbit_user,
        args.rabbit_password,
    )
    dead_messages_before = int(dead_queue_before.get("messages", 0))
    if queue.get("messages", 0) != 0:
        raise RuntimeError(
            f"{QUEUE_NAME} already contains {queue['messages']} messages; refusing to purge it"
        )

    source = mysql(
        "SELECT user_id, file_url FROM resume "
        "WHERE file_url IS NOT NULL AND file_url <> '' ORDER BY id DESC LIMIT 20;",
        env_values,
    )
    candidates = [line.split("\t", 1) for line in source.splitlines() if "\t" in line]
    source_row = next(
        ((user_id, path) for user_id, path in candidates if pathlib.Path(path).exists()),
        None,
    )
    if source_row is None:
        raise RuntimeError("No existing readable resume PDF was found for the benchmark")

    user_id, file_url = source_row
    if args.file_mode == "missing":
        file_url = str(ROOT / "benchmarks" / "missing-benchmark-resume.pdf")
    run_id = uuid.uuid4().hex[:12]
    file_name = f"{BENCHMARK_PREFIX}{run_id}.pdf"
    escaped_path = file_url.replace("\\", "\\\\").replace("'", "''")
    mysql(
        "INSERT INTO resume(user_id,file_name,file_url,status,create_time,update_time) "
        f"SELECT {int(user_id)},'{file_name}','{escaped_path}',0,NOW(),NOW() "
        f"FROM information_schema.tables LIMIT {args.count};",
        env_values,
    )
    ids_text = mysql(
        f"SELECT id FROM resume WHERE file_name='{file_name}' ORDER BY id;",
        env_values,
    )
    resume_ids = [int(value) for value in ids_text.splitlines()]
    if len(resume_ids) != args.count:
        raise RuntimeError(f"Expected {args.count} rows, created {len(resume_ids)}")

    message_ids = [str(uuid.uuid4()) for _ in resume_ids]
    if args.submission_mode == "outbox":
        values = ",".join(
            f"('{message_id}',{resume_id},0,0,NOW(),NOW(),NOW())"
            for message_id, resume_id in zip(message_ids, resume_ids)
        )
        mysql(
            "INSERT INTO resume_parse_outbox("
            "message_id,resume_id,status,retry_count,next_retry_time,create_time,update_time"
            f") VALUES {values};",
            env_values,
        )

    before_metrics = mock_metrics()
    started = time.perf_counter()
    try:
        if args.submission_mode == "direct":
            for resume_id in resume_ids:
                publish(
                    resume_id,
                    args.message_format,
                    args.rabbit_user,
                    args.rabbit_password,
                )

        deadline = time.monotonic() + args.timeout
        final_counts: dict[int, int] = {}
        while time.monotonic() < deadline:
            status_rows = mysql(
                "SELECT status,COUNT(*) FROM resume "
                f"WHERE file_name='{file_name}' GROUP BY status;",
                env_values,
            )
            final_counts = {
                int(status): int(count)
                for status, count in (
                    row.split("\t", 1) for row in status_rows.splitlines() if "\t" in row
                )
            }
            if final_counts.get(1, 0) + final_counts.get(2, 0) == args.count:
                break
            time.sleep(0.2)

        elapsed = time.perf_counter() - started
        dead_queue_after: dict[str, object] = {}
        expected_dead_letters = final_counts.get(2, 0) if args.file_mode == "missing" else 0
        # Management API queue totals are sampled asynchronously (normally every
        # five seconds), so allow more than one statistics refresh interval.
        dead_letter_deadline = time.monotonic() + 15
        while True:
            dead_queue_after = rabbit_request(
                "queues/%2F/" + urllib.parse.quote(DEAD_QUEUE_NAME, safe=""),
                args.rabbit_user,
                args.rabbit_password,
            )
            dead_letter_delta = (
                int(dead_queue_after.get("messages", 0)) - dead_messages_before
            )
            if dead_letter_delta >= expected_dead_letters:
                break
            if time.monotonic() >= dead_letter_deadline:
                break
            time.sleep(0.1)

        after_metrics = mock_metrics()
        queue_after = rabbit_request(
            "queues/%2F/" + urllib.parse.quote(QUEUE_NAME, safe=""),
            args.rabbit_user,
            args.rabbit_password,
        )
        result = {
            "runId": run_id,
            "messageFormat": args.message_format,
            "submissionMode": args.submission_mode,
            "fileMode": args.file_mode,
            "tasks": args.count,
            "elapsedSeconds": round(elapsed, 3),
            "throughputTasksPerSecond": round(args.count / elapsed, 3),
            "success": final_counts.get(1, 0),
            "failed": final_counts.get(2, 0),
            "unfinished": args.count - final_counts.get(1, 0) - final_counts.get(2, 0),
            "rabbitConsumers": max(
                int(queue_after.get("consumers", 0)),
                len(queue_after.get("consumer_details", [])),
            ),
            "rabbitPrefetch": [
                detail.get("prefetch_count")
                for detail in queue_after.get("consumer_details", [])
            ],
            "mockAiRequests": after_metrics["total"] - before_metrics["total"],
            "mockAiMaxConcurrency": after_metrics["max_active"],
            "deadLetterMessagesAdded": max(0, dead_letter_delta),
        }
        if args.submission_mode == "outbox":
            outbox_counts = mysql(
                "SELECT status,COUNT(*) FROM resume_parse_outbox "
                f"WHERE resume_id IN ({','.join(map(str, resume_ids))}) GROUP BY status;",
                env_values,
            )
            result["outboxStatuses"] = {
                int(status): int(count)
                for status, count in (
                    row.split("\t", 1)
                    for row in outbox_counts.splitlines()
                    if "\t" in row
                )
            }
        output = json.dumps(result, ensure_ascii=False, indent=2)
        print(output)
        if args.output:
            output_path = pathlib.Path(args.output)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(output + "\n", encoding="utf-8")
    finally:
        mysql(
            "DELETE FROM resume_parse_outbox "
            f"WHERE resume_id IN ({','.join(map(str, resume_ids))});",
            env_values,
        )
        mysql(f"DELETE FROM resume WHERE file_name='{file_name}';", env_values)


if __name__ == "__main__":
    main()
