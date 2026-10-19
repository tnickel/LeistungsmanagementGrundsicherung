$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$taskInput = Join-Path $taskRoot 'doc/Projektübersicht.docx'
$taskPdf = Join-Path $taskRoot 'doc/Projektübersicht.pdf'
$taskWord = $null
$taskDocument = $null
try {
    $taskWord = New-Object -ComObject Word.Application
    $taskWord.Visible = $false
    $taskWord.DisplayAlerts = 0
    $taskDocument = $taskWord.Documents.Open($taskInput, $false, $true)
    $taskDocument.Repaginate()
    $taskDocument.ExportAsFixedFormat($taskPdf, 17)
    Write-Output ('PDF pages: ' + $taskDocument.ComputeStatistics(2))
    Write-Output $taskPdf
} finally {
    if ($null -ne $taskDocument) { $taskDocument.Close(0); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($taskDocument) }
    if ($null -ne $taskWord) { $taskWord.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($taskWord) }
}
