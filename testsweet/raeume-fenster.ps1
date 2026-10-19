param([switch]$AllDrivers)

$basis = Join-Path $PSScriptRoot 'logs/browser-profile'
$count = 0

if (-not (Test-Path -LiteralPath $basis) -and -not $AllDrivers) {
    exit 0
}

# 1. Ausschließlich eigene, registrierte Chrome-Profile und deren Prozesse aufräumen
if (Test-Path -LiteralPath $basis) {
    $profile = @(Get-ChildItem -LiteralPath $basis -Directory -ErrorAction SilentlyContinue | Where-Object {
        Test-Path -LiteralPath (Join-Path $_.FullName '.testsweet-profile')
    } | ForEach-Object { $_.FullName })

    if ($profile.Count -gt 0) {
        $prozesse = @(Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" -ErrorAction SilentlyContinue)
        foreach ($prozess in $prozesse) {
            $treffer = [regex]::Match($prozess.CommandLine, '(?:^|\s)(?:"--user-data-dir=([^"]+)"|--user-data-dir="([^"]+)"|--user-data-dir=([^\s"]+))')
            if (-not $treffer.Success) { continue }
            $wert = @($treffer.Groups | Select-Object -Skip 1 | Where-Object Success | Select-Object -First 1).Value
            try { $voll = [System.IO.Path]::GetFullPath($wert).TrimEnd('\') } catch { continue }
            if ($profile -contains $voll) {
                if ($prozess.ParentProcessId) {
                    $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($prozess.ParentProcessId)" -ErrorAction SilentlyContinue
                    if ($parent -and $parent.Name -eq 'chromedriver.exe') {
                        Stop-Process -Id $parent.ProcessId -Force -ErrorAction SilentlyContinue
                        $count++
                    }
                }
                Stop-Process -Id $prozess.ProcessId -Force -ErrorAction SilentlyContinue
                $count++
            }
        }
    }
}

# 2. Verwaiste chromedriver.exe aufräumen:
# - wenn -AllDrivers angegeben ist: sofort alle beenden
# - andernfalls: wenn der Parent tot ist ODER kein chrome.exe-Kind mehr existiert
if ($AllDrivers) {
    $drivers = @(Get-Process -Name 'chromedriver' -ErrorAction SilentlyContinue)
    $count += $drivers.Count
    $drivers | Stop-Process -Force -ErrorAction SilentlyContinue
} else {
    $drivers = @(Get-CimInstance Win32_Process -Filter "Name = 'chromedriver.exe'" -ErrorAction SilentlyContinue)
    if ($drivers.Count -gt 0) {
        $allChrome = @(Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" -ErrorAction SilentlyContinue)
        $chromeParents = @($allChrome | Select-Object -ExpandProperty ParentProcessId -Unique)
        foreach ($driver in $drivers) {
            $parentAlive = [bool](Get-Process -Id $driver.ParentProcessId -ErrorAction SilentlyContinue)
            $hasChromeChild = $chromeParents -contains $driver.ProcessId
            if (-not $parentAlive -or -not $hasChromeChild) {
                Stop-Process -Id $driver.ProcessId -Force -ErrorAction SilentlyContinue
                $count++
            }
        }
    }
}

# 3. Verwaiste Profilverzeichnisse entfernen
if (Test-Path -LiteralPath $basis) {
    Get-ChildItem -LiteralPath $basis -Directory -ErrorAction SilentlyContinue | Where-Object {
        Test-Path -LiteralPath (Join-Path $_.FullName '.testsweet-profile')
    } | ForEach-Object {
        try {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
        } catch {}
    }
}

Write-Output $count

