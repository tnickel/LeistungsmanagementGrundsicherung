param([ValidateSet('before','after')][string]$Stage = 'before')
$ErrorActionPreference = 'Stop'
$reviewRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $reviewRoot
$reviewFiles = @()
foreach ($reviewDir in @('src','testsweet/src','ddl','tools','doc/testzertifikat-werkzeug')) {
    $reviewFiles += @(Get-ChildItem -LiteralPath $reviewDir -File -Recurse | Where-Object {
        $_.FullName -notmatch '[\\/](node_modules|__pycache__)[\\/]'
    })
}
foreach ($reviewFile in @('AGENTS.md','README.md','pom.xml','testsweet/pom.xml','testsweet/README.md','doc/projektbeschreibung.md','doc/architektur.md','doc/leistungsmanagement.md','doc/rechtsgrundlagen.md','doc/regressionstests.md','doc/roadmap.md','doc/codereview/grundsicherung-gesamt-reviewPrompt.txt')) {
    $reviewFiles += Get-Item -LiteralPath $reviewFile
}
$reviewInventory = @($reviewFiles | Sort-Object FullName -Unique | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($reviewRoot.Length+1).Replace('\','/'); bytes=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$reviewInventory | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot "inventory-$Stage.json")
$reviewState = [ordered]@{stage=$Stage; capturedAt=[DateTimeOffset]::Now.ToString('o'); commit=(& git rev-parse HEAD); branch=(& git branch --show-current); status=@(& git status --short); java=@(& java -version 2>&1 | ForEach-Object {"$_"}); maven=@(& mvn -version 2>&1 | ForEach-Object {"$_"}); docker=@(& docker ps --filter name=lmg-oracle --format '{{.Names}} {{.Status}} {{.Ports}}'); files=$reviewInventory.Count}
$reviewState | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot "state-$Stage.json")
Write-Output "Captured $Stage : $($reviewInventory.Count) files"
