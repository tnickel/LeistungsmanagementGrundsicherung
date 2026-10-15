$ErrorActionPreference='Stop'
$fixRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $fixRoot
if ([string]::IsNullOrWhiteSpace($env:TX_PASS)) { throw 'TX_PASS für das eigene Fixschema setzen' }
& (Join-Path $PSScriptRoot 'capture-state.ps1') before
& (Join-Path $PSScriptRoot 'run-baseline.ps1')
& node (Join-Path $PSScriptRoot 'check-junit-reader.js') *> (Join-Path $PSScriptRoot 'junit-reader-early-check.log')
if($LASTEXITCODE -ne 0){throw 'Zertifikatsleser akzeptiert den JUnit-Nachweis nicht'}
Write-Output 'JUnit-Nachweise vom Zertifikatsleser akzeptiert'
& (Join-Path $PSScriptRoot 'run-simulator-matrix.ps1')
& python (Join-Path $PSScriptRoot 'inspect-results.py') *> (Join-Path $PSScriptRoot 'simulator-inspection.log')
if($LASTEXITCODE -ne 0){throw 'Simulator-Belegprüfung fehlgeschlagen'}
Write-Output 'Alle sieben Simulator-Belege vollständig; starte GLM mit 18 Fällen'
$fixCp=(Join-Path $fixRoot 'testsweet/target/classes')+';'+(Get-Content -Raw (Join-Path $PSScriptRoot 'testsweet-classpath.txt')).Trim()+';'+(Join-Path $PSScriptRoot 'llm-classes')
& java -cp $fixCp ReviewLlmRun $PSScriptRoot 18 *> (Join-Path $PSScriptRoot 'llm-run.log')
if($LASTEXITCODE -ne 0){throw 'GLM-Helfer fehlgeschlagen'}
$fixStatus=Get-Content -Raw (Join-Path $PSScriptRoot 'llm-review-status.json') | ConvertFrom-Json
if($fixStatus.state -ne 'PASS'){throw "GLM-Endzustand $($fixStatus.state); Ergebnisbericht prüfen"}
& (Join-Path $PSScriptRoot 'capture-state.ps1') after
Write-Output 'Frische Gesamtverifikation abgeschlossen'
