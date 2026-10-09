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
