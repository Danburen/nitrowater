@echo off
REM Thin launcher for start-all.ps1 (PowerShell holds the real logic).
setlocal
set "PS=powershell"
where pwsh >nul 2>&1 && set "PS=pwsh"
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
echo.
pause
endlocal
