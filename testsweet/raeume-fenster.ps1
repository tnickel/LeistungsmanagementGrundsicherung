# Räumt verwaiste Selenium-Fenster auf — aber NUR die der eigenen Java-Prozesskette:
# Die eigene PID-Kette wird über Win32_Process (ParentProcessId) bis zur Wurzel
# verfolgt, dann werden rekursiv alle Kind-Prozesse dieser Kette gesammelt und
# daraus ausschließlich chromedriver.exe und deren Automation-Chrome-Fenster
# (Erkennung über Selenium-Parameter) beendet. Fremde Chrome-Fenster anderer
# Nutzer und Prozesse bleiben unberührt. Der Skript-Body ist identisch zum
# eingebetteten Skript in WebMitarbeiter.raeumeVerwaisteFensterAuf().
$prozesse = @()
try { $prozesse = Get-CimInstance Win32_Process -ErrorAction Stop } catch {}
if (@($prozesse).Count -eq 0) {
    Write-Output 'Prozessliste nicht ermittelbar — es wurde nichts beendet'
    exit 0
}
# Eigene PID-Kette nach oben verfolgen (Abbruch bei Zyklus oder Wurzel)
$kette = New-Object System.Collections.Generic.HashSet[uint32]
$aktuell = [uint32]$PID
while ($aktuell -and $aktuell -ne 0 -and -not $kette.Contains($aktuell)) {
    [void]$kette.Add($aktuell)
    $eintrag = $prozesse | Where-Object { $_.ProcessId -eq $aktuell } | Select-Object -First 1
    if ($null -eq $eintrag) { break }
    $aktuell = [uint32]$eintrag.ParentProcessId
}
# Alle Kind-Prozesse der Kette rekursiv sammeln (chromedriver, chrome darunter)
$eigene = New-Object System.Collections.Generic.HashSet[uint32]
$warteschlange = [System.Collections.Generic.Queue[uint32]]::new()
foreach ($id in $kette) { $warteschlange.Enqueue($id) }
while ($warteschlange.Count -gt 0) {
    $id = $warteschlange.Dequeue()
    foreach ($p in ($prozesse | Where-Object { $_.ParentProcessId -eq $id })) {
        if (-not $eigene.Contains([uint32]$p.ProcessId)) {
            [void]$eigene.Add([uint32]$p.ProcessId)
            $warteschlange.Enqueue([uint32]$p.ProcessId)
        }
    }
}
# Nur eigene chromedriver-/Automation-Chrome-Prozesse beenden
$ziele = $prozesse | Where-Object {
    ($_.ProcessId -ne $PID) -and $eigene.Contains([uint32]$_.ProcessId) -and (
        $_.Name -eq 'chromedriver.exe' -or
        ($_.Name -eq 'chrome.exe' -and ($_.CommandLine -like '*--remote-debugging-pipe*' -or $_.CommandLine -like '*--test-type*')))
}
if (@($ziele).Count -eq 0) {
    Write-Output 'Keine verwaisten Fenster der eigenen Prozesskette gefunden'
} else {
    foreach ($z in $ziele) { Stop-Process -Id $z.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Output ('Aufgeraeumt: ' + @($ziele).Count + ' Prozess(e) der eigenen Kette beendet')
}
