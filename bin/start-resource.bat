@echo off
REM Start the unified resource service (nitrowater-resource, port 8093).
cd /d "%~dp0.."
call gradlew.bat :nitrowater-resource:bootRun
