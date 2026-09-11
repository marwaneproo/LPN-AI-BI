@echo off
setlocal

cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\launch-lpn-ai-bi.ps1"

echo.
echo Launcher finished. You can close this window when you are done reading.
pause
