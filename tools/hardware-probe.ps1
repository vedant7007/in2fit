# The hardware probe runner.
#
# Everything Katori has asserted but never verified, answered on the Realme 11, in one pass, with
# every step redirected to a log that can be read afterwards. Nothing here reports a result it did
# not capture.
#
# ORDER MATTERS. Install and launch first, because if the APK does not open on this phone then no
# number that follows means anything.

$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$logs = Join-Path $root 'logs'
$log  = Join-Path $logs 'hardware.log'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}
Set-Content -Path $log -Value "=== hardware probe $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$pkg = 'io.github.vedant7007.katori'
$gw  = Join-Path $root 'gradlew.bat'

# Gradle resolves the project from the CURRENT directory, not from the path to gradlew.bat.
# Without this the build runs from tools\ and fails with "not part of the build".
Push-Location $root

Log "adb at $adb, exists: $(Test-Path $adb)"
if (-not (Test-Path $adb)) { Log 'FATAL: no adb. Stopping.'; exit 1 }

# ---------------------------------------------------------------- 0. the phone
Log '--- devices ---'
& $adb devices -l 2>&1 | ForEach-Object { Log "  $_" }
$serialLines = (& $adb devices) | Select-String -Pattern "`tdevice$"
if (-not $serialLines) { Log 'FATAL: no device in state "device". Check USB debugging and the RSA prompt.'; exit 1 }
$serial = ($serialLines[0] -split "`t")[0]
Log "using serial $serial"

foreach ($p in @('ro.product.manufacturer','ro.product.model','ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abi')) {
  $v = (& $adb -s $serial shell getprop $p).Trim()
  Log "  $p = $v"
}
Log "  MemTotal = $((& $adb -s $serial shell cat /proc/meminfo | Select-String MemTotal).ToString().Trim())"

# ---------------------------------------------------------------- 1. build, install, launch
Log '--- assembling demo debug and the test APK ---'
& cmd /c "`"$gw`" :app:assembleDemoDebug :app:assembleDemoDebugAndroidTest --no-daemon --stacktrace" 1> (Join-Path $logs 'hw-assemble.log') 2>&1
Log "assemble exit: $LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { Log 'FATAL: assemble failed. See logs\hw-assemble.log'; exit 1 }

Log '--- installing ---'
& cmd /c "`"$gw`" :app:installDemoDebug --no-daemon" 1> (Join-Path $logs 'hw-install.log') 2>&1
Log "installDemoDebug exit: $LASTEXITCODE"

Log '--- launching, and watching for a crash ---'
& $adb -s $serial logcat -c
& $adb -s $serial shell am start -n "$pkg/.ui.MainActivity" 2>&1 | ForEach-Object { Log "  $_" }
Start-Sleep -Seconds 6
$running = (& $adb -s $serial shell pidof $pkg).Trim()
Log "pid after launch: '$running'  (empty means it is not running)"
& $adb -s $serial logcat -d -v brief 2>&1 | Out-File (Join-Path $logs 'hw-launch-logcat.log') -Encoding UTF8
$crash = Select-String -Path (Join-Path $logs 'hw-launch-logcat.log') -Pattern 'FATAL EXCEPTION|ANR in|AndroidRuntime' -SimpleMatch:$false
if ($crash) { Log "CRASH MARKERS FOUND: $($crash.Count) lines, see logs\hw-launch-logcat.log" } else { Log 'no crash markers in logcat' }

Log '--- screenshot of the launched app ---'
& $adb -s $serial shell screencap -p /sdcard/katori-launch.png
& $adb -s $serial pull /sdcard/katori-launch.png (Join-Path $logs 'hw-launch.png') 2>&1 | ForEach-Object { Log "  $_" }
& $adb -s $serial shell rm -f /sdcard/katori-launch.png

# ---------------------------------------------------------------- 2. push the models
# The app's external media directory needs no runtime permission and adb can write it.
$dest = "/sdcard/Android/media/$pkg/models"
Log "--- pushing models to $dest ---"
& $adb -s $serial shell mkdir -p $dest 2>&1 | ForEach-Object { Log "  $_" }

$pushes = @(
  @{ src = "$root\data-sources\models\llm\qwen2.5-1.5b-instruct-q4_k_m.gguf"; dst = "$dest/qwen2.5-1.5b-instruct-q4_k_m.gguf" },
  @{ src = "$root\data-sources\models\asr\indicconformer\te\model.int8.onnx"; dst = "$dest/asr-te-model.int8.onnx" },
  @{ src = "$root\data-sources\models\tts\piper\te_IN-padmavathi-medium.onnx"; dst = "$dest/piper-te-model.onnx" }
)
foreach ($p in $pushes) {
  if (-not (Test-Path $p.src)) { Log "MISSING SOURCE: $($p.src)"; continue }
  $size = [math]::Round((Get-Item $p.src).Length / 1MB, 1)
  $already = (& $adb -s $serial shell "ls -l $($p.dst) 2>/dev/null").Trim()
  if ($already) { Log "already on device: $($p.dst)  [$already]"; continue }
  Log "pushing $($p.src) ($size MB) ..."
  $t0 = Get-Date
  & $adb -s $serial push $p.src $p.dst 2>&1 | Select-Object -Last 1 | ForEach-Object { Log "  $_" }
  Log "  took $([math]::Round(((Get-Date) - $t0).TotalSeconds,1)) s"
}
Log '--- what is on the device now ---'
& $adb -s $serial shell "ls -l $dest" 2>&1 | ForEach-Object { Log "  $_" }

# ---------------------------------------------------------------- 3. the probe itself
Log '--- running HardwareProbeTest on the device ---'
& $adb -s $serial logcat -c
& cmd /c "`"$gw`" :app:connectedDemoDebugAndroidTest --no-daemon --stacktrace" 1> (Join-Path $logs 'hw-connected.log') 2>&1
Log "connectedDemoDebugAndroidTest exit: $LASTEXITCODE"

Log '--- pulling the report off the phone ---'
& $adb -s $serial pull "/sdcard/Android/media/$pkg/katori-hardware-report.txt" (Join-Path $logs 'hw-report.txt') 2>&1 | ForEach-Object { Log "  $_" }
& $adb -s $serial logcat -d -s katori-probe:I katori-llama:I AndroidRuntime:E 2>&1 | Out-File (Join-Path $logs 'hw-probe-logcat.log') -Encoding UTF8

Log '--- decoding gradle logs to utf8 ---'
Get-ChildItem $logs -Filter 'hw-*.log' | ForEach-Object {
  try {
    $txt = Get-Content $_.FullName -Raw -Encoding Unicode
    if ($txt -and $txt -notmatch "`0") {
      Set-Content -Path (Join-Path $logs ("utf8-" + $_.Name)) -Value $txt -Encoding UTF8
    }
  } catch { }
}

Log '=== done. Read logs\hw-report.txt first, then logs\hardware.log ==='
Pop-Location
