@echo off
setlocal
rem =====================================================================
rem  SDN.Leistung - Leistungsmanagement Grundsicherung (LMG)
rem  Startet Weboberflaeche und JSON-API auf dem JDK-HttpServer.
rem
rem  Aufruf:  start.bat          -> Fachanwendung (Standard, Port 8080)
rem           start.bat demo     -> DemoMain (Saga-Beispiel mit Kompensation)
rem
rem  Konfiguration: die Umgebungsvariablen TX_URL, TX_USER, TX_PASS und
rem  LMG_PORT uebersteuern die unten gesetzten Vorgaben. Standard ist die
rem  Entwickler-DB im Docker-Container "lmg-oracle" (Oracle Free, PDB
rem  FREEPDB1, Port 1521) — sie wird bei Bedarf automatisch gestartet.
rem  Alternativ Oracle 19c EE in der VirtualBox-VM "oracle-19c-adminwkshop":
rem  set TX_URL=jdbc:oracle:thin:@//localhost:1921/o19c1p1
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
rem  Standard: Entwickler-DB im Docker hochfahren, falls sie aus ist
rem ---------------------------------------------------------------------
echo %TX_URL% | findstr /c:"localhost:1521" >nul
if errorlevel 1 goto vm_check

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
if not errorlevel 1 goto db_warten

echo Starte Entwickler-DB ^(Container lmg-oracle^) ...
docker start lmg-oracle >nul

:db_warten
rem Der Container lmg-oracle hat keinen Docker-Healthcheck; Bereitschaft
rem daher echt pruefen: sqlplus-Login gilt erst, wenn Listener UND PDB offen sind.
docker exec lmg-oracle bash -c "echo 'select 1 from dual;' | sqlplus -S -L %TX_USER%/%TX_PASS%@localhost:1521/FREEPDB1" >nul 2>nul
if not errorlevel 1 goto db_bereit
set /a warte+=5
if %warte% geq 300 (
    echo [Warnung] DB nach 300s nicht bereit - Start wird trotzdem fortgesetzt.
    goto db_ok
)
echo Warte auf Datenbank ... %warte%s
timeout /t 5 /nobreak >nul 2>&1
goto db_warten

:db_bereit
echo Datenbank ist bereit.
goto db_ok

rem ---------------------------------------------------------------------
rem  Alternative: Oracle 19c in VirtualBox (Port 1921) nur erreichbar
rem  pruefen — die VM muss von Hand in VirtualBox gestartet werden.
rem ---------------------------------------------------------------------
:vm_check
echo %TX_URL% | findstr /c:"localhost:1921" >nul
if errorlevel 1 goto db_ok
powershell -NoProfile -Command "if (!(Test-NetConnection -ComputerName 127.0.0.1 -Port 1921 -InformationLevel Quiet)) { exit 1 }" >nul 2>nul
if errorlevel 1 (
    echo [Warnung] VirtualBox Oracle-DB auf Port 1921 antwortet nicht.
    echo Bitte VM "oracle-19c-adminwkshop" in VirtualBox starten.
) else (
    echo Oracle 19c Datenbank ^(VirtualBox, Port 1921^) ist erreichbar.
)

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
echo   SDN.Leistung - Leistungsmanagement Grundsicherung
echo   Oberflaeche: http://localhost:%LMG_PORT%/
echo   Stoppen: dieses Fenster schliessen oder Strg+C
echo =====================================================================
echo.
call mvn exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain

:ende
endlocal
