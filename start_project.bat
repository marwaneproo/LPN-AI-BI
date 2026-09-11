@echo off
setlocal

cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_project.ps1" %*

echo.
echo start_project finished. You can close this window when you are done reading.
pause
