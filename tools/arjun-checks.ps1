# Arjun's device checks, one scripted run (docs/demo/device-queue.md items 10, 13-15, 24-28, 35).
# Runs `ArjunDeviceChecksTest` on the phone through the DEFAULT adb server only, after stopping
# the app so one process holds the database, and prints every ARJUN-CHECK line it decided.
# Needs the demo debug APK and its androidTest APK installed (measurement-pass.ps1 builds and
# installs both; or `gradlew :app:installDemoDebug :app:installDemoDebugAndroidTest`).
#
#   powershell -File tools\arjun-checks.ps1            # all items
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

$servers = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'fork-server server' }
if ($servers | Where-Object { $_.CommandLine -notmatch 'tcp:5037' }) { throw "REFUSED: a second adb server is running; default server only while the phone is on USB" }
$devices = @((& $adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' })
if ($devices.Count -ne 1) { throw "REFUSED: expected exactly one device on the default server, saw $($devices.Count)" }

"ARJUN CHECKS $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  commit $((& git -C $repo rev-parse --short HEAD).Trim())" | Tee-Object -FilePath $log
& $adb shell am force-stop $pkg
& $adb shell rm -f "/sdcard/Android/media/$pkg/katori-arjun-checks.txt" 2>$null
& $adb logcat -c

# `-e class Class#method` needs the full method name; a prefix selects through the runner's filter instead.
$iargs = if ($Only) { @("-e", "class", $cls, "-e", "tests_regex", "$Only.*") } else { @("-e", "class", $cls) }
& $adb shell am instrument -w -r @iargs $runner 2>&1 | Tee-Object -FilePath $log -Append | Out-Null
"" | Tee-Object -FilePath $log -Append
"RESULTS (from logcat, tag katori-arjun):" | Tee-Object -FilePath $log -Append
& $adb logcat -d -s katori-arjun:I | Select-String "ARJUN-CHECK" | ForEach-Object { $_.Line -replace '^.*ARJUN-CHECK', 'ARJUN-CHECK' } | Tee-Object -FilePath $log -Append
$summary = (Get-Content $log | Select-String "INSTRUMENTATION_STATUS_CODE|OK \(|FAILURES!!!|Tests run" | ForEach-Object { $_.Line }) -join "`n"
"" | Tee-Object -FilePath $log -Append
"instrumentation: $($summary -replace '\s+', ' ')" | Tee-Object -FilePath $log -Append
"log: $log"
