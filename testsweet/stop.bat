@echo off
setlocal
rem ============================================================
rem  testsweet - Stoppt die Simulator-Oberflaeche (SweetServer)
rem  Beendet den Prozess, der auf Port 8095 lauscht.
rem ============================================================

set "PORT=8095"
set "PID="

rem Lauschender Prozess: Zeile mit lokalem Port und Foreign-Adresse 0.0.0.0:0
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:":%PORT%" ^| findstr /c:"0.0.0.0:0"') do set "PID=%%p"

if "%PID%"=="" (
    echo testsweet laeuft nicht - nichts zu beenden ^(Port %PORT% frei^).
    pause
    exit /b 0
)

echo Beende testsweet ^(PID %PID%^) ...
taskkill /F /PID %PID% >nul 2>&1

rem kurze Kontrolle: Port wirklich frei?
ping -n 3 127.0.0.1 >nul
set "PID2="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:":%PORT%" ^| findstr /c:"0.0.0.0:0"') do set "PID2=%%p"
if "%PID2%"=="" (
    echo testsweet gestoppt ^(Port %PORT% ist frei^).
) else (
    echo [Warnung] Port %PORT% wird weiterhin von PID %PID2% belegt - bitte manuell pruefen.
)
pause
