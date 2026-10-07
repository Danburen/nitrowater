@echo off
cd /d "%~dp0..\.."
call gradlew.bat :nitrowater-account:bootRun
