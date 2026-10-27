param([int]$Port = 0, [switch]$Db)
$ErrorActionPreference = 'Stop'

function Test-LmgOwner($Process, [string]$ProjectRoot) {
    if ($Process.Name -notin @('java.exe','javaw.exe')) { return $false }
    $command = [string]$Process.CommandLine
    $root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\').Replace('\','/')
    $normalized = $command.Replace('\','/')
    if ($normalized -notmatch ([regex]::Escape($root) + '(?:/|[";\s]|$)')) { return $false }
    if ($command -match '(?:^|\s|=)de\.allgemein\.lmg\.web\.LmgServerMain(?:\s|$)') { return $true }
    # Maven exec:java laeuft in der Maven-JVM; nur das bekannte Projekt-POM mit
    # LmgServerMain-Default zulassen, fremde exec.mainClass-Overrides ablehnen.
    if ($command -match '(?:^|\s)-Dexec\.mainClass=(?!de\.allgemein\.lmg\.web\.LmgServerMain(?:\s|$))') { return $false }
    $launcher = $command -match 'org\.codehaus\.plexus\.classworlds\.launcher\.Launcher'
    $goal = $command -match '(?:^|\s)exec:java(?:\s|$)'
    $module = [regex]::Match($normalized, '(?:^|\s)"?-Dmaven\.multiModuleProjectDirectory=(?:"([^"]+)"|([^"\s]+))"?(?:\s|$)')
    if (-not $launcher -or -not $goal -or -not $module.Success) { return $false }
    $directory = if ($module.Groups[1].Success) { $module.Groups[1].Value } else { $module.Groups[2].Value }
    return $directory.TrimEnd('/') -ieq $root
}

if ($Port -eq 0) {
    if ([string]::IsNullOrWhiteSpace($env:LMG_PORT)) { $Port = 8080 }
    elseif (-not [int]::TryParse($env:LMG_PORT, [ref]$Port)) { throw 'LMG_PORT muss eine Ganzzahl sein' }
}
if ($Port -lt 1 -or $Port -gt 65535) { throw 'LMG_PORT muss zwischen 1 und 65535 liegen' }
$projectRoot = $PSScriptRoot
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
if ($listeners.Count -eq 0) {
    Write-Output "LMG-Port $Port ist frei - Server laeuft nicht."
}
else {
    $identities = @{}
    foreach ($ownerId in $listeners) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
        if (-not $owner -or -not (Test-LmgOwner $owner $projectRoot)) {
            Write-Error "Port $Port gehoert keinem verifizierten LMG-Server-Prozess dieses Projekts (PID $ownerId). Kein Prozess beendet."
            exit 1
        }
        $identities[$ownerId] = $owner.CreationDate
    }
    foreach ($ownerId in $listeners) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
        if (-not $owner -or $owner.CreationDate -ne $identities[$ownerId] -or -not (Test-LmgOwner $owner $projectRoot)) { throw 'Prozessidentitaet hat sich geaendert; Abbruch ohne Kill' }
        & taskkill.exe /PID $ownerId /T /F | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "LMG-Server-Prozess $ownerId konnte nicht beendet werden" }
    }
    Write-Output "Verifizierter LMG-Server-Prozess auf Port $Port beendet."
}

if ($Db) {
    where.exe docker > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Output '[Hinweis] Docker nicht gefunden - Datenbank-Container wurde nicht gestoppt.'
    }
    else {
        docker inspect lmg-oracle *> $null
        if ($LASTEXITCODE -ne 0) {
            Write-Output '[Hinweis] Kein Container "lmg-oracle" vorhanden.'
        }
        else {
            $laeuft = docker ps --format '{{.Names}}' | Select-String -SimpleMatch 'lmg-oracle'
            if (-not $laeuft) {
                Write-Output 'DB-Container lmg-oracle laeuft bereits nicht.'
            }
            else {
                docker stop lmg-oracle *> $null
                if ($LASTEXITCODE -eq 0) { Write-Output 'DB-Container lmg-oracle gestoppt.' }
                else { Write-Error 'DB-Container lmg-oracle konnte nicht gestoppt werden.' }
            }
        }
    }
}
