param([string]$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')))
$ErrorActionPreference = 'Stop'
. (Join-Path $ProjectRoot 'tools/LmgOperating.ps1')
$script:checks = 0
function Assert-Equal($Actual, $Expected, [string]$Name) {
    if ($Actual -cne $Expected) { throw ('Regression: ' + $Name) }
    $script:checks++
}
function Assert-Rejected([scriptblock]$Action, [string]$Name) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-Equal $rejected $true $Name
}
function New-Command([string[]]$Values) {
    return (@($Values | ForEach-Object { ConvertTo-LmgProcessArgument $_ }) -join ' ')
}
function Test-Owner([string[]]$Values) {
    Test-LmgOwner ([pscustomobject]@{Name='java.exe';CommandLine=(New-Command $Values)}) $ProjectRoot
}
$root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\')
$main = '-Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain'
$withoutFile = @('java', ('-Dmaven.multiModuleProjectDirectory=' + $root), 'org.codehaus.plexus.classworlds.launcher.Launcher', 'exec:java')
$launcher = $withoutFile + @('-f',(Join-Path $root 'pom.xml'))
Assert-Equal (Test-Owner ($launcher + $main)) $true 'expliziter Maven-LMG-Start'
Assert-Equal (Test-Owner ($withoutFile + $main)) $false 'Modulverzeichnis allein beweist kein POM'
Assert-Equal (Test-Owner $launcher) $false 'POM-Default ist kein Hauptklassenbeleg'
Assert-Equal (Test-Owner ($launcher + '-Dexec.mainClass=example.OtherServer')) $false 'vollstaendig gequotete Fremdklasse'
Assert-Equal (Test-LmgOwner ([pscustomobject]@{Name='java.exe';CommandLine=('java -Dmaven.multiModuleProjectDirectory="' + $root + '" org.codehaus.plexus.classworlds.launcher.Launcher exec:java -Dexec.mainClass=example.OtherServer')}) $root) $false 'ungequotete Fremdklasse'
Assert-Equal (Test-Owner ($launcher + $main + $main)) $false 'doppelte Klassenparameter sind mehrdeutig'
Assert-Equal (Test-Owner ($launcher + $main + @('-f',(Join-Path $root 'pom.xml')))) $false 'mehrdeutige POM-Parameter'
Assert-Equal (Test-Owner ($withoutFile + $main + @('-f',(Join-Path $root 'testsweet/pom.xml')))) $false 'fremdes -f-POM'
Assert-Equal (Test-Owner ($withoutFile + $main + ('--file=' + (Join-Path $root 'testsweet/pom.xml')))) $false 'fremdes --file-POM'
Assert-Equal (Test-Owner ($withoutFile + $main + @('-f','pom.xml'))) $false 'nicht beweisbarer relativer POM-Pfad'
Assert-Equal (Test-Owner ($launcher + $main + @('-pl','other'))) $false 'fremde Projektauswahl'
$otherRoot = @('java', ('-Dmaven.multiModuleProjectDirectory=' + $root + '-other'), 'org.codehaus.plexus.classworlds.launcher.Launcher', 'exec:java', $main)
Assert-Equal (Test-Owner $otherRoot) $false 'aehnlicher Rootpfad'
Assert-Equal (Test-Owner @('java','-cp',(Join-Path $root 'target/classes'),'de.allgemein.lmg.web.LmgServerMain')) $true 'direkter Projektstart'
Assert-Equal (Test-Owner @('java','-Dmaven.multiModuleProjectDirectory=C:/other','-cp',(Join-Path $root 'target/classes'),'de.allgemein.lmg.web.LmgServerMain')) $true 'direkter Start unabhaengig von Maven-Property'
Assert-Equal (Test-Owner @('java','-cp',('C:/other/classes;' + (Join-Path $root 'target/classes')),'de.allgemein.lmg.web.LmgServerMain')) $false 'fremder Klassenpfad vor Projekt'
Assert-Equal (Test-Owner @('java','-Dnote=de.allgemein.lmg.web.LmgServerMain','-cp',(Join-Path $root 'target/classes'),'example.OtherServer')) $false 'Hauptklasse nur im Propertytext'
Assert-Equal (Test-LmgOwner ([pscustomobject]@{Name='node.exe';CommandLine=(New-Command ($launcher + $main))}) $root) $false 'Nicht-Java'

$values = @('argument mit Leerzeichen','a"b','', 'C:\Pfad mit Leerzeichen\', 'a\"b', 'p$ss&word')
$decoded = @(Split-LmgCommandLine (New-Command (@('probe.exe') + $values)))
Assert-Equal $decoded.Count ($values.Count + 1) 'vollstaendige Windows-Argumente'
for ($i=0; $i -lt $values.Count; $i++) { Assert-Equal $decoded[$i+1] $values[$i] ('Windows-Quote-Roundtrip ' + $i) }

$dummy = 'p$ ss&word''/@'
$sql = New-LmgSqlPlusInput 'review' $dummy
Assert-Equal ($sql.Contains(('connect review/"' + $dummy + '"@//localhost:1521/FREEPDB1'))) $true 'Sonderzeichen unveraendert im Passwort'
Assert-Equal ($sql.IndexOf('set define off') -lt $sql.IndexOf('connect review/')) $true 'SQL-Substitution vor Login ausgeschaltet'
Assert-Rejected { New-LmgSqlPlusInput 'review' 'bad"quote' } 'doppeltes Quote gezielt abgewiesen'
Assert-Rejected { New-LmgSqlPlusInput 'review' "bad`nselect 1;" } 'Passwort-Zeilenumbruch abgewiesen'
Assert-Rejected { New-LmgSqlPlusInput 'review/other' 'pass' } 'Benutzername ohne SQL-Injection'
Assert-Equal (Get-LmgDatabaseMode 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1') 'DOCKER' 'Docker-Ziel'
Assert-Equal (Get-LmgDatabaseMode 'jdbc:oracle:thin:@//127.0.0.1:1921/o19c1p1') 'VM' 'VM-Ziel'
Assert-Equal (Get-LmgDatabaseMode 'jdbc:oracle:thin:@//db.example:1921/o19c1p1') 'EXTERNAL' 'externe DB unveraendert'
Assert-Equal (Get-LmgDatabaseMode 'jdbc:oracle:thin:@//notlocalhost:1521/FREEPDB1') 'EXTERNAL' 'aehnlicher Host startet keinen lokalen Container'
$batch = Get-Content -LiteralPath (Join-Path $ProjectRoot 'start.bat') -Raw
Assert-Equal ($batch.Contains('%TX_PASS%')) $false 'Batch interpoliert kein Passwort in Kommandozeilen'
Assert-Equal ($batch.Contains('Wait-LmgDatabase.ps1')) $true 'Startpfad verwendet getesteten Bereitschaftshelfer'
Assert-Equal ($batch.Contains('setlocal DisableDelayedExpansion')) $true 'Ausrufezeichen werden nicht von cmd expandiert'
Assert-Equal ($batch.Contains('call mvn -f "%~dp0pom.xml" exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain')) $true 'Standardstarter belegt absolutes POM'

$state = [pscustomobject]@{Elapsed=0L;SqlAttempts=0;Calls=(New-Object 'Collections.Generic.List[object]')}
$runner = {
    param($file,$argv,$stdin,$limit)
    $state.Calls.Add([pscustomobject]@{Arguments=$argv;InputText=$stdin;Limit=$limit})
    $state.Elapsed += 100
    if ($argv[0] -eq 'inspect') { return [pscustomobject]@{ExitCode=0;Output='true';TimedOut=$false} }
    if ($argv[0] -eq 'exec') {
        $state.SqlAttempts++
        return [pscustomobject]@{ExitCode=([int]($state.SqlAttempts -lt 2));Output='';TimedOut=$false}
    }
    return [pscustomobject]@{ExitCode=0;Output='';TimedOut=$false}
}.GetNewClosure()
$elapsed = { $state.Elapsed }.GetNewClosure()
$delay = { param($milliseconds) $state.Elapsed += $milliseconds }.GetNewClosure()
$result = @(Wait-LmgDockerDatabase 'mock-docker' 'review' $dummy -TimeoutMilliseconds 5000 -AttemptMilliseconds 2000 -RetryMilliseconds 200 -Runner $runner -Elapsed $elapsed -Delay $delay)
Assert-Equal $result[-1] $true 'erfolgreiche Wiederholung'
$sqlCalls = @($state.Calls | Where-Object { $_.Arguments[0] -eq 'exec' })
Assert-Equal $sqlCalls.Count 2 'zwei Loginversuche'
Assert-Equal $sqlCalls[0].InputText $sql 'Passwort geht ausschliesslich ueber stdin'
Assert-Equal (($sqlCalls[0].Arguments -join ' ').Contains($dummy)) $false 'keine Zugangsdaten in Prozessargumenten'
Assert-Equal ($sqlCalls[0].Arguments -contains 'bash') $false 'keine Bash-Quelltextausfuehrung'
Assert-Equal ($sqlCalls[0].Arguments -contains '/nolog') $true 'SQLPlus ohne argv-Passwort'
Assert-Equal ($sqlCalls[0].Arguments -contains 'timeout') $true 'auch Containerprozess begrenzt'

$quick = [pscustomobject]@{Elapsed=0L;RemoteLimit=''}
$small = {
    param($file,$argv,$stdin,$limit)
    if ($argv[0] -eq 'inspect') { return [pscustomobject]@{ExitCode=0;Output='true';TimedOut=$false} }
    if ($argv[0] -eq 'exec') { $quick.RemoteLimit=$argv[5] }
    return [pscustomobject]@{ExitCode=0;Output='';TimedOut=$false}
}.GetNewClosure()
$result = @(Wait-LmgDockerDatabase 'mock-docker' 'review' 'dummy' -TimeoutMilliseconds 2000 -AttemptMilliseconds 500 -Runner $small -Elapsed { $quick.Elapsed }.GetNewClosure())
Assert-Equal $result[-1] $true 'kurzer Einzelversuch'
Assert-Equal $quick.RemoteLimit '0.5s' 'Containerlimit wird nicht auf volle Sekunde verlaengert'

$clock = [pscustomobject]@{Elapsed=0L;Calls=0}
$slow = {
    param($file,$argv,$stdin,$limit)
    $clock.Calls++; $clock.Elapsed += $limit
    if ($argv[0] -eq 'inspect') { return [pscustomobject]@{ExitCode=0;Output='true';TimedOut=$false} }
    if ($argv[0] -eq 'exec') { return [pscustomobject]@{ExitCode=-1;Output='';TimedOut=$true} }
    return [pscustomobject]@{ExitCode=0;Output='';TimedOut=$false}
}.GetNewClosure()
$result = @(Wait-LmgDockerDatabase 'mock-docker' 'review' 'dummy' -TimeoutMilliseconds 3500 -AttemptMilliseconds 1000 -RetryMilliseconds 500 -Runner $slow -Elapsed { $clock.Elapsed }.GetNewClosure() -Delay { param($ms) $clock.Elapsed += $ms }.GetNewClosure())
Assert-Equal $result[-1] $false 'Gesamtdeadline statt Schlafzaehler'
Assert-Equal $clock.Elapsed 3500L 'Zeit der Einzelaufrufe in Frist enthalten'
Assert-Equal $clock.Calls 3 'kein weiterer Aufruf nach Frist'

$missing = {
    param($file,$argv,$stdin,$limit)
    if ($argv[0] -eq 'inspect') { return [pscustomobject]@{ExitCode=0;Output='true';TimedOut=$false} }
    return [pscustomobject]@{ExitCode= $(if($argv[0] -eq 'exec'){127}else{0});Output='';TimedOut=$false}
}
$result = @(Wait-LmgDockerDatabase 'mock-docker' 'review' 'dummy' -Runner $missing)
Assert-Equal $result[-1] $false 'fehlendes SQLPlus/timeout beendet Probe'
$powershell = Join-Path $PSHOME 'powershell.exe'
$timer = [Diagnostics.Stopwatch]::StartNew()
$hung = Invoke-LmgBoundedProcess $powershell @('-NoProfile','-Command','Start-Sleep -Seconds 5') '' 200
Assert-Equal $hung.TimedOut $true 'eigener haengender Testprozess begrenzt'
Assert-Equal ($timer.ElapsedMilliseconds -lt 2500) $true 'keine versteckte Fuenfsekundenwartezeit'
$timer.Restart()
$blocked = Invoke-LmgBoundedProcess $powershell @('-NoProfile','-Command','Start-Sleep -Seconds 5') ('p' * 1048576) 200
Assert-Equal $blocked.TimedOut $true 'nie lesendes Kind blockiert stdin nicht'
Assert-Equal ($timer.ElapsedMilliseconds -lt 2500) $true 'stdin-WriteAsync hat dieselbe Deadline'
$unicode = New-LmgSqlPlusInput 'review' ([string][char]0x20AC * 1024)
$timer.Restart()
$blocked = Invoke-LmgBoundedProcess $powershell @('-NoProfile','-Command','Start-Sleep -Seconds 5') $unicode 200
Assert-Equal $blocked.TimedOut $true 'maximales Unicodepasswort bei nie lesendem Kind begrenzt'
Assert-Equal ($timer.ElapsedMilliseconds -lt 2500) $true 'Unicode-Deadline'
$unavailable = Invoke-LmgBoundedProcess (Join-Path $ProjectRoot 'does-not-exist-review.exe') @() '' 1000
Assert-Equal $unavailable.Unavailable $true 'fehlendes Tool ohne ungeschuetzten finally-Fehler'
$copy = Invoke-LmgBoundedProcess $powershell @('-NoProfile','-Command','[Console]::Out.Write([Console]::In.ReadToEnd())') $sql 5000
Assert-Equal $copy.ExitCode 0 'stdin-Transportprobe beendet'
Assert-Equal $copy.Output $sql 'echte Prozessuebergabe bewahrt alle Sonderzeichen'
$copy = Invoke-LmgBoundedProcess $powershell @('-NoProfile','-Command','[Console]::InputEncoding=[Text.Encoding]::UTF8; [Console]::Out.Write([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd())))') $unicode 5000
Assert-Equal $copy.ExitCode 0 'UTF8-Transportprobe beendet'
Assert-Equal $copy.Output ([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($unicode))) 'maximales Unicodepasswort wird unveraendert als UTF8 uebertragen'
Write-Output ('PASS: ' + $script:checks + ' Betriebsregressionen; ausschliesslich fiktive Prozesszuordnung, gemocktes Docker und eigene kurzlebige Hilfsprozesse.')
