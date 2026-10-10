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

## Executor saturation repair and concurrency matrix (2026-10-09 21:31 UTC+08:00)

Before the repair, raising only the listener prefetch from 3 to 4 caused a
`RejectedExecutionException` while scheduling the PDF continuation: 11 of 12
temporary resumes finished, one message went to the DLQ, and the remaining
resume did not reach a terminal status. The executor then had 3 active threads
and 3 queued tasks. The repair makes its size configurable (defaults: 3 threads,
12 queue slots), uses caller-runs backpressure while active, and explicitly
requests broker requeue if the executor is shutting down. Production listener
settings remain one consumer and prefetch 3.

With the fixed build, a local 750 ms Mock AI, identical PDF and 12 temporary
resumes per run, the following are three measured runs after one warm-up per
configuration. All runs finished 12/12 with zero new dead letters and no Ready
or Unacked messages:

| Consumers × prefetch | Measured seconds | Median seconds | Mock AI max concurrency |
| --- | --- | ---: | ---: |
| 1 × 3 | 3.753, 3.741, 3.747 | 3.747 | 3 |
| 1 × 4 | 2.979, 2.936, 2.964 | 2.964 | 4 |
| 1 × 6 | 2.140, 2.104, 2.086 | 2.104 | 6 |
| 2 × 3 | 2.194, 2.239, 2.189 | 2.194 | 6 |

At 60 resumes, a warmed 1 × 6 run took 9.444 seconds and a warmed 2 × 3 run
took 9.336 seconds, both 60/60 successful with zero new dead letters. With
1 × 6 but executor queue capacity forced down to one slot, 30/30 succeeded
without new dead letters (6.984 seconds); this exercises the overload guard.

Results and logs are saved under `target/concurrency-fixed/` locally (ignored by
Git). These measurements include sequential RabbitMQ publication and 200 ms
database polling, use a deterministic mock rather than a paid model, and do
not establish a safe real-provider concurrency limit. The failed pre-repair
test added one benchmark message to an existing DLQ; the other DLQ entries
were not modified or purged.
