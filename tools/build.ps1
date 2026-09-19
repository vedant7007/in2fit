$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$log  = Join-Path $root 'logs\build.log'

function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}
Set-Content -Path $log -Value "=== build $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:JAVA_HOME = $jdk
Log "JAVA_HOME=$jdk"
Log "PROJECT=$root"

Push-Location $root
$gw = Join-Path $root 'gradlew.bat'
Log "gradlew exists: $(Test-Path $gw)"

Log "--- probeVersions (KSP and Hilt) ---"
& cmd /c "`"$gw`" probeVersions --no-daemon" 1> (Join-Path $root 'logs\gradle-probe.log') 2>&1
Log "probeVersions exit: $LASTEXITCODE"

Log "--- assembleDemoDebug ---"
& cmd /c "`"$gw`" :app:assembleDemoDebug --no-daemon --stacktrace" 1> (Join-Path $root 'logs\gradle-assemble.log') 2>&1
$asmExit = $LASTEXITCODE
Log "assembleDemoDebug exit: $asmExit"

Log "--- unit tests ---"
& cmd /c "`"$gw`" :app:testDemoDebugUnitTest --no-daemon" 1> (Join-Path $root 'logs\gradle-test.log') 2>&1
Log "testDemoDebugUnitTest exit: $LASTEXITCODE"

Log "--- apk on disk, one row per APK appended to logs\apk-size.log with the commit and its sha256 ---"
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'apk-size.ps1') | ForEach-Object { Log $_ }

Log "--- decoding gradle logs to utf8 ---"
Get-ChildItem (Join-Path $root 'logs') -Filter 'gradle-*.log' | ForEach-Object {
  $txt = Get-Content $_.FullName -Raw -Encoding Unicode
  if ($txt -notmatch '[A-Za-z]') { $txt = Get-Content $_.FullName -Raw }
  $out = Join-Path $root ('logs\utf8-' + $_.Name)
  Set-Content -Path $out -Value $txt -Encoding UTF8
  Log "decoded: $out"
}

Pop-Location
Log "=== DONE ==="
