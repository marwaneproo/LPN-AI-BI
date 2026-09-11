param(
    [int]$FrontendPort = 5173,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$LogsRoot = Join-Path $RepoRoot "logs"
$RuntimeDir = Join-Path $LogsRoot "_runtime"
$LauncherLogDir = Join-Path $LogsRoot "launcher"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$LauncherLog = Join-Path $LauncherLogDir "launcher-$Timestamp.log"
$LauncherErrorLog = Join-Path $LauncherLogDir "launcher-$Timestamp.error.log"

$Services = @(
    "postgres",
    "qdrant",
    "schema-retrieval",
    "sql-validator",
    "llm-orchestrator",
    "sql-executor"
)

$AppPorts = @(
    @{ Port = 5433; Name = "postgres" },
    @{ Port = 6333; Name = "qdrant" },
    @{ Port = 8084; Name = "schema-retrieval" },
    @{ Port = 8086; Name = "sql-validator" },
    @{ Port = 8081; Name = "llm-orchestrator" },
    @{ Port = 8082; Name = "sql-executor" },
    @{ Port = $FrontendPort; Name = "frontend" }
)

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-Step {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Path $LauncherLog -Value $line
}

function Write-Problem {
    param([string]$Message)
    $line = "[{0}] ERROR: {1}" -f (Get-Date -Format "HH:mm:ss"), $Message
    Write-Host $line -ForegroundColor Red
    Add-Content -Path $LauncherErrorLog -Value $line
}

function Get-CommandLine {
    param([int]$ProcessId)
    try {
        return (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId").CommandLine
    } catch {
        return ""
    }
}

function Stop-PidFileProcess {
    param(
        [string]$PidFile,
        [string]$Label
    )

    if (-not (Test-Path $PidFile)) {
        return
    }

    $rawPid = (Get-Content -Path $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $rawPid) {
        Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
        return
    }

    $process = Get-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue
    if ($process) {
        Write-Step "Stopping previous $Label process PID $rawPid."
        cmd.exe /c "taskkill /PID $rawPid /T /F >nul 2>nul"
    }

    Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
}

function Start-DockerDesktop {
    cmd.exe /c "docker info >nul 2>nul"
    if ($LASTEXITCODE -eq 0) {
        Write-Step "Docker is already running."
        return
    }

    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $dockerDesktop)) {
        throw "Docker Desktop executable not found at $dockerDesktop"
    }

    Write-Step "Docker is not running. Starting Docker Desktop."
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden

    $deadline = (Get-Date).AddMinutes(5)
    do {
        Start-Sleep -Seconds 5
        cmd.exe /c "docker info >nul 2>nul"
        if ($LASTEXITCODE -eq 0) {
            Write-Step "Docker is ready."
            return
        }
        Write-Step "Waiting for Docker Desktop..."
    } while ((Get-Date) -lt $deadline)

    throw "Docker did not become ready within 5 minutes."
}

function Get-EnvValue {
    param(
        [string]$Name,
        [string]$Fallback
    )

    $envFile = Join-Path $RepoRoot ".env"
    if (-not (Test-Path $envFile)) {
        return $Fallback
    }

    $match = Get-Content -Path $envFile |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -Last 1

    if (-not $match) {
        return $Fallback
    }

    return (($match -split "=", 2)[1]).Trim().Trim('"').Trim("'")
}

function Get-OllamaExecutable {
    $envOllamaExe = Get-EnvValue -Name "OLLAMA_EXE" -Fallback ""
    $candidatePaths = @(
        $envOllamaExe,
        (Get-Command "ollama.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
        (Join-Path $env:LOCALAPPDATA "Programs\Ollama\ollama.exe"),
        (Join-Path $env:ProgramFiles "Ollama\ollama.exe")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidatePaths) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Native Ollama executable was not found. Install Ollama or set OLLAMA_EXE in .env to the full path of ollama.exe."
}

function Start-NativeOllama {
    $nativeOllamaLogDir = Join-Path $LogsRoot "native-ollama"
    Ensure-Directory $nativeOllamaLogDir

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Step "Native Ollama API is already running at http://localhost:11434."
            return
        }
    } catch {
        Write-Step "Native Ollama API is not responding. Trying to start 'ollama serve'."
    }

    $pidFile = Join-Path $RuntimeDir "native-ollama.pid"
    Stop-PidFileProcess -PidFile $pidFile -Label "native Ollama"

    $stdout = Join-Path $nativeOllamaLogDir "native-ollama.log"
    $stderr = Join-Path $nativeOllamaLogDir "native-ollama.error.log"
    $ollamaExe = Get-OllamaExecutable
    Add-Content -Path $stdout -Value ""
    Add-Content -Path $stdout -Value "===== native Ollama logs started $(Get-Date -Format s) ====="
    Write-Step "Starting native Ollama from $ollamaExe."

    $process = Start-Process -FilePath $ollamaExe `
        -ArgumentList "serve" `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id

    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Step "Native Ollama API is ready at http://localhost:11434."
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Native Ollama did not become ready at http://localhost:11434. Start the Ollama app manually and rerun this launcher."
}

function Assert-NativeOllamaModel {
    param([string]$Model)

    if ([string]::IsNullOrWhiteSpace($Model)) {
        return
    }

    $tags = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -Method Get -TimeoutSec 10
    $installed = @($tags.models | ForEach-Object { $_.name })
    if ($installed -notcontains $Model) {
        throw "Native Ollama model '$Model' is not installed. Run: ollama pull $Model"
    }

    Write-Step "Native Ollama model found: $Model"
}

function Stop-AppPortConflicts {
    Write-Step "Stopping existing Compose containers to free Docker-managed ports."
    $composeDownLog = Join-Path $LauncherLogDir "compose-down.log"
    $composeDownErrorLog = Join-Path $LauncherLogDir "compose-down.error.log"
    cmd.exe /c "docker compose down --remove-orphans >> `"$composeDownLog`" 2>> `"$composeDownErrorLog`""

    foreach ($entry in $AppPorts) {
        $port = [int]$entry.Port
        $name = [string]$entry.Name
        $listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

        foreach ($listener in $listeners) {
            $ownerPid = [int]$listener.OwningProcess
            if ($ownerPid -le 0) {
                continue
            }

            $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
            $processName = if ($process) { $process.ProcessName } else { "unknown" }
            $commandLine = Get-CommandLine -ProcessId $ownerPid
            $isDockerProxy = $processName -in @("com.docker.backend", "wslrelay", "vpnkit")
            $isFrontendNode = $name -eq "frontend" -and $processName -eq "node" -and (
                $commandLine -like "*$RepoRoot*" -or
                $commandLine -like "*vite*" -or
                $commandLine -like "*frontend*"
            )

            if ($isDockerProxy) {
                Write-Step "Port $port is owned by Docker proxy PID $ownerPid. Leaving Docker process alive."
                continue
            }

            if ($ownerPid -eq $PID -or $processName -eq "System") {
                Write-Step "Port $port is owned by protected process $processName PID $ownerPid. Skipping."
                continue
            }

            if ($isFrontendNode -or $name -ne "frontend") {
                Write-Step "Freeing port $port for $name by stopping $processName PID $ownerPid."
                cmd.exe /c "taskkill /PID $ownerPid /T /F >nul 2>nul"
            }
        }

        $deadline = (Get-Date).AddSeconds(12)
        do {
            $remaining = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
                Where-Object {
                    $remainingPid = [int]$_.OwningProcess
                    if ($remainingPid -le 0) {
                        return $false
                    }

                    $remainingProcess = Get-Process -Id $remainingPid -ErrorAction SilentlyContinue
                    $remainingName = if ($remainingProcess) { $remainingProcess.ProcessName } else { "unknown" }
                    return $remainingName -notin @("com.docker.backend", "wslrelay", "vpnkit")
                }

            if (-not $remaining) {
                break
            }

            Start-Sleep -Milliseconds 500
        } while ((Get-Date) -lt $deadline)

        $remaining = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Where-Object {
                $remainingPid = [int]$_.OwningProcess
                if ($remainingPid -le 0) {
                    return $false
                }

                $remainingProcess = Get-Process -Id $remainingPid -ErrorAction SilentlyContinue
                $remainingName = if ($remainingProcess) { $remainingProcess.ProcessName } else { "unknown" }
                return $remainingName -notin @("com.docker.backend", "wslrelay", "vpnkit")
            }

        if ($remaining) {
            $ownerPid = [int]($remaining | Select-Object -First 1).OwningProcess
            $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
            $processName = if ($process) { $process.ProcessName } else { "unknown" }
            throw "Port $port for $name is still occupied by $processName PID $ownerPid. Close that process and rerun the launcher."
        }
    }
}

function Start-ServiceLogWatcher {
    param([string]$Service)

    $serviceLogDir = Join-Path $LogsRoot $Service
    Ensure-Directory $serviceLogDir

    $pidFile = Join-Path $RuntimeDir "$Service.logs.pid"
    Stop-PidFileProcess -PidFile $pidFile -Label "$Service log watcher"

    $stdout = Join-Path $serviceLogDir "$Service.log"
    $stderr = Join-Path $serviceLogDir "$Service.error.log"
    Add-Content -Path $stdout -Value ""
    Add-Content -Path $stdout -Value "===== $Service logs started $(Get-Date -Format s) ====="
    $command = "cd /d `"$RepoRoot`" && docker compose logs --no-color --timestamps --follow $Service >> `"$stdout`" 2>> `"$stderr`""

    $process = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", $command `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id
    Write-Step "Log watcher started for $Service. PID $($process.Id)."
}

function Start-Frontend {
    $frontendLogDir = Join-Path $LogsRoot "frontend"
    Ensure-Directory $frontendLogDir

    $pidFile = Join-Path $RuntimeDir "frontend.pid"
    Stop-PidFileProcess -PidFile $pidFile -Label "frontend"

    $stdout = Join-Path $frontendLogDir "frontend.log"
    $stderr = Join-Path $frontendLogDir "frontend.error.log"
    Add-Content -Path $stdout -Value ""
    Add-Content -Path $stdout -Value "===== frontend logs started $(Get-Date -Format s) ====="

    Write-Step "Starting frontend on http://127.0.0.1:$FrontendPort"
    $frontendDir = Join-Path $RepoRoot "frontend"
    $viteCmd = Join-Path $frontendDir "node_modules\.bin\vite.cmd"
    if (Test-Path $viteCmd) {
        $command = "cd /d `"$frontendDir`" && `"$viteCmd`" --host 127.0.0.1 --port $FrontendPort >> `"$stdout`" 2>> `"$stderr`""
    } else {
        $command = "cd /d `"$frontendDir`" && npm.cmd run dev -- --host 127.0.0.1 --port $FrontendPort >> `"$stdout`" 2>> `"$stderr`""
    }
    $process = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", $command `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id

    $deadline = (Get-Date).AddSeconds(60)
    do {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$FrontendPort" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Step "Frontend is ready at http://127.0.0.1:$FrontendPort"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Frontend did not become ready on port $FrontendPort within 60 seconds."
}

function Wait-HttpHealth {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Step "$Name is healthy at $Url"
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    } while ((Get-Date) -lt $deadline)

    throw "$Name did not become healthy at $Url within $TimeoutSeconds seconds."
}

Ensure-Directory $LogsRoot
Ensure-Directory $RuntimeDir
Ensure-Directory $LauncherLogDir

try {
    Write-Step "Starting LPN AI-BI launcher from $RepoRoot"

    if (-not (Test-Path (Join-Path $RepoRoot ".env"))) {
        Write-Step ".env not found. Creating it from .env.example."
        Copy-Item -Path (Join-Path $RepoRoot ".env.example") -Destination (Join-Path $RepoRoot ".env")
    }

    Start-DockerDesktop
    Start-NativeOllama
    Assert-NativeOllamaModel -Model (Get-EnvValue -Name "SQL_MODEL" -Fallback "qwen2.5-coder:7b")
    Assert-NativeOllamaModel -Model (Get-EnvValue -Name "SQL_REASONING_MODEL" -Fallback "qwen2.5-coder:14b")
    Assert-NativeOllamaModel -Model (Get-EnvValue -Name "SQL_FALLBACK_MODEL" -Fallback "qwen2.5-coder:7b")
    Assert-NativeOllamaModel -Model (Get-EnvValue -Name "NARRATOR_MODEL" -Fallback "llama3.1:latest")
    Set-Location $RepoRoot
    Stop-AppPortConflicts

    Write-Step "Starting Docker Compose stack."
    $composeUpLog = Join-Path $LauncherLogDir "compose-up.log"
    $composeUpErrorLog = Join-Path $LauncherLogDir "compose-up.error.log"
    cmd.exe /c "docker compose up -d --build >> `"$composeUpLog`" 2>> `"$composeUpErrorLog`""
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed. See logs\launcher\compose-up.error.log"
    }

    foreach ($service in $Services) {
        Start-ServiceLogWatcher -Service $service
    }

    Wait-HttpHealth -Name "llm-orchestrator" -Url "http://localhost:8081/actuator/health" -TimeoutSeconds 120
    Wait-HttpHealth -Name "schema-retrieval" -Url "http://localhost:8084/health" -TimeoutSeconds 120
    Wait-HttpHealth -Name "sql-validator" -Url "http://localhost:8086/health" -TimeoutSeconds 120

    Start-Frontend

    if (-not $NoBrowser) {
        Start-Process "http://127.0.0.1:$FrontendPort"
    }

    Write-Step "All services launched successfully."
    Write-Step "Frontend: http://127.0.0.1:$FrontendPort"
    Write-Step "Logs root: $LogsRoot"
} catch {
    Write-Problem $_.Exception.Message
    Write-Problem "Check $LauncherErrorLog and service logs under $LogsRoot"
    throw
}
