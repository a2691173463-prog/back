# Resume MQ Benchmark

This benchmark measures the end-to-end resume parsing queue with a deterministic
local AI stub. It creates temporary `resume` rows prefixed with
`__mq_benchmark__`, publishes one task per row, waits for terminal statuses, and
deletes the rows afterwards.

The script refuses to start when `resume.parse.queue` already contains messages.
It reuses the path of an existing readable PDF but never changes that file.

Start the stub:

```powershell
python .\benchmarks\mock_ai_server.py --delay-ms 750
```

Run against the legacy `Long` message:

```powershell
python .\benchmarks\resume_mq_benchmark.py `
  --count 12 `
  --message-format java-long `
  --output .\benchmarks\results\before.json
```

Run against the JSON task message:

```powershell
python .\benchmarks\resume_mq_benchmark.py `
  --count 12 `
  --message-format json-task `
  --output .\benchmarks\results\after.json
```

Exercise the transactional outbox:

```powershell
python .\benchmarks\resume_mq_benchmark.py `
  --count 3 `
  --message-format json-task `
  --submission-mode outbox
```

Use `mock_ai_server.py --fail-first 2` with one task to verify the two delayed
retry queues. Use `--file-mode missing` to verify terminal rejection and DLQ
routing.

The measured before/after numbers and reliability test conclusions are recorded
in `../../docs/RABBITMQ_RELIABILITY_AND_BENCHMARK.md`.

## Synchronous versus asynchronous diagnosis (2026-10-09)

The synchronous code is preserved in GitHub branch
`codex/backup-sync-resume-20261009` at commit `0e8158f`. Both runs below used the
same local MySQL, RabbitMQ, PDF, fixed 750 ms Mock AI, and 12 temporary tasks.
Each version was started and run once to warm the JVM before the measured run.

```powershell
python .\benchmarks\resume_mq_benchmark.py --count 12 --message-format json-task --output .\benchmarks\results\sync-warm-20261009.json
python .\benchmarks\resume_mq_benchmark.py --count 12 --message-format json-task --output .\benchmarks\results\async-ack-20261009.json
python .\benchmarks\compare_resume_mq_results.py .\benchmarks\results\sync-warm-20261009.json .\benchmarks\results\async-ack-20261009.json --output .\benchmarks\results\sync-vs-async-20261009.json
```

Run the first benchmark with the backup version running, then stop it and run
the second with the async version. The script refuses to publish if the queue
already contains messages, cleans up its temporary database rows, and now
checks Ready and Unacked message counts before passing. The comparison script
checks task count, success, model requests, one async consumer, prefetch 3, and
three concurrent model requests. It reports elapsed time without enforcing a
speedup, because the original system already had three synchronous consumers.

Measured warm runs: synchronous 3.708 s (3 consumers, prefetch 1 each), async
3.727 s (1 consumer, prefetch 3); both finished 12/12 tasks with AI maximum
concurrency 3. Async permanent file failure and two transient 503 responses
are recorded in `async-missing-20261009.json` and `async-retry-20261009.json`.

After moving the initial blocking JDBC claim off the listener thread, the final
async build was warmed again and measured in `async-final-20261009.json`:
12/12 succeeded in 3.939 s, with one consumer, prefetch 3, model maximum
concurrency 3, and no Ready or Unacked messages. Compared with the synchronous
warm run (3.708 s), this single small-sample run was 6.23% slower; see
`sync-vs-async-final-20261009.json`. The result demonstrates nonblocking
listener behavior, not a throughput gain over the existing three-consumer
synchronous implementation. Real-provider latency needs separate measurement.
