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
    $taskDocument = $taskWord.Documents.Open($taskInput, $false, $false)
    $taskRange = $taskDocument.Content
    if ($taskRange.Find.Execute('[[TOC]]')) {
        $taskRange.Text = ''
        [void]$taskDocument.TablesOfContents.Add($taskRange, $true, 1, 1, $false, '', $true, $true, 'Chapter Continuation,2', $true, $true, $false)
    }
    [void]$taskDocument.Fields.Update()
    $taskDocument.Repaginate()
    foreach ($taskToc in $taskDocument.TablesOfContents) { $taskToc.Update() }
    $taskDocument.Repaginate()
    foreach ($taskToc in $taskDocument.TablesOfContents) { $taskToc.UpdatePageNumbers() }
    $taskDocument.Save()
    $taskDocument.ExportAsFixedFormat($taskPdf, 17, $false, 0, 0, 1, 1, 0, $true, $true, 1, $true, $true, $false)
    Write-Output ('PDF pages: ' + $taskDocument.ComputeStatistics(2))
    Write-Output ('TOC count: ' + $taskDocument.TablesOfContents.Count)
    Write-Output $taskPdf
} finally {
    if ($null -ne $taskDocument) { $taskDocument.Close(0); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($taskDocument) }
    if ($null -ne $taskWord) { $taskWord.Quit(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($taskWord) }
}
