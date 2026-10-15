$ErrorActionPreference = 'Stop'
$reviewRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $reviewRoot
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='tx_demo'
$env:TX_PASS='tx_demo'
$reviewResults = @()
function Invoke-ReviewStep([string]$Name,[scriptblock]$Action) {
    $reviewStart = [DateTimeOffset]::Now
    & $Action *> (Join-Path $PSScriptRoot "$Name.log")
    $reviewExit = $LASTEXITCODE
    $script:reviewResults += [ordered]@{name=$Name; startedAt=$reviewStart.ToString('o'); finishedAt=[DateTimeOffset]::Now.ToString('o'); exitCode=$reviewExit; log="$Name.log"}
    $script:reviewResults | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'baseline-results.json')
    Write-Output "$Name exit=$reviewExit"
    if ($reviewExit -ne 0) {throw "Review step $Name failed; inspect its log"}
}
Invoke-ReviewStep 'main-install-db' { & mvn install '-Dstyle.color=never' }
Remove-Item Env:TX_URL,Env:TX_USER,Env:TX_PASS
Invoke-ReviewStep 'main-config-without-db' { & mvn '-Dtest=LmgConfigTest' test '-Dstyle.color=never' }
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='tx_demo'
$env:TX_PASS='tx_demo'
Invoke-ReviewStep 'testsweet-db' { & mvn -f testsweet/pom.xml test '-Dstyle.color=never' }
Invoke-ReviewStep 'node-evidence' { & node --test doc/testzertifikat-werkzeug/certificate-evidence.test.js testsweet/src/test/js/llm-ui.test.js }
Invoke-ReviewStep 'python-reference' { & python -m unittest discover -s doc/testzertifikat-werkzeug -p test_nachrechnen.py }
Invoke-ReviewStep 'testsweet-classpath' { & mvn -f testsweet/pom.xml dependency:build-classpath "-Dmdep.outputFile=$PSScriptRoot/testsweet-classpath.txt" '-Dstyle.color=never' }
