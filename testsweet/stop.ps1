param([int]$Port = 0, [switch]$LibraryOnly)
$ErrorActionPreference = 'Stop'

function Test-SweetOwner($Process, [string]$ProjectRoot) {
    if ($Process.Name -notin @('java.exe','javaw.exe')) { return $false }
    $command = [string]$Process.CommandLine
    $root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\').Replace('\','/')
    $normalized = $command.Replace('\','/')
    if ($normalized -notmatch ([regex]::Escape($root) + '(?:/|[";\s]|$)')) { return $false }
    if ($command -match '(?:^|\s)de\.allgemein\.testsweet\.SweetMain(?:\s|$)') { return $true }
    # Maven exec:java läuft im Maven-JVM; nur das bekannte Projekt-POM mit
    # SweetMain-Default zulassen, fremde exec.mainClass-Overrides ablehnen.
    if ($command -match '(?:^|\s)-Dexec\.mainClass=(?!de\.allgemein\.testsweet\.SweetMain(?:\s|$))') { return $false }
    $launcher = $command -match 'org\.codehaus\.plexus\.classworlds\.launcher\.Launcher'
    $goal = $command -match '(?:^|\s)exec:java(?:\s|$)'
    $pom = $normalized -match '(?:^|\s)-f\s+"?(?:testsweet/pom\.xml|[^"\r\n]*?/testsweet/pom\.xml)"?(?:\s|$)'
    $module = [regex]::Match($normalized, '(?:^|\s)"?-Dmaven\.multiModuleProjectDirectory=(?:"([^"]+)"|([^"\s]+))"?(?:\s|$)')
    if (-not $launcher -or -not $goal -or -not $pom -or -not $module.Success) { return $false }
    $directory = if ($module.Groups[1].Success) { $module.Groups[1].Value } else { $module.Groups[2].Value }
    return $directory.TrimEnd('/') -ieq $root
}

if ($LibraryOnly) { return }
if ($Port -eq 0) {
    if ([string]::IsNullOrWhiteSpace($env:SWEET_PORT)) { $Port = 8095 }
    elseif (-not [int]::TryParse($env:SWEET_PORT, [ref]$Port)) { throw 'SWEET_PORT muss eine Ganzzahl sein' }
}
if ($Port -lt 1 -or $Port -gt 65535) { throw 'SWEET_PORT muss zwischen 1 und 65535 liegen' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
if ($listeners.Count -eq 0) { Write-Output "testsweet-Port $Port ist frei."; exit 0 }
$identities = @{}
foreach ($ownerId in $listeners) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
    if (-not $owner -or -not (Test-SweetOwner $owner $projectRoot)) {
        Write-Error "Port $Port gehört keinem verifizierten testsweet-Prozess dieses Projekts (PID $ownerId). Kein Prozess beendet."
        exit 1
    }
    $identities[$ownerId] = $owner.CreationDate
}
foreach ($ownerId in $listeners) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
    if (-not $owner -or $owner.CreationDate -ne $identities[$ownerId] -or -not (Test-SweetOwner $owner $projectRoot)) { throw 'Prozessidentität hat sich geändert; Abbruch ohne Kill' }
    & taskkill.exe /PID $ownerId /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "testsweet-Prozess $ownerId konnte nicht beendet werden" }
}
Write-Output "Verifizierte testsweet-Prozesskette auf Port $Port beendet."
