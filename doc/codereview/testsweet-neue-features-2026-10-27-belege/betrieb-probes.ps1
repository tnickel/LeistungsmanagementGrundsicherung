param([string]$ProjectRoot = (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))))

$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)

# Ausschliesslich die reine Verifikationsfunktion auslesen. Der Skriptkoerper
# mit Portsuche, taskkill und Docker wird NICHT ausgefuehrt.
$source = Get-Content -LiteralPath (Join-Path $ProjectRoot 'stop.ps1') -Raw
$ast = [System.Management.Automation.Language.Parser]::ParseInput($source, [ref]$null, [ref]$null)
$definition = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Test-LmgOwner'
}, $true)
if (-not $definition) { throw 'Test-LmgOwner wurde nicht gefunden' }
. ([scriptblock]::Create($definition.Extent.Text))

$mavenPrefix = 'java -Dmaven.multiModuleProjectDirectory="' + $ProjectRoot + '" org.codehaus.plexus.classworlds.launcher.Launcher exec:java'
$cases = @(
    @{name = 'Maven ohne exec.mainClass, POM-Default DemoMain'; command = $mavenPrefix; expectedCurrent = $true},
    @{name = 'Maven explizit fremde Klasse'; command = $mavenPrefix + ' -Dexec.mainClass=example.OtherServer'; expectedCurrent = $false},
    @{name = 'Maven fremde Klasse als gequotetes Argument'; command = $mavenPrefix + ' "-Dexec.mainClass=example.OtherServer"'; expectedCurrent = $true},
    @{name = 'Direkter Start mit Projekt-Classpath'; command = 'java -cp "' + $ProjectRoot + '/target/classes" de.allgemein.lmg.web.LmgServerMain'; expectedCurrent = $true},
    @{name = 'Direkter Start von externem JAR ohne Projektpfad'; command = 'java -cp C:/runtime/lmg.jar de.allgemein.lmg.web.LmgServerMain'; expectedCurrent = $false}
)
$selection = foreach ($case in $cases) {
    $accepted = Test-LmgOwner ([pscustomobject]@{Name = 'java.exe'; CommandLine = $case.command}) $ProjectRoot
    if ($accepted -ne $case.expectedCurrent) { throw ('Auswahlverhalten geaendert: ' + $case.name) }
    [pscustomobject]@{Name = $case.name; Accepted = $accepted; CommandLine = $case.command}
}
Write-Output 'SICHERE AST-PROBE: kein Portzugriff, kein taskkill, kein Docker, nur fiktive Prozessobjekte.'
$selection | ConvertTo-Json -Depth 3

# Die Bash-Probe ersetzt SQL*Plus durch printf und verwendet nur Dummywerte.
$bashCandidates = @('C:\Program Files\Git\bin\bash.exe', 'C:\Program Files\Git\usr\bin\bash.exe')
$bash = $bashCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $bash) { throw 'Git-Bash nicht gefunden; Bash-Probe mit bash-passwort-probe.sh separat ausfuehren.' }
$bashSource = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'bash-passwort-probe.sh') -Raw
Write-Output 'SICHERE BASH-PROBE: sqlplus ist eine printf-Funktion; Passwort-Dummy ist p$ssword.'
$bashOutput = @(& $bash -c $bashSource)
if ($LASTEXITCODE -ne 0) { throw 'Bash-Probe fehlgeschlagen' }
$bashOutput | Write-Output
if ($bashOutput -notcontains 'ARG=<review/p@localhost:1521/FREEPDB1>') { throw 'Erwartete Passwort-Expansion wurde nicht reproduziert' }
Write-Output 'PROBEN REPRODUZIERT. Dies ist ein Fehlernachweis des geprueften Stands, kein bestandener Sicherheitstest.'
