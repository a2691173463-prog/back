$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:UV_CACHE_DIR = Join-Path $projectRoot ".uv-cache-new"

Set-Location $projectRoot
uv run uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
