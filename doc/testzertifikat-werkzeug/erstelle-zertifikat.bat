@echo off
rem Gemeinsame fail-closed Pipeline fuer GUI und Batch. Auch negative Zertifikate
rem werden erstellt; Prozess-Erfolg ist keine fachliche Freigabe.
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo [FEHLER] node nicht gefunden - Node.js installieren oder PATH pruefen.
  exit /b 1
)
node publish-certificate.js --publish
if errorlevel 1 (
  echo [FEHLER] Kein aktuelles Zertifikat veroeffentlicht. Exportlog und Staging pruefen.
  exit /b 1
)
echo Zertifikat neu erzeugt: doc\SDN-Leistung-LMG-Testzertifikat.pdf
echo Das fachliche Urteil steht auf dem Deckblatt und in stand.json.
start "" "..\SDN-Leistung-LMG-Testzertifikat.pdf"
endlocal
exit /b 0
