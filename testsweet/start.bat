@echo off
setlocal
rem =====================================================================
rem  testsweet - Mitarbeiter-Simulator (SDN.sozial LMG)
rem  Startet die Simulator-Oberflaeche auf http://127.0.0.1:8095/
rem
rem  Voraussetzung: Hauptprojekt mindestens einmal mit "mvn install"
rem  installiert (nach Aenderungen am Hauptprojekt wiederholen).
rem  Datenbank: TX_URL/TX_USER/TX_PASS (Standard: Docker-DB 1521/FREEPDB1)
rem =====================================================================

cd /d "%~dp0.."

where mvn >nul 2>nul
if errorlevel 1 (
    echo [Fehler] Maven ^(mvn^) nicht gefunden - bitte PATH pruefen.
    pause
    exit /b 1
)

call mvn -q -f testsweet/pom.xml compile exec:java
if errorlevel 1 pause
endlocal
