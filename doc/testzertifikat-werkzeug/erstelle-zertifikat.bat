@echo off
rem Erzeugt das Testzertifikat (DOCX + PDF) in einem Rutsch:
rem   1. node generate.js  -> testzertifikat.docx (Zahlen aus den PRUEFSCHICHTEN im Skript)
rem   2. LibreOffice        -> testzertifikat.pdf
rem   3. Kopie nach doc/SDN-Leistung-LMG-Testzertifikat.docx|.pdf
rem Vorher aktuell halten: Laufzahlen (zusammenfassung.txt), mvn test, ggf. LLM-Protokoll
rem und die Zeilen in generate.js (PRUEFSCHICHTEN).
setlocal
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo [FEHLER] node nicht gefunden - Node.js installieren oder PATH pruefen.
  pause & exit /b 1
)

echo [1/3] Erzeuge DOCX ...
node generate.js testzertifikat.docx
if errorlevel 1 ( echo [FEHLER] Generator fehlgeschlagen. & pause & exit /b 1 )

echo [2/3] Exportiere PDF ueber LibreOffice ...
set "SOFFICE=C:\Program Files\LibreOffice\program\soffice.exe"
if not exist "%SOFFICE%" set "SOFFICE=soffice"
"%SOFFICE%" --headless --convert-to pdf --outdir . testzertifikat.docx
if not exist testzertifikat.pdf ( echo [FEHLER] PDF-Export fehlgeschlagen. & pause & exit /b 1 )

echo [3/3] Kopiere nach doc\ ...
copy /y testzertifikat.docx "..\SDN-Leistung-LMG-Testzertifikat.docx" >nul
copy /y testzertifikat.pdf  "..\SDN-Leistung-LMG-Testzertifikat.pdf"  >nul

echo.
echo Fertig: doc\SDN-Leistung-LMG-Testzertifikat.pdf
start "" "..\SDN-Leistung-LMG-Testzertifikat.pdf"
endlocal
