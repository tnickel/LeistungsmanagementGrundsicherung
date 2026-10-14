@echo off
setlocal
rem Nur einen nachweislich zu diesem Projekt gehoerenden SweetMain-Prozess stoppen.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
exit /b %ERRORLEVEL%