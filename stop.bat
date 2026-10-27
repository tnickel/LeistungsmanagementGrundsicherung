@echo off
setlocal
rem =====================================================================
rem  SDN.Leistung - Leistungsmanagement Grundsicherung (LMG)
rem  Stoppt die laufende Fachanwendung (Server auf LMG_PORT, Standard 8080).
rem
rem  Aufruf:  stop.bat        -> nur Fachanwendung stoppen
rem           stop.bat db     -> zusaetzlich Docker-DB "lmg-oracle" stoppen
rem
rem  Es wird nur ein nachweislich zu diesem Projekt gehoerender
rem  LmgServerMain-Prozess beendet (Verifikation in stop.ps1).
rem =====================================================================
set "EXTRA="
if /i "%~1"=="db" set "EXTRA=-Db"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1" %EXTRA%
set "RC=%ERRORLEVEL%"
echo.
pause
exit /b %RC%
