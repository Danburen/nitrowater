@echo off
REM Thin launcher for stop-all.ps1 (PowerShell holds the real logic).
REM MySQL is left running unless -StopMysql is given.
setlocal
set "PS=powershell"
where pwsh >nul 2>&1 && set "PS=pwsh"
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1" %*
echo.
pause
endlocal
