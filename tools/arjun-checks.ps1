# Arjun's device checks, one scripted run (docs/demo/device-queue.md items 10, 11, 13-15, 24-28, 5a).
# Runs `ArjunDeviceChecksTest` on the phone through the DEFAULT adb server only, after stopping
# the app so one process holds the database, and prints every ARJUN-CHECK line it decided, then
# one VERDICT line. Needs the demo debug APK and its androidTest APK installed
# (measurement-pass.ps1 builds and installs both; or `gradlew :app:installDemoDebug
# :app:installDemoDebugAndroidTest`).
#
#   powershell -File tools\arjun-checks.ps1            # all items, about a minute
#   powershell -File tools\arjun-checks.ps1 -Only i    # one method prefix (a..i), e.g. i = the PDF
param([string]$Only = "")

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$pkg = "io.github.vedant7007.katori"
$cls = "$pkg.data.local.ArjunDeviceChecksTest"
$runner = "$pkg.test/androidx.test.runner.AndroidJUnitRunner"
$repo = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$log = Join-Path $repo "logs\arjun-checks-$stamp.txt"
# UTF-8 on purpose: Tee-Object writes UTF-16 on PowerShell 5.1 and grep cannot read it.
function Line($s) { Add-Content -Encoding utf8 $log $s; Write-Host $s }

$servers = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'fork-server server' }
if ($servers | Where-Object { $_.CommandLine -notmatch 'tcp:5037' }) { throw "REFUSED: a second adb server is running; default server only while the phone is on USB" }
$devices = @((& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' })
if ($devices.Count -ne 1) { throw "REFUSED: expected exactly one device on the default server, saw $($devices.Count)" }

Line "ARJUN CHECKS $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  commit $((& git -C $repo rev-parse --short HEAD).Trim())  device $($devices[0] -split '\t' | Select-Object -First 1)"
& $adb shell am force-stop $pkg
# A copy of the database BEFORE anything opens it, so a refused migration can be reproduced on the
# JVM from the real rows and nothing the person logged is lost (debug build: run-as works).
# Through cmd, because PowerShell's own redirection re-encodes bytes as text and corrupts a database.
$backup = Join-Path $repo "logs\katori-user-$stamp.db"
cmd /c "`"$adb`" exec-out run-as $pkg cat databases/katori-user.db > `"$backup`""
$wal = (& $adb shell run-as $pkg ls databases/ 2>$null) -match 'katori-user\.db-wal'
if ($wal) { cmd /c "`"$adb`" exec-out run-as $pkg cat databases/katori-user.db-wal > `"$backup-wal`"" }
Line "database copied before the run: $backup ($((Get-Item $backup).Length) bytes$(if ($wal) { ' + wal' }))"
& $adb shell rm -f "/sdcard/Android/media/$pkg/katori-arjun-checks.txt" 2>$null
& $adb logcat -c

# `-e class Class#method` needs the full method name; a prefix selects through the runner's filter instead.
$iargs = if ($Only) { @("-e", "class", $cls, "-e", "tests_regex", "$Only.*") } else { @("-e", "class", $cls) }
$raw = @(& $adb shell am instrument -w -r @iargs $runner 2>&1 | ForEach-Object { "$_" })
$raw | ForEach-Object { Add-Content -Encoding utf8 $log $_ }
Line ""
Line "RESULTS (from logcat, tag katori-arjun):"
$results = @(& $adb logcat -d -s katori-arjun:I | ForEach-Object { "$_" } | Where-Object { $_ -match 'ARJUN-CHECK' } | ForEach-Object { $_ -replace '^.*ARJUN-CHECK', 'ARJUN-CHECK' })
$results | ForEach-Object { Line $_ }
$passes = @($results | Where-Object { $_ -match ' PASS ' })
$fails = @($results | Where-Object { $_ -match ' FAIL ' })
$summary = ($raw | Where-Object { $_ -match 'OK \(|FAILURES!!!|Tests run|INSTRUMENTATION_CODE' }) -join ' | '
Line ""
Line "instrumentation: $summary"
$tail = if ($fails.Count -gt 0) { ' -> ' + (($fails | ForEach-Object { ($_ -split ' ')[1] }) -join ', ') }
        elseif ($results.Count -eq 0) { ' -> NO LINES: the run did not reach the checks; read the stack above' }
        else { '' }
Line ("VERDICT: {0} PASS, {1} FAIL{2}" -f $passes.Count, $fails.Count, $tail)
Line "log: $log"
