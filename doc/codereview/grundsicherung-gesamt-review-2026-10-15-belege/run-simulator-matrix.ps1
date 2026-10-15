$ErrorActionPreference='Stop'
$reviewRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $reviewRoot
$env:TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER='REVIEW_20261015'
if ([string]::IsNullOrWhiteSpace($env:TX_PASS)) { throw 'TX_PASS für das eigene Reviewschema vor dem Lauf setzen' }
$reviewCp=(Join-Path $reviewRoot 'testsweet/target/classes') + ';' + (Get-Content -Raw (Join-Path $PSScriptRoot 'testsweet-classpath.txt')).Trim()
$reviewJobs=@(
    @{mode='run'; category='POSITIV'; employees=8; orders=500},
    @{mode='run'; category='NEGATIV'; employees=8; orders=500},
    @{mode='run'; category='LAST'; employees=8; orders=120},
    @{mode='run'; category='STRESS'; employees=4; orders=21},
    @{mode='web'; category='POSITIV'; employees=3; orders=60},
    @{mode='web'; category='NEGATIV'; employees=3; orders=60},
    @{mode='web'; category='LAST'; employees=3; orders=30}
)
$reviewResults=@()
foreach($reviewJob in $reviewJobs) {
    $reviewName="$($reviewJob.mode)-$($reviewJob.category)"
    $reviewStart=[DateTimeOffset]::Now
    Write-Output "Starting $reviewName"
    & java '-Djava.awt.headless=false' '-Dlmg.port=18180' '-Dsweet.requireEmbedded=true' -cp $reviewCp de.allgemein.testsweet.SweetMain $reviewJob.mode $reviewJob.employees $reviewJob.orders 20261015 0 $reviewJob.category *> (Join-Path $PSScriptRoot "$reviewName.log")
    $reviewExit=$LASTEXITCODE
    $reviewResults += [ordered]@{mode=$reviewJob.mode; category=$reviewJob.category; employees=$reviewJob.employees; configuredOrders=$reviewJob.orders; seed=20261015; startedAt=$reviewStart.ToString('o'); finishedAt=[DateTimeOffset]::Now.ToString('o'); exitCode=$reviewExit; log="$reviewName.log"; schema='REVIEW_20261015'}
    $reviewResults | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'simulator-matrix-results.json')
    Write-Output "$reviewName exit=$reviewExit"
}
