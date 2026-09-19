$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$log  = Join-Path $root 'logs\gradle-bootstrap.log'

function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}
Set-Content -Path $log -Value "=== gradle bootstrap $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8
Log "PROJECT=$root"

# JDK 21 is the pick: AGP supports it, and it avoids the JDK 24/25 toolchain surprises.
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) { Log "FATAL: JDK 21 not at $jdk"; exit 3 }
$env:JAVA_HOME = $jdk
Log "JAVA_HOME=$jdk"

# Find an already-downloaded Gradle distribution. No network needed to bootstrap.
$dist = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\wrapper\dists') -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue } |
        ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue } |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\gradle.bat') }

if (-not $dist) { Log "FATAL: no gradle distribution found under ~/.gradle/wrapper/dists"; exit 4 }
foreach ($d in $dist) { Log "gradle dist found: $($d.FullName)" }

$gradle = Join-Path ($dist | Select-Object -Last 1).FullName 'bin\gradle.bat'
Log "USING GRADLE=$gradle"

Push-Location $root

Log "--- step 1: generate the wrapper ---"
$wlog = Join-Path $root 'logs\gradle-wrapper-gen.log'
& cmd /c "`"$gradle`" wrapper --gradle-version 8.14.5 --distribution-type bin --no-daemon" 1> $wlog 2>&1
Log "wrapper task exit code: $LASTEXITCODE"
Log "gradlew exists: $(Test-Path (Join-Path $root 'gradlew.bat'))"
Log "wrapper jar exists: $(Test-Path (Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'))"

Log "--- step 2: probe dependency versions ---"
$plog = Join-Path $root 'logs\gradle-probe.log'
& cmd /c "`"$gradle`" probeVersions --no-daemon --stacktrace" 1> $plog 2>&1
Log "probeVersions exit code: $LASTEXITCODE"

Pop-Location
Log "=== DONE ==="
