$ErrorActionPreference = 'Stop'
$reviewRepo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$reviewClasses = Join-Path $PSScriptRoot 'classes'
$reviewUcp = Join-Path $env:USERPROFILE '.m2/repository/com/oracle/database/jdbc/ucp/23.5.0.24.07/ucp-23.5.0.24.07.jar'
$reviewJdbc = Join-Path $env:USERPROFILE '.m2/repository/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar'
New-Item -ItemType Directory -Force -Path $reviewClasses | Out-Null
javac --release 21 -encoding UTF-8 -cp "$reviewUcp;$reviewJdbc" -sourcepath (Join-Path $reviewRepo 'src/main/java') -d $reviewClasses (Join-Path $PSScriptRoot 'KernProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Isolierter javac fehlgeschlagen' }
java -cp "$reviewClasses;$reviewUcp;$reviewJdbc" de.allgemein.tx.saga.KernProbe 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'probe-output.txt')
if ($LASTEXITCODE -ne 0) { throw 'Isolierte Kernprobe fehlgeschlagen' }
