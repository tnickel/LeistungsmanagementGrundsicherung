@echo off
setlocal DisableDelayedExpansion
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
rem  FREEPDB1, Port 1521) - sie wird bei Bedarf automatisch gestartet.
rem  Alternativ Oracle 19c EE in der VirtualBox-VM "oracle-19c-adminwkshop":
rem  set TX_URL=jdbc:oracle:thin:@//localhost:1921/o19c1p1
rem =====================================================================

cd /d "%~dp0"

if not defined TX_URL set "TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
if not defined TX_USER set "TX_USER=tx_demo"
if not defined TX_PASS set "TX_PASS=tx_demo"
if not defined LMG_PORT set "LMG_PORT=8080"

where mvn >nul 2>nul
if errorlevel 1 (
    echo [Fehler] Maven ^(mvn^) nicht gefunden - bitte PATH pruefen.
    pause
    exit /b 1
)

rem ---------------------------------------------------------------------
rem  Datenbankbereitschaft mit sicherer Argumentuebergabe und Gesamtfrist
rem ---------------------------------------------------------------------
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Wait-LmgDatabase.ps1" -TimeoutSeconds 300
if errorlevel 1 echo [Hinweis] Datenbankpruefung nicht erfolgreich - Anwendung meldet Verbindungsfehler bei Bedarf.

:db_ok

rem ---------------------------------------------------------------------
rem  Bauen und Starten
rem ---------------------------------------------------------------------
echo Kompiliere ...
call mvn -f "%~dp0pom.xml" -q compile
if errorlevel 1 (
    echo [Fehler] Kompilierung fehlgeschlagen.
    pause
    exit /b 1
)

if /i "%~1"=="demo" (
    echo Starte Demo ^(Saga-Beispiel^) ...
    call mvn -f "%~dp0pom.xml" exec:java
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
call mvn -f "%~dp0pom.xml" exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain

:ende
endlocal
