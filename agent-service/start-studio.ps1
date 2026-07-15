$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:UV_CACHE_DIR = Join-Path $projectRoot ".uv-cache-new"
$env:PYTHONUTF8 = "1"

Set-Location $projectRoot
uv run langgraph dev --port 2024
