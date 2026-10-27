param([int]$Port = 0, [switch]$Db)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools/LmgOperating.ps1')

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
            $laeuft = docker ps --format '{{.Names}}' | Where-Object { $_ -ceq 'lmg-oracle' }
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
