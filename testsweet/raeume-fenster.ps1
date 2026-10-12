# Ausschließlich eigene, registrierte Chrome-Profile aufräumen. Keine Ahnen-/PID-Heuristik.
$basis = Join-Path $PSScriptRoot 'logs/browser-profile'
if (-not (Test-Path -LiteralPath $basis)) { exit 0 }
$profile = @(Get-ChildItem -LiteralPath $basis -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName '.testsweet-profile')
} | ForEach-Object { $_.FullName })
$prozesse = @(Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'")
foreach ($prozess in $prozesse) {
    # Gequotete und ungequotete user-data-dir-Werte, jeweils vollständig vergleichen.
    $treffer = [regex]::Match($prozess.CommandLine, '(?:^|\s)(?:"--user-data-dir=([^"]+)"|--user-data-dir="([^"]+)"|--user-data-dir=([^\s"]+))')
    if (-not $treffer.Success) { continue }
    $wert = @($treffer.Groups | Select-Object -Skip 1 | Where-Object Success | Select-Object -First 1).Value
    try { $voll = [System.IO.Path]::GetFullPath($wert).TrimEnd('\') } catch { continue }
    if ($profile -contains $voll) {
        Stop-Process -Id $prozess.ProcessId -Force -ErrorAction SilentlyContinue
    }
}
