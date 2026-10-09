#!/usr/bin/env python3
"""Deterministic local AI stub used by the resume MQ benchmark."""

from __future__ import annotations

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Metrics:
    def __init__(self, fail_first: int) -> None:
        self._lock = threading.Lock()
        self.fail_first = fail_first
        self.active = 0
        self.max_active = 0
        self.total = 0

    def begin(self) -> int:
        with self._lock:
            self.active += 1
            self.total += 1
            self.max_active = max(self.max_active, self.active)
            return self.total

    def end(self) -> None:
        with self._lock:
            self.active -= 1

    def snapshot(self) -> dict[str, int]:
        with self._lock:
            return {
                "active": self.active,
                "max_active": self.max_active,
                "total": self.total,
            }


def build_handler(delay_seconds: float, metrics: Metrics):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/health":
                self._write_json({"status": "ok", "service": "resume-benchmark-stub"})
                return
            if self.path == "/metrics":
                self._write_json(metrics.snapshot())
                return
            self.send_error(404)

        def do_POST(self) -> None:
            if self.path != "/resume/diagnose":
                self.send_error(404)
                return

            content_length = int(self.headers.get("Content-Length", "0"))
            if content_length:
                self.rfile.read(content_length)

            request_number = metrics.begin()
            try:
                time.sleep(delay_seconds)
                if request_number <= metrics.fail_first:
                    self._write_json(
                        {"detail": f"synthetic failure {request_number}"},
                        status=503,
                    )
                    return
                diagnosis = json.dumps(
                    {
                        "extracted_text": "benchmark",
                        "advantages": [],
                        "disadvantages": [],
                        "suggestions": [],
                    },
                    ensure_ascii=False,
                )
                self._write_json({"content": diagnosis})
            finally:
                metrics.end()

        def log_message(self, format: str, *args: object) -> None:
            return

        def _write_json(self, payload: dict[str, object], status: int = 200) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18000)
    parser.add_argument("--delay-ms", type=int, default=750)
    parser.add_argument("--fail-first", type=int, default=0)
    args = parser.parse_args()

    metrics = Metrics(args.fail_first)
    server = ThreadingHTTPServer(
        (args.host, args.port),
        build_handler(args.delay_ms / 1000, metrics),
    )
    print(
        f"Mock AI listening on http://{args.host}:{args.port}, "
        f"delay={args.delay_ms}ms, fail_first={args.fail_first}",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
