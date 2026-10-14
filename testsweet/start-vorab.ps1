# Vorabpruefung fuer start.bat: Laeuft auf dem SweetServer-Port bereits ein
# verifizierter testsweet-Prozess, fragt ein Dialogfenster, ob er beendet
# werden darf. Ein Fremdprozess auf dem Port bricht den Start ab — fremde
# Prozesse werden nie beendet (gleiche Erkennung wie stop.ps1).
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

$Port = 0
if ([string]::IsNullOrWhiteSpace($env:SWEET_PORT)) { $Port = 8095 }
elseif (-not [int]::TryParse($env:SWEET_PORT, [ref]$Port)) { throw 'SWEET_PORT muss eine Ganzzahl sein' }
if ($Port -lt 1 -or $Port -gt 65535) { throw 'SWEET_PORT muss zwischen 1 und 65535 liegen' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
if ($listeners.Count -eq 0) { Write-Output "Port $Port ist frei — Start geht weiter."; exit 0 }

$sweet = @()
$fremd = @()
foreach ($ownerId in $listeners) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerId"
    if ($owner -and (Test-SweetOwner $owner $projectRoot)) { $sweet += $ownerId } else { $fremd += $ownerId }
}

Add-Type -AssemblyName System.Windows.Forms
# Unsichtbares TopMost-Fenster als Besitzer, damit der Dialog zuverlässig vorn liegt.
$form = New-Object System.Windows.Forms.Form
$form.TopMost = $true

if ($fremd.Count -gt 0) {
    [void][System.Windows.Forms.MessageBox]::Show($form,
        "Port $Port wird von einem anderen Programm belegt (PID $($fremd -join ', ')).`n`nDer testsweet-Start wird abgebrochen. Bitte das Fremdprogramm selbst beenden oder mit SWEET_PORT einen anderen Port setzen.",
        'testsweet — Start nicht möglich', 'OK', 'Error', 'Button1', 0, 'DefaultDesktopOnly')
    Write-Error "Port $Port gehoert keinem verifizierten testsweet-Prozess — Start abgebrochen."
    exit 1
}

$phase = ''
try {
    $status = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/status" -TimeoutSec 2
    $phase = [string]$status.phase
} catch { }

$laufHinweis = if ($phase -and $phase -ne 'BEREIT') {
    "`nACHTUNG: Es laeuft gerade ein Testlauf bzw. eine Pruefung (Phase: $phase).`nEin Beenden bricht ihn ab; der Lauf liefert dann keinen Nachweis."
} else {
    "`nDer Server ist im Leerlauf (kein Testlauf aktiv)."
}
$starts = (($sweet | ForEach-Object { (Get-CimInstance Win32_Process -Filter "ProcessId = $_").CreationDate.ToString('HH:mm:ss') }) -join ', ')
$antwort = [System.Windows.Forms.MessageBox]::Show($form,
    "Es laeuft bereits ein testsweet-Simulator auf Port $Port.`nPID: $($sweet -join ', ') · gestartet um $starts.$laufHinweis`n`nSoll er beendet werden, damit der neue Start erfolgen kann?",
    'testsweet — Server läuft bereits', 'YesNo', 'Warning', 'Button2', 0, 'DefaultDesktopOnly')
if ($antwort -ne 'Yes') {
    Write-Output "Bestehender Server bleibt laufen — Start abgebrochen."
    exit 1
}

& (Join-Path $PSScriptRoot 'stop.ps1')
if ($LASTEXITCODE -ne 0) { exit 1 }
$frist = [DateTime]::Now.AddSeconds(15)
while ([DateTime]::Now -lt $frist) {
    $rest = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($rest.Count -eq 0) { Write-Output "Port $Port ist wieder frei — Start geht weiter."; exit 0 }
    Start-Sleep -Milliseconds 500
}
Write-Error "Port $Port wurde nach dem Beenden nicht frei."
exit 1
