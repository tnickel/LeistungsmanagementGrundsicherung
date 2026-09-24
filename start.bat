@echo off
setlocal
rem =====================================================================
rem  SDN.sozial - Leistungsmanagement Grundsicherung (LMG)
rem  Startet Weboberflaeche und JSON-API auf dem JDK-HttpServer.
rem
rem  Aufruf:  start.bat          -> Fachanwendung (Standard, Port 8080)
rem           start.bat demo     -> DemoMain (Saga-Beispiel mit Kompensation)
rem
rem  Konfiguration: die Umgebungsvariablen TX_URL, TX_USER, TX_PASS und
rem  LMG_PORT uebersteuern die unten gesetzten Vorgaben (Entwickler-DB
rem  im Docker-Container "lmg-oracle", Oracle 23ai Free).
rem =====================================================================

cd /d "%~dp0"

if "%TX_URL%"==""   set "TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
if "%TX_USER%"==""  set "TX_USER=tx_demo"
if "%TX_PASS%"==""  set "TX_PASS=tx_demo"
if "%LMG_PORT%"=="" set "LMG_PORT=8080"

where mvn >nul 2>nul
if errorlevel 1 (
    echo [Fehler] Maven ^(mvn^) nicht gefunden - bitte PATH pruefen.
    pause
    exit /b 1
)

rem ---------------------------------------------------------------------
rem  Entwickler-DB im Docker hochfahren, falls sie aus ist
rem ---------------------------------------------------------------------
echo %TX_URL% | findstr /c:"localhost:1521" >nul
if errorlevel 1 goto db_ok

where docker >nul 2>nul
if errorlevel 1 (
    echo [Hinweis] Docker nicht gefunden - Datenbank muss extern laufen.
    goto db_ok
)
docker info >nul 2>nul
if errorlevel 1 (
    echo [Hinweis] Docker-Deamon laeuft nicht - Datenbank muss extern laufen.
    goto db_ok
)
docker inspect lmg-oracle >nul 2>nul
if errorlevel 1 (
    echo [Hinweis] Kein Container "lmg-oracle" vorhanden - DB muss extern laufen.
    goto db_ok
)
docker ps --format "{{.Names}}" | findstr /x "lmg-oracle" >nul
if not errorlevel 1 goto db_bereit

echo Starte Entwickler-DB ^(Container lmg-oracle^) ...
docker start lmg-oracle >nul

docker inspect --format "{{.State.Health.Status}}" lmg-oracle 2>nul | findstr /r "." >nul
if errorlevel 1 (
    echo Kein Healthcheck verfuegbar - warte 30 Sekunden ...
    timeout /t 30 /nobreak >nul
    goto db_ok
)

set /a warte=0
:db_warten
docker inspect --format "{{.State.Health.Status}}" lmg-oracle 2>nul | findstr /x "healthy" >nul && goto db_bereit
set /a warte+=5
if %warte% geq 120 (
    echo [Warnung] DB nach 120s nicht gesund - Start wird trotzdem fortgesetzt.
    goto db_ok
)
echo Warte auf Datenbank ... %warte%s
timeout /t 5 /nobreak >nul 2>&1
goto db_warten

:db_bereit
echo Datenbank ist bereit.
:db_ok

rem ---------------------------------------------------------------------
rem  Bauen und Starten
rem ---------------------------------------------------------------------
echo Kompiliere ...
call mvn -q compile
if errorlevel 1 (
    echo [Fehler] Kompilierung fehlgeschlagen.
    pause
    exit /b 1
)

if /i "%~1"=="demo" (
    echo Starte Demo ^(Saga-Beispiel^) ...
    call mvn exec:java
    goto :ende
)

rem Browser oeffnen, sobald der Server hochgefahren ist (eigenes, minimiertes Fenster)
start "LMG-Browser" /min cmd /c "timeout /t 8 /nobreak >nul & start "" http://localhost:%LMG_PORT%/"

echo.
echo =====================================================================
echo   SDN.sozial - Leistungsmanagement Grundsicherung
echo   Oberflaeche: http://localhost:%LMG_PORT%/
echo   Stoppen: dieses Fenster schliessen oder Strg+C
echo =====================================================================
echo.
call mvn exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain

:ende
endlocal
