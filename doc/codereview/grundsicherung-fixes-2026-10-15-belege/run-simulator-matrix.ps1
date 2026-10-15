$ErrorActionPreference='Stop'
$fixRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $fixRoot
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='FIX_20261015'
if ([string]::IsNullOrWhiteSpace($env:TX_PASS)) { throw 'TX_PASS für das eigene Fixschema setzen' }
$fixCp=(Join-Path $fixRoot 'testsweet/target/classes') + ';' + (Get-Content -Raw (Join-Path $PSScriptRoot 'testsweet-classpath.txt')).Trim()
$fixJobs=@(
    @{mode='web'; category='POSITIV'; employees=3; orders=60},
    @{mode='web'; category='NEGATIV'; employees=3; orders=60},
    @{mode='web'; category='LAST'; employees=3; orders=30},
    @{mode='run'; category='POSITIV'; employees=8; orders=500},
    @{mode='run'; category='NEGATIV'; employees=8; orders=500},
    @{mode='run'; category='LAST'; employees=8; orders=120},
    @{mode='run'; category='STRESS'; employees=4; orders=21}
)
$fixResults=@()
foreach($fixJob in $fixJobs) {
    $fixName="$($fixJob.mode)-$($fixJob.category)"
    $fixStart=[DateTimeOffset]::Now
    Write-Output "Starting $fixName"
    & java '-Djava.awt.headless=false' '-Dlmg.port=18180' '-Dsweet.requireEmbedded=true' -cp $fixCp de.allgemein.testsweet.SweetMain $fixJob.mode $fixJob.employees $fixJob.orders 20261015 0 $fixJob.category *> (Join-Path $PSScriptRoot "$fixName.log")
    $fixExit=$LASTEXITCODE
    $fixResults += [ordered]@{mode=$fixJob.mode; category=$fixJob.category; employees=$fixJob.employees; configuredOrders=$fixJob.orders; seed=20261015; startedAt=$fixStart.ToString('o'); finishedAt=[DateTimeOffset]::Now.ToString('o'); exitCode=$fixExit; log="$fixName.log"; schema='FIX_20261015'}
    $fixResults | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'simulator-matrix-results.json')
    Write-Output "$fixName exit=$fixExit"
    if ($fixExit -ne 0) {throw "Simulator $fixName fehlgeschlagen"}
}
