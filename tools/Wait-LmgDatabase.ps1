param([ValidateRange(1,300)][int]$TimeoutSeconds = 300)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LmgOperating.ps1')

try {
    $url = [Environment]::GetEnvironmentVariable('TX_URL')
    $mode = Get-LmgDatabaseMode $url
    if ($mode -eq 'DOCKER') {
        $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
        if (-not $docker) { Write-Output '[Hinweis] Docker nicht gefunden - Datenbank muss extern laufen.'; exit 0 }
        $result = @(Wait-LmgDockerDatabase $docker.Source $env:TX_USER $env:TX_PASS -TimeoutMilliseconds ($TimeoutSeconds * 1000))
        $result | Where-Object { $_ -is [string] } | Write-Output
        if ($result[-1] -eq $true) { exit 0 }
        exit 1
    }
    if ($mode -eq 'VM') {
        $client = New-Object Net.Sockets.TcpClient
        try {
            $connected = $client.ConnectAsync('127.0.0.1',1921)
            if ($connected.Wait(3000) -and $client.Connected) { Write-Output 'Oracle 19c Datenbank (VirtualBox, Port 1921) ist erreichbar.' }
            else { Write-Output '[Warnung] VirtualBox Oracle-DB auf Port 1921 antwortet nicht. VM bitte selbst starten.' }
        } finally { $client.Dispose() }
    }
    exit 0
} catch {
    Write-Output ('[Warnung] DB-Bereitschaft konnte nicht geprueft werden (' + $_.Exception.GetType().Name + '). Konfiguration pruefen; Start wird fortgesetzt.')
    exit 1
}
