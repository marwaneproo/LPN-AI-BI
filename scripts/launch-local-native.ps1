param(
    [int]$FrontendPort = 5173,
    [int]$DbPort = 5432,
    [switch]$NoBrowser
)

# Local-native launcher: runs the whole stack on the host (no Docker).
# PostgreSQL must already be installed and listening on 5433. Qdrant runs
# embedded on-disk inside schema-retrieval. See docs/LOCAL_DEV_SETUP.md.

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogsRoot = Join-Path $RepoRoot "logs"
$RuntimeDir = Join-Path $LogsRoot "_runtime"
$LauncherLogDir = Join-Path $LogsRoot "launcher"
$QdrantPath = Join-Path $RepoRoot ".local\qdrant"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$LauncherLog = Join-Path $LauncherLogDir "native-$Timestamp.log"

# Database connection (native Postgres on 5433)
$DbName = "lpn_ai_bi"
$AppAdminPwd = "change_me_app_admin"
$AiReadonlyPwd = "change_me_ai_readonly"
$AppDbUrl = "jdbc:postgresql://localhost:$DbPort/$DbName"
$ReadonlyDbUrl = "jdbc:postgresql://localhost:$DbPort/$DbName" + "?options=-c%20statement_timeout%3D30000%20-c%20search_path%3Dbusiness"
$BiReadonlyDbUrl = "jdbc:postgresql://localhost:$DbPort/$DbName" + "?options=-c%20statement_timeout%3D30000%20-c%20search_path%3Dmart"

function Ensure-Directory { param([string]$Path) if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Path $Path | Out-Null } }

function Write-Step {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Path $LauncherLog -Value $line
}

function Get-EnvValue {
    param([string]$Name, [string]$Fallback)
    $envFile = Join-Path $RepoRoot ".env"
    if (-not (Test-Path $envFile)) { return $Fallback }
    $match = Get-Content -Path $envFile | Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } | Select-Object -Last 1
    if (-not $match) { return $Fallback }
    return (($match -split "=", 2)[1]).Trim().Trim('"').Trim("'")
}

function Test-PostgresReady {
    param([int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect("127.0.0.1", $Port)
        $client.Close()
        return $true
    } catch { return $false }
}

function Start-NativeOllama {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) { Write-Step "Native Ollama already running."; return }
    } catch { }

    $candidates = @(
        (Get-EnvValue -Name "OLLAMA_EXE" -Fallback ""),
        (Get-Command "ollama.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
        (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama app.exe"),
        (Join-Path $env:ProgramFiles "Ollama\ollama.exe"),
        (Join-Path $env:ProgramFiles "Ollama\ollama app.exe")
    ) |
        Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_) } |
        ForEach-Object { (Resolve-Path -LiteralPath $_).Path } |
        Select-Object -Unique
    if (-not $candidates) { throw "Ollama not found. Set OLLAMA_EXE in .env." }

    $logDir = Join-Path $LogsRoot "native-ollama"; Ensure-Directory $logDir
    $ollamaExe = [string]($candidates | Select-Object -First 1)
    Write-Step "Starting native Ollama from $ollamaExe."
    Start-Process -FilePath $ollamaExe -ArgumentList "serve" -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir "native-ollama.log") `
        -RedirectStandardError (Join-Path $logDir "native-ollama.error.log") | Out-Null

    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            if ((Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200) {
                Write-Step "Native Ollama ready."; return
            }
        } catch { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $deadline)
    throw "Ollama did not become ready on 11434."
}

function Start-BackgroundProcess {
    param([string]$Name, [string]$WorkDir, [string]$Command, [hashtable]$EnvVars)

    $logDir = Join-Path $LogsRoot $Name; Ensure-Directory $logDir
    $stdout = Join-Path $logDir "$Name.log"
    $stderr = Join-Path $logDir "$Name.error.log"
    Add-Content -Path $stdout -Value "===== $Name started $(Get-Date -Format s) ====="

    # Build a cmd line that sets per-process env vars, then runs the command.
    $envPrefix = ""
    foreach ($key in $EnvVars.Keys) { $envPrefix += "set `"$key=$($EnvVars[$key])`" && " }
    $full = "cd /d `"$WorkDir`" && $envPrefix$Command >> `"$stdout`" 2>> `"$stderr`""

    $proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $full -WindowStyle Hidden -PassThru
    Set-Content -Path (Join-Path $RuntimeDir "$Name.native.pid") -Value $proc.Id
    Write-Step "Started $Name (PID $($proc.Id)). Logs: $stdout"
}

function Wait-HttpHealth {
    param([string]$Name, [string]$Url, [int]$TimeoutSeconds = 180)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { Write-Step "$Name healthy at $Url"; return }
        } catch { Start-Sleep -Seconds 3 }
    } while ((Get-Date) -lt $deadline)
    throw "$Name not healthy at $Url within $TimeoutSeconds s. Check logs\$Name\."
}

Ensure-Directory $LogsRoot
Ensure-Directory $RuntimeDir
Ensure-Directory $LauncherLogDir
Ensure-Directory $QdrantPath

try {
    Write-Step "Local-native launcher starting from $RepoRoot"

    # Read passwords from .env if present
    $AppAdminPwd = Get-EnvValue -Name "POSTGRES_APP_ADMIN_PASSWORD" -Fallback $AppAdminPwd
    $AiReadonlyPwd = Get-EnvValue -Name "POSTGRES_AI_READONLY_PASSWORD" -Fallback $AiReadonlyPwd

    if (-not (Test-PostgresReady -Port $DbPort)) {
        throw "PostgreSQL is not listening on localhost:$DbPort. Start your native Postgres first (see docs/LOCAL_DEV_SETUP.md)."
    }
    Write-Step "PostgreSQL reachable on $DbPort."

    Start-NativeOllama

    $servicesPython = Join-Path $RepoRoot "services-python"
    $servicesJava = Join-Path $RepoRoot "services-java"
    $docsCsv = Join-Path $RepoRoot "docs\schema_metadata.csv"

    # Resolve how to run uvicorn: prefer the workspace .venv, fall back to uv.
    $venvUvicorn = Join-Path $servicesPython ".venv\Scripts\uvicorn.exe"
    if (Test-Path $venvUvicorn) {
        $uvicorn = "`"$venvUvicorn`""
        Write-Step "Python services use venv uvicorn: $venvUvicorn"
    } elseif (Get-Command uv -ErrorAction SilentlyContinue) {
        $uvicorn = "uv run uvicorn"
        Write-Step "Python services use 'uv run uvicorn'."
    } else {
        throw "Neither services-python\.venv nor 'uv' found. Run 'uv sync' in services-python first."
    }

    # 1) schema-retrieval (embedded Qdrant)
    Start-BackgroundProcess -Name "schema-retrieval" -WorkDir $servicesPython `
        -Command "$uvicorn schema_retrieval.main:app --host 127.0.0.1 --port 8084" `
        -EnvVars @{ QDRANT_PATH = $QdrantPath; EMBEDDING_BACKEND = "hash"; SCHEMA_CSV_PATH = $docsCsv; COLLECTION_NAME = "lpn_schema" }

    # 2) sql-validator
    Start-BackgroundProcess -Name "sql-validator" -WorkDir $servicesPython `
        -Command "$uvicorn sql_validator.main:app --host 127.0.0.1 --port 8086" -EnvVars @{}

    # 3) predictive
    Start-BackgroundProcess -Name "predictive" -WorkDir $servicesPython `
        -Command "$uvicorn predictive.main:app --host 127.0.0.1 --port 8085" `
        -EnvVars @{ DATABASE_URL = "postgresql://lpn_ai_readonly:$AiReadonlyPwd@localhost:$DbPort/$DbName" }

    # 4) sql-executor (Java)
    Start-BackgroundProcess -Name "sql-executor" -WorkDir $servicesJava `
        -Command "gradlew.bat :sql-executor:bootRun" `
        -EnvVars @{
            SERVER_PORT = "8082"; APP_ADMIN_DATABASE_URL = $AppDbUrl; APP_ADMIN_DATABASE_USER = "lpn_app_admin"; APP_ADMIN_DATABASE_PASSWORD = $AppAdminPwd;
            READONLY_DATABASE_URL = $ReadonlyDbUrl; READONLY_DATABASE_USER = "lpn_ai_readonly"; READONLY_DATABASE_PASSWORD = $AiReadonlyPwd;
            SQL_VALIDATOR_BASE_URL = "http://localhost:8086"
        }

    # 5) llm-orchestrator (Java)
    Start-BackgroundProcess -Name "llm-orchestrator" -WorkDir $servicesJava `
        -Command "gradlew.bat :llm-orchestrator:bootRun" `
        -EnvVars @{
            SERVER_PORT = "8081"; APP_DATABASE_URL = $AppDbUrl; APP_DATABASE_USER = "lpn_app_admin"; APP_DATABASE_PASSWORD = $AppAdminPwd;
            SPRING_DATASOURCE_URL = $AppDbUrl; SPRING_DATASOURCE_USERNAME = "lpn_app_admin"; SPRING_DATASOURCE_PASSWORD = $AppAdminPwd;
            BI_READONLY_DATABASE_URL = $BiReadonlyDbUrl; BI_READONLY_DATABASE_USER = "lpn_ai_readonly"; BI_READONLY_DATABASE_PASSWORD = $AiReadonlyPwd;
            SCHEMA_RETRIEVAL_BASE_URL = "http://localhost:8084"; SQL_EXECUTOR_BASE_URL = "http://localhost:8082";
            PREDICTIVE_BASE_URL = "http://localhost:8085"; OLLAMA_BASE_URL = "http://localhost:11434"
        }

    Write-Step "Waiting for services to become healthy..."
    Wait-HttpHealth -Name "sql-validator" -Url "http://localhost:8086/health"
    Wait-HttpHealth -Name "schema-retrieval" -Url "http://localhost:8084/health"
    Wait-HttpHealth -Name "llm-orchestrator" -Url "http://localhost:8081/actuator/health" -TimeoutSeconds 240

    # 6) frontend
    Start-BackgroundProcess -Name "frontend" -WorkDir (Join-Path $RepoRoot "frontend") `
        -Command "npm.cmd run dev -- --host 127.0.0.1 --port $FrontendPort" -EnvVars @{}
    Wait-HttpHealth -Name "frontend" -Url "http://127.0.0.1:$FrontendPort" -TimeoutSeconds 90

    if (-not $NoBrowser) { Start-Process "http://127.0.0.1:$FrontendPort" }

    Write-Step "All services launched natively."
    Write-Step "Frontend: http://127.0.0.1:$FrontendPort   Logs: $LogsRoot"
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    throw
}
