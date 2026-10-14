$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'stop.ps1') -LibraryOnly
$root = 'D:\Fixture\LMG'
function Assert-Owner([string]$Name, [string]$Command, [bool]$Expected) {
    $actual = Test-SweetOwner ([pscustomobject]@{Name=$Name;CommandLine=$Command}) $root
    if ($actual -ne $Expected) { throw "Falsche Eigentumsentscheidung: erwartet $Expected, ist $actual" }
}
Assert-Owner 'java.exe' 'java -cp D:\Fixture\LMG\testsweet\target\classes de.allgemein.testsweet.SweetMain' $true
Assert-Owner 'java.exe' 'java -cp D:\Other\testsweet\target\classes de.allgemein.testsweet.SweetMain' $false
Assert-Owner 'java.exe' 'java -cp D:\Fixture\LMG-other\testsweet\target\classes de.allgemein.testsweet.SweetMain' $false
Assert-Owner 'java.exe' 'java -cp D:\Fixture\LMG\target\classes de.allgemein.lmg.web.LmgServerMain' $false
Assert-Owner 'node.exe' 'node D:\Fixture\LMG\testsweet\app.js' $false
Assert-Owner 'java.exe' 'java -Dmaven.multiModuleProjectDirectory=D:\Fixture\LMG org.codehaus.plexus.classworlds.launcher.Launcher -q -f testsweet/pom.xml compile exec:java' $true
Assert-Owner 'java.exe' 'java -Dmaven.multiModuleProjectDirectory=D:\Other org.codehaus.plexus.classworlds.launcher.Launcher -q -f D:\Fixture\LMG\testsweet\pom.xml compile exec:java' $false
Assert-Owner 'java.exe' 'java -Dmaven.multiModuleProjectDirectory=D:\Fixture\LMG org.codehaus.plexus.classworlds.launcher.Launcher -f testsweet/pom.xml exec:java -Dexec.mainClass=other.Main' $false
Assert-Owner 'java.exe' 'java -Dmaven.multiModuleProjectDirectory=D:\Fixture\LMG org.codehaus.plexus.classworlds.launcher.Launcher -f other/pom.xml exec:java' $false
Write-Output '9 synthetische Eigentumspruefungen bestanden; kein Prozess wurde gestartet oder beendet.'
