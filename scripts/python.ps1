param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("sync", "test", "lint")]
    [string] $Task
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$PythonWorkspace = Join-Path $Root "services-python"

switch ($Task) {
    "sync" { python -m uv sync --directory $PythonWorkspace }
    "test" { python -m uv run --directory $PythonWorkspace pytest }
    "lint" { python -m uv run --directory $PythonWorkspace ruff check . }
}
