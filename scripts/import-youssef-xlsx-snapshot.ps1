param(
    [string]$BundlePath = "",
    [string]$AdminKey = "change_me_schema_admin"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$snapshotZip = if ($BundlePath) {
    $BundlePath
} else {
    Join-Path $repoRoot "local-snapshots\youssef-xlsx-20260424\lpn-youssef-xlsx-20260424-v1.zip"
}

function Write-Step {
    param([string]$Message)
    Write-Host "[xlsx-snapshot] $Message"
}

Set-Location $repoRoot

Write-Step "Starting Docker services needed for import and RAG."
docker compose up -d postgres qdrant schema-retrieval sql-validator sql-executor llm-orchestrator

Write-Step "Building local derived snapshot from Youssef_Extractions."
python scripts\build-youssef-xlsx-snapshot.py

Write-Step "Validating generated bundle with data-import dry-run."
Push-Location (Join-Path $repoRoot "services-python")
uv run data-import import $snapshotZip --dry-run

Write-Step "Importing generated bundle into PostgreSQL business schema."
$env:POSTGRES_HOST = "localhost"
$env:POSTGRES_PORT = "5433"
$env:POSTGRES_DB = $env:POSTGRES_DB
if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "lpn_ai_bi" }
$env:POSTGRES_USER = "lpn_app_admin"
$env:POSTGRES_PASSWORD = $env:POSTGRES_APP_ADMIN_PASSWORD
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "change_me_app_admin" }
uv run data-import import $snapshotZip
Pop-Location

Write-Step "Refreshing Schema RAG collection in Qdrant."
curl.exe -s -X POST "http://localhost:8084/admin/reindex" -H "X-Admin-Key: $AdminKey"
Write-Host ""

Write-Step "Imported table counts:"
docker exec lpn-ai-bi-postgres-1 psql -U lpn_app_admin -d $env:POSTGRES_DB -c "SELECT table_name FROM information_schema.tables WHERE table_schema='business' ORDER BY table_name;"

Write-Step "Done. The frontend and /v1/qa can now query the Excel-derived snapshot."
