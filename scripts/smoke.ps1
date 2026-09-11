param(
    [switch]$SkipCompose,
    [switch]$SkipQa,
    [int]$StartupTimeoutSeconds = 240,
    [int]$QaTimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $RepoRoot ".env"
$EnvExamplePath = Join-Path $RepoRoot ".env.example"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Pass {
    param([string]$Message)
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
    return $values
}

function Get-EnvValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$Default
    )
    if ($Values.ContainsKey($Name) -and $Values[$Name]) {
        return $Values[$Name]
    }
    return $Default
}

function Convert-ToHostOllamaUrl {
    param([string]$BaseUrl)
    if (-not $BaseUrl) {
        return "http://localhost:11434"
    }
    return $BaseUrl.Replace("host.docker.internal", "localhost")
}

function Invoke-Json {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [object]$Body = $null,
        [int]$TimeoutSeconds = 15
    )
    $parameters = @{
        Uri = $Uri
        Method = $Method
        TimeoutSec = $TimeoutSeconds
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $parameters["ContentType"] = "application/json"
        $parameters["Body"] = ($Body | ConvertTo-Json -Depth 10)
    }
    return Invoke-RestMethod @parameters
}

function Wait-HttpJson {
    param(
        [string]$Name,
        [string]$Uri,
        [scriptblock]$Predicate,
        [int]$TimeoutSeconds = 120
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-Json -Uri $Uri -TimeoutSeconds 10
            if (& $Predicate $response) {
                Write-Pass "$Name is ready"
                return $response
            }
            $lastError = "Unexpected response: $($response | ConvertTo-Json -Compress -Depth 5)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 3
    }
    throw "$Name did not become ready at $Uri. Last error: $lastError"
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found on PATH: $Name"
    }
}

function Require-DockerDaemon {
    try {
        docker info --format "{{.ServerVersion}}" *> $null
    } catch {
        throw "Docker Desktop is not running or the Docker daemon is unreachable. Start Docker Desktop, then rerun scripts/smoke.ps1."
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop is not running or the Docker daemon is unreachable. Start Docker Desktop, then rerun scripts/smoke.ps1."
    }
}

Push-Location $RepoRoot
try {
    Write-Host "LPN AI-BI Phase 1 smoke test"
    Write-Host "Repository: $RepoRoot"

    if (-not (Test-Path -LiteralPath $EnvPath)) {
        if (Test-Path -LiteralPath $EnvExamplePath) {
            Copy-Item -LiteralPath $EnvExamplePath -Destination $EnvPath
            Write-Host "Created .env from .env.example. Review passwords before production use." -ForegroundColor Yellow
        } else {
            throw ".env is missing and .env.example could not be found."
        }
    }

    $envValues = Read-DotEnv -Path $EnvPath
    $ollamaContainerUrl = Get-EnvValue -Values $envValues -Name "OLLAMA_BASE_URL" -Default "http://host.docker.internal:11434"
    $ollamaHostUrl = Convert-ToHostOllamaUrl -BaseUrl $ollamaContainerUrl
    $sqlModel = Get-EnvValue -Values $envValues -Name "SQL_MODEL" -Default "qwen2.5-coder:7b"
    $narratorModel = Get-EnvValue -Values $envValues -Name "NARRATOR_MODEL" -Default "llama3.1:latest"

    Require-Command "docker"
    Require-DockerDaemon

    if (-not $SkipCompose) {
        Write-Step "Starting Docker Compose stack"
        docker compose up -d --wait --wait-timeout $StartupTimeoutSeconds
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose up failed with exit code $LASTEXITCODE"
        }
        Write-Pass "Docker Compose stack started"
    }

    Write-Step "Checking infrastructure and service health"
    $postgres = docker compose ps postgres --format json | ConvertFrom-Json
    if (-not $postgres -or ($postgres.Health -and $postgres.Health -ne "healthy")) {
        throw "PostgreSQL container is not healthy. Current state: $($postgres | ConvertTo-Json -Compress)"
    }
    Write-Pass "PostgreSQL container is healthy"

    Wait-HttpJson -Name "Qdrant" -Uri "http://localhost:6333/healthz" -TimeoutSeconds 120 `
        -Predicate { param($response) ($response.status -eq "ok") -or ($response.title -eq "ok") -or ($response -eq "healthz check passed") } | Out-Null

    Wait-HttpJson -Name "Schema Retrieval" -Uri "http://localhost:8084/health" -TimeoutSeconds 120 `
        -Predicate { param($response) $response.status -eq "ok" -and $response.qdrant_reachable -eq $true -and $response.collection_size -gt 0 } | Out-Null

    Wait-HttpJson -Name "SQL Validator" -Uri "http://localhost:8086/health" -TimeoutSeconds 120 `
        -Predicate { param($response) $response.status -eq "ok" } | Out-Null

    Wait-HttpJson -Name "SQL Executor" -Uri "http://localhost:8082/actuator/health" -TimeoutSeconds 180 `
        -Predicate { param($response) $response.status -eq "UP" } | Out-Null

    Wait-HttpJson -Name "LLM Orchestrator" -Uri "http://localhost:8081/actuator/health" -TimeoutSeconds 180 `
        -Predicate { param($response) $response.status -eq "UP" } | Out-Null

    $ollamaTags = Wait-HttpJson -Name "Ollama" -Uri "$ollamaHostUrl/api/tags" -TimeoutSeconds 60 `
        -Predicate { param($response) $null -ne $response.models }
    $availableModels = @($ollamaTags.models | ForEach-Object { $_.name })
    foreach ($model in @($sqlModel, $narratorModel)) {
        if ($availableModels -notcontains $model) {
            throw "Ollama model '$model' is not available at $ollamaHostUrl. Available models: $($availableModels -join ', ')"
        }
    }
    Write-Pass "Required Ollama models are available: $sqlModel, $narratorModel"

    if (-not $SkipQa) {
        Write-Step "Running end-to-end /v1/qa smoke question"
        $qaRequest = @{
            question = "How many orders were placed last month?"
            language = "en"
            mode = "standard"
            reasoning_mode = $false
            client_request_id = "smoke-$([guid]::NewGuid())"
            frontend_started_at = (Get-Date).ToUniversalTime().ToString("o")
        }
        $qaResponse = Invoke-Json -Uri "http://localhost:8081/v1/qa" -Method "POST" -Body $qaRequest -TimeoutSeconds $QaTimeoutSeconds

        if (-not $qaResponse.answer) {
            throw "/v1/qa response did not include a non-empty answer."
        }
        if (-not $qaResponse.sql) {
            throw "/v1/qa response did not include generated/executed SQL."
        }
        if ($null -eq $qaResponse.rows) {
            throw "/v1/qa response did not include a rows array."
        }
        if (-not $qaResponse.retrieved_tables -or $qaResponse.retrieved_tables.Count -eq 0) {
            throw "/v1/qa response did not include retrieved tables."
        }
        if (-not $qaResponse.latency_breakdown_ms) {
            throw "/v1/qa response did not include latency_breakdown_ms."
        }
        if ($qaResponse.execution_status -ne "SUCCESS") {
            throw "/v1/qa did not complete successfully. execution_status=$($qaResponse.execution_status), error=$($qaResponse.error)"
        }

        Write-Pass "/v1/qa returned answer, SQL, rows, retrieved tables, and latency breakdown"
        Write-Host "Trace ID: $($qaResponse.trace_id)"
        Write-Host "Row count: $($qaResponse.row_count)"
        Write-Host "Model used: $($qaResponse.model_used)"
    }

    Write-Host ""
    Write-Pass "Phase 1 smoke test passed"
    exit 0
} catch {
    Write-Host ""
    Write-Fail $_.Exception.Message
    Write-Host ""
    Write-Host "Helpful checks:"
    Write-Host "- docker compose ps"
    Write-Host "- docker compose logs --tail=120 llm-orchestrator sql-executor schema-retrieval sql-validator"
    Write-Host "- $ollamaHostUrl/api/tags"
    exit 1
} finally {
    Pop-Location
}
