$ErrorActionPreference='Stop'
$reviewRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$reviewLogs=(Resolve-Path (Join-Path $reviewRoot 'testsweet/logs')).Path
$reviewArchive=Join-Path $PSScriptRoot 'archived-runtime-runs'
$reviewArchive=[IO.Path]::GetFullPath($reviewArchive)
if(-not $reviewArchive.StartsWith($reviewRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive leaves workspace'}
$reviewNames=@(
 'run-20261015-100328-17716845934957858882',
 'run-20261015-100338-436171106041524070',
 'run-20261015-100346-211625280496513741',
 'run-20261015-100350-14808705979089760654',
 'run-20261015-100353-13234168534251085179',
 'run-20261015-100608-12296938910290168033',
 'run-20261015-100741-8270573836449113349',
 'llm-review-1b22edef-e45f-4e74-a86d-001ff7a63d51'
)
$reviewPlan=@()
foreach($reviewName in $reviewNames){
 $reviewSource=(Resolve-Path -LiteralPath (Join-Path $reviewLogs $reviewName)).Path
 if(-not $reviewSource.StartsWith($reviewLogs+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $reviewSource -Leaf) -ne $reviewName){throw 'Unexpected source'}
 $reviewItem=Get-Item -LiteralPath $reviewSource
 if($reviewItem.LinkType){throw 'Linked source not allowed'}
 $reviewTarget=Join-Path $reviewArchive $reviewName
 if(-not $reviewTarget.StartsWith($reviewArchive+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $reviewTarget)){throw 'Unexpected target'}
 $reviewStateFile=if($reviewName.StartsWith('run-')){'run-state.json'}else{'result.json'}
 $reviewState=Get-Content -Raw -LiteralPath (Join-Path $reviewSource $reviewStateFile) | ConvertFrom-Json
 if($reviewState.state -ne 'PASS' -or $reviewState.running -eq $true){throw 'Only completed own runs may be archived'}
 $reviewPlan += [ordered]@{name=$reviewName; source=$reviewSource; target=$reviewTarget; stateFile=$reviewStateFile; stateSha256=(Get-FileHash -LiteralPath (Join-Path $reviewSource $reviewStateFile)).Hash.ToLowerInvariant()}
}
New-Item -ItemType Directory -Path $reviewArchive | Out-Null
foreach($reviewMove in $reviewPlan){Move-Item -LiteralPath $reviewMove.source -Destination $reviewMove.target}
$reviewPlan | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'archived-run-map.json')
Write-Output "Archived $($reviewPlan.Count) completed own run directories; preexisting runs untouched"
