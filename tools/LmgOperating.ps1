# Gemeinsame, ohne Start-/Stopp-Seiteneffekte ladbare Betriebshilfen.
# CommandLineToArgvW: https://learn.microsoft.com/windows/win32/api/shellapi/nf-shellapi-commandlinetoargvw
if (-not ('LmgOperating.NativeCommandLine' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace LmgOperating {
    public static class NativeCommandLine {
        [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        public static extern IntPtr CommandLineToArgvW(string commandLine, out int count);
        [DllImport("kernel32.dll")]
        public static extern IntPtr LocalFree(IntPtr memory);
    }
}
'@
}

function Split-LmgCommandLine([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return @() }
    $count = 0
    $memory = [LmgOperating.NativeCommandLine]::CommandLineToArgvW($CommandLine, [ref]$count)
    if ($memory -eq [IntPtr]::Zero) { throw 'Windows-Befehlszeile konnte nicht gelesen werden' }
    try {
        for ($index = 0; $index -lt $count; $index++) {
            $pointer = [Runtime.InteropServices.Marshal]::ReadIntPtr($memory, $index * [IntPtr]::Size)
            [Runtime.InteropServices.Marshal]::PtrToStringUni($pointer)
        }
    } finally { [void][LmgOperating.NativeCommandLine]::LocalFree($memory) }
}

function ConvertTo-LmgProcessArgument([string]$Value) {
    # Windows-CRT-Quoting; keine Shell wird zur Ausfuehrung verwendet.
    $escaped = [regex]::Replace($Value, '(\\*)"', { param($match) $match.Groups[1].Value + $match.Groups[1].Value + '\"' })
    $escaped = [regex]::Replace($escaped, '(\\+)$', { param($match) $match.Value + $match.Value })
    return '"' + $escaped + '"'
}

function Get-LmgCanonicalPath([string]$Path) {
    if (-not [IO.Path]::IsPathRooted($Path)) { return $null }
    try { return [IO.Path]::GetFullPath($Path).TrimEnd('\', '/') } catch { return $null }
}

function Test-LmgOwner($Process, [string]$ProjectRoot) {
    if ($Process.Name -notin @('java.exe', 'javaw.exe')) { return $false }
    $root = Get-LmgCanonicalPath $ProjectRoot
    if (-not $root) { return $false }
    try { $arguments = @(Split-LmgCommandLine ([string]$Process.CommandLine)) } catch { return $false }
    if ($arguments.Count -lt 2) { return $false }
    $mainIndex = -1
    $classPath = $null
    for ($index = 1; $index -lt $arguments.Count; $index++) {
        $argument = $arguments[$index]
        if ($argument -in @('-cp', '-classpath', '--class-path')) {
            $index++
            if ($index -ge $arguments.Count -or $null -ne $classPath) { return $false }
            $classPath = $arguments[$index]
        } elseif ($argument.StartsWith('--class-path=')) {
            if ($null -ne $classPath) { return $false }
            $classPath = $argument.Substring('--class-path='.Length)
        } elseif ($argument.StartsWith('-D') -or $argument.StartsWith('-X') -or $argument -in @('-ea','-da','-esa','-dsa','-server','-client')) {
            continue
        } elseif ($argument.StartsWith('-')) {
            # Unbekannte Java-Startformen (z.B. -jar/-m) sind kein Klassenbeleg.
            return $false
        } else { $mainIndex = $index; break }
    }
    if ($mainIndex -lt 0) { return $false }
    $mainClass = $arguments[$mainIndex]
    $expectedClass = 'de.allgemein.lmg.web.LmgServerMain'
    if ($mainClass -ceq $expectedClass) {
        # Ohne Prozess-Arbeitsverzeichnis sind relative Klassenpfade nicht beweisbar.
        # Das erste Artefakt muss den Projektcode enthalten; spaetere Treiber-JARs
        # koennen die bereits daraus geladene Hauptklasse nicht ueberschatten.
        if (-not $classPath) { return $false }
        $first = Get-LmgCanonicalPath (($classPath -split ';')[0])
        $classes = Get-LmgCanonicalPath (Join-Path $root 'target/classes')
        $target = (Join-Path $root 'target') + [IO.Path]::DirectorySeparatorChar
        return $first -and ($first -ieq $classes -or
            ($first.StartsWith($target, [StringComparison]::OrdinalIgnoreCase) -and
             [IO.Path]::GetFileName($first) -match '^transaktionssteuerung-[^\\/]+\.jar$'))
    }
    if ($mainClass -cne 'org.codehaus.plexus.classworlds.launcher.Launcher') { return $false }
    $mainOverrides = @($arguments | Where-Object { $_.StartsWith('-Dexec.mainClass=') })
    $modules = @($arguments | Where-Object { $_.StartsWith('-Dmaven.multiModuleProjectDirectory=') })
    if ($mainOverrides.Count -ne 1 -or $mainOverrides[0] -cne ('-Dexec.mainClass=' + $expectedClass)) { return $false }
    if ($modules.Count -ne 1 -or (Get-LmgCanonicalPath $modules[0].Substring('-Dmaven.multiModuleProjectDirectory='.Length)) -ine $root) { return $false }
    if ($mainIndex + 1 -ge $arguments.Count) { return $false }
    $mavenArguments = @($arguments[($mainIndex + 1)..($arguments.Count - 1)])
    if ($mavenArguments -cnotcontains 'exec:java') { return $false }
    $pom = Join-Path $root 'pom.xml'
    $fileOptions = 0
    for ($index = 0; $index -lt $mavenArguments.Count; $index++) {
        $argument = $mavenArguments[$index]
        $file = $null
        if ($argument -in @('-f', '--file')) {
            $index++
            if ($index -ge $mavenArguments.Count) { return $false }
            $file = $mavenArguments[$index]
        } elseif ($argument.StartsWith('--file=')) { $file = $argument.Substring('--file='.Length) }
        elseif ($argument.StartsWith('-f') -and -not $argument.StartsWith('-fail-')) { $file = $argument.Substring(2) }
        elseif ($argument -in @('-pl','--projects','-am','--also-make','-amd','--also-make-dependents') -or $argument.StartsWith('--projects=') -or $argument.StartsWith('-pl')) { return $false }
        if ($null -ne $file) {
            $fileOptions++
            if ($fileOptions -gt 1 -or (Get-LmgCanonicalPath $file) -ine $pom) { return $false }
        }
    }
    # multiModuleProjectDirectory ist allein kein Beleg fuer das ausgewaehlte
    # POM. Der Standardstarter uebergibt deshalb dessen absoluten Pfad explizit.
    return $fileOptions -eq 1
}

function New-LmgSqlPlusInput([string]$UserName, [string]$Password) {
    if ($UserName -notmatch '^[A-Za-z][A-Za-z0-9_$#]{0,127}$') { throw 'TX_USER muss ein einfacher Oracle-Benutzername sein' }
    # Oracle-/SQL*Plus-Passwoerter mit doppeltem Quote sind nicht portabel.
    # Niemals einen solchen Wert umdeuten oder in ein weiteres SQL-Kommando lassen.
    if ([string]::IsNullOrEmpty($Password) -or $Password.Length -gt 1024 -or $Password -match '["\r\n\x00]') {
        throw 'TX_PASS ist fuer den SQL*Plus-Bereitschaftscheck nicht verwendbar (leer, zu lang, doppeltes Quote oder Zeilenumbruch)'
    }
    return "set echo off`nset define off`nwhenever oserror exit failure`nwhenever sqlerror exit failure rollback`nconnect $UserName/`"$Password`"@//localhost:1521/FREEPDB1`nselect 1 from dual;`nexit success`n"
}

function Get-LmgDatabaseMode([string]$Url) {
    if ($Url -match '(?i)^jdbc:oracle:thin:@//(?:localhost|127\.0\.0\.1):1521(?:/|$)') { return 'DOCKER' }
    if ($Url -match '(?i)^jdbc:oracle:thin:@//(?:localhost|127\.0\.0\.1):1921(?:/|$)') { return 'VM' }
    return 'EXTERNAL'
}

function Invoke-LmgBoundedProcess([string]$FilePath, [string[]]$Arguments, [string]$InputText = '', [int]$TimeoutMilliseconds = 10000) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.Arguments = (@($Arguments | ForEach-Object { ConvertTo-LmgProcessArgument $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    $started = $false
    try {
        [void]$process.Start()
        $started = $true
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($InputText) {
            $bytes = [Text.Encoding]::UTF8.GetBytes($InputText)
            $write = $process.StandardInput.BaseStream.WriteAsync($bytes, 0, $bytes.Length)
            $remaining = [Math]::Max(0, $TimeoutMilliseconds - [int]$watch.ElapsedMilliseconds)
            if (-not $write.Wait($remaining)) { return [pscustomobject]@{ExitCode=-1; TimedOut=$true; Output=''} }
        }
        $process.StandardInput.Close()
        $remaining = [Math]::Max(0, $TimeoutMilliseconds - [int]$watch.ElapsedMilliseconds)
        if (-not $process.WaitForExit($remaining)) { return [pscustomobject]@{ExitCode=-1; TimedOut=$true; Output=''} }
        foreach ($task in @($stdout, $stderr)) {
            $remaining = [Math]::Max(0, $TimeoutMilliseconds - [int]$watch.ElapsedMilliseconds)
            if (-not $task.Wait($remaining)) { return [pscustomobject]@{ExitCode=-1; TimedOut=$true; Output=''} }
        }
        return [pscustomobject]@{ExitCode=$process.ExitCode; TimedOut=$false; Output=$stdout.Result}
    } catch {
        # Keine Prozessausgabe/Argumente/SQL-Zeilen in Fehlermeldungen aufnehmen.
        return [pscustomobject]@{ExitCode=-1; TimedOut=$false; Output=''; Unavailable=$true}
    } finally {
        if ($started) {
            try { if (-not $process.HasExited) { $process.Kill() } }
            catch [InvalidOperationException] { <# Bereits zwischen Pruefung und Kill beendet. #> }
        }
        $process.Dispose()
    }
}

function Wait-LmgDockerDatabase(
    [string]$DockerPath, [string]$UserName, [string]$Password,
    [int]$TimeoutMilliseconds = 300000, [int]$AttemptMilliseconds = 10000, [int]$RetryMilliseconds = 5000,
    [scriptblock]$Runner = { param($file, $argv, $inputText, $limit) Invoke-LmgBoundedProcess $file $argv $inputText $limit },
    [scriptblock]$Elapsed = $null,
    [scriptblock]$Delay = { param($milliseconds) Start-Sleep -Milliseconds $milliseconds }
) {
    if ($TimeoutMilliseconds -lt 1 -or $AttemptMilliseconds -lt 1 -or $RetryMilliseconds -lt 1) { throw 'Zeitlimits muessen positiv sein' }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -eq $Elapsed) { $Elapsed = { $watch.ElapsedMilliseconds }.GetNewClosure() }
    $inputText = New-LmgSqlPlusInput $UserName $Password
    $run = {
        param([string[]]$argv, [string]$stdin = '')
        $remaining = $TimeoutMilliseconds - [long](& $Elapsed)
        if ($remaining -le 0) { return [pscustomobject]@{ExitCode=-1;TimedOut=$true;Output=''} }
        & $Runner $DockerPath $argv $stdin ([int][Math]::Min($AttemptMilliseconds, $remaining))
    }.GetNewClosure()
    $result = & $run -argv @('info')
    if ($result.ExitCode -ne 0) { Write-Output '[Hinweis] Docker ist nicht erreichbar - Datenbank muss extern laufen.'; return $false }
    $result = & $run -argv @('inspect','--format','{{.State.Running}}','lmg-oracle')
    if ($result.ExitCode -ne 0) { Write-Output '[Hinweis] Container lmg-oracle ist nicht erreichbar.'; return $false }
    if ($result.Output.Trim() -ceq 'false') {
        Write-Output 'Starte Entwickler-DB (Container lmg-oracle) ...'
        $result = & $run -argv @('start','lmg-oracle')
        if ($result.ExitCode -ne 0) { Write-Output '[Warnung] Containerstart nicht bestaetigt; Bereitschaft wird weiter geprueft.' }
    } elseif ($result.Output.Trim() -cne 'true') {
        Write-Output '[Warnung] Containerstatus ist nicht eindeutig; kein Start ausgefuehrt.'
        return $false
    }
    while (($remaining = $TimeoutMilliseconds - [long](& $Elapsed)) -ge 1000) {
        $limit = [int][Math]::Min($AttemptMilliseconds, $remaining)
        # Auch die im Container laufende SQL*Plus-Instanz endet begrenzt, falls
        # der lokale Docker-Client wegen seines Zeitlimits beendet wird.
        $seconds = ([double]$limit / 1000).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
        $argv = @('exec','-i','lmg-oracle','timeout','--signal=KILL',($seconds + 's'),'sqlplus','-S','-L','/nolog')
        $result = & $Runner $DockerPath $argv $inputText $limit
        if ($result.ExitCode -eq 0) { Write-Output 'Datenbank ist bereit.'; return $true }
        if ($result.ExitCode -in @(126,127) -or $result.Unavailable) {
            Write-Output '[Warnung] SQL*Plus, timeout oder Docker konnte nicht ausgefuehrt werden.'
            return $false
        }
        $remaining = $TimeoutMilliseconds - [long](& $Elapsed)
        if ($remaining -le 0) { break }
        Write-Output 'Warte auf Datenbank ...'
        & $Delay ([int][Math]::Min($RetryMilliseconds, $remaining))
    }
    Write-Output '[Warnung] DB innerhalb der Gesamtfrist nicht bereit - Start wird trotzdem fortgesetzt.'
    return $false
}
