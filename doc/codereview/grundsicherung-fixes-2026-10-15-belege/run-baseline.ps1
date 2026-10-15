$ErrorActionPreference = 'Stop'
$fixRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $fixRoot
if ([string]::IsNullOrWhiteSpace($env:TX_PASS)) { throw 'TX_PASS für das eigene Fixschema setzen' }
$fixPassword = $env:TX_PASS
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='FIX_20261015'
$fixResults = @()
function Invoke-FixStep([string]$Name,[scriptblock]$Action) {
    $fixStart = [DateTimeOffset]::Now
    & $Action *> (Join-Path $PSScriptRoot "$Name.log")
    $fixExit = $LASTEXITCODE
    $script:fixResults += [ordered]@{name=$Name; startedAt=$fixStart.ToString('o'); finishedAt=[DateTimeOffset]::Now.ToString('o'); exitCode=$fixExit; log="$Name.log"}
    $script:fixResults | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'baseline-results.json')
    Write-Output "$Name exit=$fixExit"
    if ($fixExit -ne 0) {throw "Fix step $Name failed; inspect its log"}
}
Invoke-FixStep 'main-install-db' { & mvn install '-Dstyle.color=never' }
Remove-Item Env:TX_URL,Env:TX_USER,Env:TX_PASS
Invoke-FixStep 'main-config-without-db' { & mvn '-Dtest=LmgConfigTest' test '-Dstyle.color=never' }
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='FIX_20261015'
$env:TX_PASS=$fixPassword
Invoke-FixStep 'testsweet-db' { & mvn -f testsweet/pom.xml test '-Dstyle.color=never' }
Invoke-FixStep 'node-evidence' { & node --test doc/testzertifikat-werkzeug/certificate-evidence.test.js doc/testzertifikat-werkzeug/llm-answer-parity.test.js doc/testzertifikat-werkzeug/llm-reference.test.js testsweet/src/test/js/llm-ui.test.js }
Invoke-FixStep 'python-reference' { & python -m unittest discover -s doc/testzertifikat-werkzeug -p test_nachrechnen.py }
Invoke-FixStep 'testsweet-classpath' { & mvn -f testsweet/pom.xml dependency:build-classpath "-Dmdep.outputFile=$PSScriptRoot/testsweet-classpath.txt" '-Dstyle.color=never' }
