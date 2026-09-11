param(
    [int]$FrontendPort = 5173,
    [int]$DbPort = 5432,
    [switch]$NoBrowser,
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path $PSScriptRoot
$LogsRoot = Join-Path $RepoRoot "logs"
$RuntimeDir = Join-Path $LogsRoot "_runtime"
$Launcher = Join-Path $RepoRoot "scripts\launch-local-native.ps1"
$ProjectPorts = @(8081, 8082, 8084, 8085, 8086, $FrontendPort) | Select-Object -Unique

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-Step {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message)
}

function Get-CommandLine {
    param([int]$ProcessId)
    try {
        return (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId").CommandLine
    } catch {
        return ""
    }
}

function Stop-ProcessTree {
    param(
        [int]$ProcessId,
        [string]$Reason
    )

    if ($ProcessId -le 0 -or $ProcessId -eq $PID) {
        return
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $process) {
        return
    }

    if ($process.ProcessName -in @("System", "Idle")) {
        Write-Step "Skipping protected process $($process.ProcessName) PID $ProcessId ($Reason)."
        return
    }

    Write-Step "Stopping $($process.ProcessName) PID $ProcessId ($Reason)."
    cmd.exe /c "taskkill /PID $ProcessId /T /F >nul 2>nul"
    Start-Sleep -Milliseconds 350
}

function Stop-PidFileProcesses {
    if (-not (Test-Path -LiteralPath $RuntimeDir)) {
        return
    }

    $nativeServicePidFiles = @(
        "frontend.native.pid",
        "llm-orchestrator.native.pid",
        "predictive.native.pid",
        "schema-retrieval.native.pid",
        "sql-executor.native.pid",
        "sql-validator.native.pid"
    )

    Get-ChildItem -LiteralPath $RuntimeDir -Filter "*.pid" -File | ForEach-Object {
        $pidFile = $_
        $name = $pidFile.Name

        # Leave a healthy native Ollama alone; the launcher checks its API.
        if ($name -eq "native-ollama.pid") {
            return
        }

        $rawPid = Get-Content -LiteralPath $pidFile.FullName -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($rawPid -match "^\d+$") {
            $pidValue = [int]$rawPid
            $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            $commandLine = if ($process) { Get-CommandLine -ProcessId $pidValue } else { "" }
            $isNativeService = $nativeServicePidFiles -contains $name
            $isDockerLogWatcher = $name.EndsWith(".logs.pid") -and $process -and $process.ProcessName -eq "cmd" -and $commandLine -like "*docker compose logs*"

            if ($isNativeService -or $isDockerLogWatcher) {
                Stop-ProcessTree -ProcessId $pidValue -Reason "stale runtime pid file $name"
            } else {
                Write-Step "Ignoring stale pid file $name because it does not point to a known project service."
            }
        }

        Remove-Item -LiteralPath $pidFile.FullName -Force -ErrorAction SilentlyContinue
    }
}

function Stop-PortConflicts {
    foreach ($port in $ProjectPorts) {
        $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
        if (-not $listeners) {
            Write-Step "Port $port is free."
            continue
        }

        foreach ($listener in $listeners) {
            $ownerPid = [int]$listener.OwningProcess
            $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
            $processName = if ($process) { $process.ProcessName } else { "unknown" }
            $commandLine = Get-CommandLine -ProcessId $ownerPid

            if ($ownerPid -eq $PID -or $processName -in @("System", "Idle")) {
                Write-Step "Port $port is owned by protected process $processName PID $ownerPid; skipping."
                continue
            }

            Stop-ProcessTree -ProcessId $ownerPid -Reason "port $port conflict; command: $commandLine"
        }

        $deadline = (Get-Date).AddSeconds(10)
        do {
            $remaining = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
            if (-not $remaining) {
                Write-Step "Port $port is now free."
                break
            }
            Start-Sleep -Milliseconds 500
        } while ((Get-Date) -lt $deadline)

        $remaining = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
        if ($remaining) {
            $ownerPid = [int]($remaining | Select-Object -First 1).OwningProcess
            $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
            $processName = if ($process) { $process.ProcessName } else { "unknown" }
            throw "Port $port is still occupied by $processName PID $ownerPid. Close it and run start_project again."
        }
    }
}

function Stop-ComposeStackIfAvailable {
    $docker = Get-Command "docker.exe" -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-Step "Docker CLI not found; skipping compose cleanup."
        return
    }

    cmd.exe /c "docker info >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
        Write-Step "Docker is not running; skipping compose cleanup."
        return
    }

    Write-Step "Stopping old Docker Compose containers for this project, if any."
    cmd.exe /c "docker compose down --remove-orphans >nul 2>nul"
}

function Test-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 8
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSeconds
        Write-Step "$Name OK ($($response.StatusCode)) at $Url"
        return $true
    } catch {
        Write-Step "$Name check failed at ${Url}: $($_.Exception.Message)"
        return $false
    }
}

function Test-LoginOk {
    param([string]$Url)

    try {
        $body = @{ username = "admin"; password = "admin" } | ConvertTo-Json
        $response = Invoke-RestMethod -Uri $Url -Method Post -ContentType "application/json" -Body $body -TimeoutSec 20
        if ($response.authenticated -eq $true -and $response.status -eq "APPROVED") {
            Write-Step "Admin login OK at $Url"
            return $true
        }
        Write-Step "Admin login failed cleanly at ${Url}: $($response.message)"
        return $false
    } catch {
        Write-Step "Admin login check failed at ${Url}: $($_.Exception.Message)"
        return $false
    }
}

Ensure-Directory $LogsRoot
Ensure-Directory $RuntimeDir

if (-not (Test-Path -LiteralPath $Launcher)) {
    throw "Missing launcher: $Launcher"
}

Write-Host ""
Write-Host "============================================="
Write-Host " LPN AI-BI local starter"
Write-Host "============================================="
Write-Host ""
Write-Step "Repo: $RepoRoot"
Write-Step "Mode: native services, PostgreSQL on localhost:$DbPort, frontend on $FrontendPort"

if (-not $SkipCleanup) {
    Write-Step "Cleaning stale services, log locks, and occupied app ports."
    Stop-PidFileProcesses
    Stop-PortConflicts
    Stop-ComposeStackIfAvailable
} else {
    Write-Step "Skipping cleanup because -SkipCleanup was provided."
}

$launcherArgs = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $Launcher,
    "-DbPort", "$DbPort",
    "-FrontendPort", "$FrontendPort"
)
if ($NoBrowser) {
    $launcherArgs += "-NoBrowser"
}

Write-Step "Launching project stack."
& powershell.exe @launcherArgs
if ($LASTEXITCODE -ne 0) {
    throw "Project launcher failed with exit code $LASTEXITCODE. Check logs under $LogsRoot."
}

Write-Step "Running final smoke checks."
$frontendOk = Test-HttpOk -Name "Frontend" -Url "http://127.0.0.1:$FrontendPort"
$overviewOk = Test-HttpOk -Name "BI overview API" -Url "http://localhost:8081/v1/bi/overview" -TimeoutSeconds 20
$loginOk = Test-LoginOk -Url "http://localhost:8081/v1/auth/login"

Write-Host ""
if ($frontendOk -and $overviewOk -and $loginOk) {
    Write-Host "Project is ready: http://127.0.0.1:$FrontendPort" -ForegroundColor Green
} else {
    Write-Host "Project launched, but one smoke check failed. Check logs under $LogsRoot." -ForegroundColor Yellow
}
Write-Host ""
