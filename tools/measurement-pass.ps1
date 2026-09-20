# THE MEASUREMENT PASS, one invocation, unattended, one results file.
#
#   powershell -File tools\measurement-pass.ps1                       # the one USB device
#   powershell -File tools\measurement-pass.ps1 -Serial emulator-5600 # dry run on an emulator (NOT CLAIMABLE)
#   powershell -File tools\measurement-pass.ps1 -SkipInstall          # the build on the phone is the one to measure
#
# Written for the moment the phone comes back (20 Sep, night): the link may hold for one session
# and no more, and that session must not be spent deciding what to run. Five stages:
#   1. ANSWER first figure and spoken-complete, cold and warm        (MeasurementPassTest)
#   2. AdviseOnMeal first tap, after a LOG and after a report         (MeasurementPassTest)
#   3. LOG two-food and three-food, plate and spoken, twice each      (MeasurementPassTest)
#   4. Peak PSS against the arbiter's ceiling                         (MeasurementPassTest)
#   5. AsrDeviceTest.b, cold (fresh process) then warm (second run)   (Jacob's test, twice)
#
# REFUSES TO RUN unless the device is in airplane mode, and unless the DEFAULT adb server owns it
# with no second adb server on this laptop while a USB device is attached (Ira's 5bd5458 rule: two
# servers race for the device on every re-enumeration and the private one can win). It does not
# put the device into airplane mode itself; that is a condition to be met, not arranged.
#
# EVERY RAW FIGURE, NEVER A MEAN: the results file is the device's own lines (`RAW stage=n ...`)
# under a header that says the device, the build, the timestamp and the commit, so a number can
# never be detached from what produced it. If the phone drops off mid-run the file says which of
# the five stages completed and which did not, from the stage markers it managed to pull.
param(
  [string]$Serial = "",
  [switch]$SkipInstall,
  [string]$Runner = "io.github.vedant7007.katori.test/androidx.test.runner.AndroidJUnitRunner"
)
$ErrorActionPreference = 'Continue'
$adb  = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pkg  = 'io.github.vedant7007.katori'
$M    = "/sdcard/Android/media/$pkg"
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force (Join-Path $repo 'logs') | Out-Null
$out  = Join-Path $repo "logs\measurement-pass-$stamp.txt"
$stages = [ordered]@{ '1' = 'not started'; '2' = 'not started'; '3' = 'not started'; '4' = 'not started'; '5' = 'not started' }  # string keys: an integer indexes by position

function Say($s) { $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $s; Add-Content -Encoding utf8 $out $line; Write-Host $line }
function Line($s) { Add-Content -Encoding utf8 $out $s }
function Adb([string[]]$argv, [int]$timeoutSec) {
  # Every adb call gets a hard timeout. A half-dead USB link (listed as `device`, answers nothing)
  # left `adb install` hanging for 29 minutes on 20 Sep; an unattended pass must never wait on it.
  $quoted = ($argv | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
  $tmp = [IO.Path]::GetTempFileName()
  $p = Start-Process -FilePath $adb -ArgumentList $quoted -NoNewWindow -PassThru -RedirectStandardOutput $tmp -RedirectStandardError "$tmp.err"
  if ($p.WaitForExit($timeoutSec * 1000)) {
    $r = @(Get-Content $tmp -ErrorAction SilentlyContinue) + @(Get-Content "$tmp.err" -ErrorAction SilentlyContinue)
  } else {
    try { $p.Kill() } catch {}
    $r = @("ADB TIMEOUT after ${timeoutSec}s: $quoted")
  }
  Remove-Item $tmp, "$tmp.err" -Force -ErrorAction SilentlyContinue
  $r | ForEach-Object { "$_" }
}
function Sh($cmd) { Adb @('-s', $Serial, 'shell', $cmd) 60 }
function Refuse($why) { Say "REFUSED: $why"; Line "RESULT: refused before any stage ran"; exit 2 }

# --- 0. ownership and conditions -------------------------------------------------------------
Line "MEASUREMENT PASS  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
$commit = (& git -C $repo rev-parse HEAD 2>$null).Trim()
$dirty  = ((& git -C $repo status --porcelain --untracked-files=no 2>$null | Measure-Object).Count -gt 0)
Line "commit:  $commit$(if ($dirty) { '  (DIRTY working tree)' })"
Line "script:  tools/measurement-pass.ps1"

$servers = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'fork-server server' }
$defaultServer = $servers | Where-Object { $_.CommandLine -match 'tcp:5037' }
$otherServers  = $servers | Where-Object { $_.CommandLine -notmatch 'tcp:5037' }
if (-not $defaultServer) { & $adb start-server | Out-Null; Start-Sleep 2 }

# One object per row; a bare array per row unrolls in the pipeline and `$_[0]` becomes a letter.
$devices = @((& $adb devices 2>&1) | ForEach-Object { "$_" } | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } |
  ForEach-Object { $f = $_ -split '\s+'; [pscustomobject]@{ Serial = $f[0]; State = $f[1] } })
$listed = ($devices | ForEach-Object { "$($_.Serial) $($_.State)" }) -join ', '
if (-not $Serial) {
  $usb = @($devices | Where-Object { $_.State -eq 'device' -and $_.Serial -notmatch '^emulator-|:\d+$' } | ForEach-Object { $_.Serial })
  if ($usb.Count -ne 1) { Refuse "expected exactly one USB device on the DEFAULT adb server, found $($usb.Count): $listed" }
  $Serial = $usb[0]
}
$owned = $devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq 'device' }
if (-not $owned) { Refuse "the default adb server (port 5037) does not own '$Serial': $listed" }
$isEmulator = $Serial -match '^emulator-|^127\.0\.0\.1:|^localhost:'
$usbAttached = @($devices | Where-Object { $_.Serial -notmatch '^emulator-|:\d+$' }).Count -gt 0
if ($otherServers -and $usbAttached) {
  Refuse "a second adb server is running while a USB device is attached (5bd5458): $($otherServers | ForEach-Object { "pid $($_.ProcessId): $($_.CommandLine)" })"
}
if ($otherServers) { Say "note: a second adb server is running (pid $($otherServers.ProcessId)); tolerated because no USB device is attached" }

$airplane = (Sh 'settings get global airplane_mode_on' | Select-Object -Last 1).Trim()
if ($airplane -ne '1') { Refuse "device '$Serial' is not in airplane mode (airplane_mode_on=$airplane); put it in airplane mode and run again" }
# Wi-Fi can stay up under airplane mode (wifi_on=3, the override); a radio is a radio.
$wifi = (Sh 'settings get global wifi_on' | Select-Object -Last 1).Trim()
if ($wifi -ne '0') { Refuse "device '$Serial' still has Wi-Fi on under airplane mode (wifi_on=$wifi); 'svc wifi disable' and run again" }

$claimable = -not $isEmulator
$props = Sh 'getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.hardware'
Line "device:  $Serial  $(($props | ForEach-Object { $_.Trim() }) -join ' / ')"
Line "claimable: $claimable$(if (-not $claimable) { '  <- EMULATOR: this run proves the harness, not the numbers' })"
Line "airplane_mode_on: $airplane   wifi_on: $wifi"
Line ("state before: " + ((Sh "cat /proc/meminfo | grep MemAvailable; dumpsys battery | grep -E 'level|USB powered|AC powered'; dumpsys thermalservice | grep 'Thermal Status'; dumpsys activity activities | grep topResumedActivity | head -1") | ForEach-Object { $_.Trim() }) -join ' | ')

# --- 0b. the build ---------------------------------------------------------------------------
$appApk  = Join-Path $repo 'app\build\outputs\apk\demo\debug\app-demo-debug.apk'
$testApk = Join-Path $repo 'app\build\outputs\apk\androidTest\demo\debug\app-demo-debug-androidTest.apk'
if (-not $SkipInstall) {
  foreach ($apk in @($appApk, $testApk)) {
    if (-not (Test-Path $apk)) { Refuse "no APK at $apk; build first or pass -SkipInstall" }
    # The USB link drops every few minutes on this phone (20 Sep); a 68 MB install is the
    # longest single transfer, so it gets three tries, each after the device is seen again.
    $r = ''
    foreach ($try in 1..3) {
      $r = (Adb @('-s', $Serial, 'install', '-r', '-t', $apk) 300 | Select-Object -Last 1)
      Say "install $(Split-Path $apk -Leaf) (try $try): $r"
      if ($r -match 'Success') { break }
      Adb @('-s', $Serial, 'wait-for-device') 60 | Out-Null; Start-Sleep 3
    }
    if ($r -notmatch 'Success') { Refuse "install failed three times ($r); the link did not hold. Re-seat the cable and run again." }
  }
  Line "app apk sha256:  $((Get-FileHash $appApk -Algorithm SHA256).Hash.ToLower())"
  Line "test apk sha256: $((Get-FileHash $testApk -Algorithm SHA256).Hash.ToLower())"
}
Line ("build:   " + ((Sh "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime' | head -2") | ForEach-Object { $_.Trim() }) -join '  ')
Line ""

# --- running a stage, tolerating a dropped link ------------------------------------------------
function RunDetached($cls, $log, $extra) {
  # The test writes its own file as it goes; a dropped adb link does not kill it.
  $script:launchedAt = "$(Sh "date '+%m-%d %H:%M:%S.000'" | Select-Object -Last 1)".Trim()
  Sh "input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; (nohup am instrument -w -r $extra -e class $cls $Runner > $log 2>&1 &); echo started" | Out-Null
}
function Ended($log) {
  # How the process ended, from the runner and from the kernel since this launch: a low-memory
  # kill is SIGKILL and leaves no END marker; another session's install force-stops it; a
  # native crash under emulation is a SIGSEGV. The file carries the reason next to the gap.
  # The verdict is the runner's alone; the kernel lines are evidence. (adbd echoes every shell
  # command into logcat, grep pattern included, so a verdict read off logcat matched itself.)
  $runner = Sh "grep -E 'INSTRUMENTATION_(RESULT|CODE)|STATUS_CODE: -2|Process crashed' $log"
  $kernel = Sh "logcat -d -t '$launchedAt' | grep -v adbd | grep -E 'Killing .*$pkg|lowmemorykiller: Kill .$pkg|Process $pkg .*has died|FATAL EXCEPTION|SIGSEGV|SIGABRT' | tail -6"
  $text = $runner -join "`n"
  $script:verdict = if ($text -match 'INSTRUMENTATION_CODE: -1' -and $text -notmatch 'STATUS_CODE: -2|Process crashed') { 'ok' } elseif ($text -match 'STATUS_CODE: -2|Process crashed|INSTRUMENTATION_CODE: 0') { 'crashed or failed' } else { 'unknown (the runner never reported)' }
  $runner + $kernel | ForEach-Object { Line $_ }
  Line "verdict: process $verdict"
}
function WaitForProcessExit($maxSec) {
  # `am instrument` under nohup returns before the app process exists, so absence means "exited"
  # only after the process has been seen once; never seen within 90 s is its own failure.
  $t0 = Get-Date; $silent = 0; $seen = $false
  while (((Get-Date) - $t0).TotalSeconds -lt $maxSec) {
    # Always a string: `$null -notmatch` yields an empty array, not $true, and the loop never ends.
    $appPid = "$(Sh "pidof $pkg" | Select-Object -Last 1)".Trim()
    if ($appPid -match 'ADB TIMEOUT|error:') { if (++$silent -ge 3) { Say "link dead: three adb calls in a row got no answer"; return $false } }
    elseif ($appPid -match '\d') { $seen = $true; $silent = 0 }
    elseif ($seen) { return $true }
    elseif (((Get-Date) - $t0).TotalSeconds -gt 90) { Say "the app process never appeared in 90 s (instrumentation did not start)"; return $false }
    Sh 'input keyevent KEYCODE_WAKEUP' | Out-Null
    Start-Sleep 5
  }
  return $false
}
function Pull($remote, $local) { Adb @('-s', $Serial, 'pull', $remote, $local) 120 | Out-Null; Test-Path $local }

Sh 'settings put global low_power 0; dumpsys deviceidle disable >/dev/null; settings put system screen_off_timeout 1800000; svc power stayon true' | Out-Null

# --- stages 1-4 ------------------------------------------------------------------------------
Say "stages 1-4: MeasurementPassTest (ANSWER cold/warm, AdviseOnMeal first tap, LOG two and three foods, peak PSS)"
Sh "rm -f $M/katori-measurement-pass.txt" | Out-Null
RunDetached 'io.github.vedant7007.katori.orchestration.MeasurementPassTest' "$M/pass-instrument.log" "-e commit $commit -e claimable $($claimable.ToString().ToLower())"
$finished = WaitForProcessExit 1500
$local = Join-Path $env:TEMP "katori-measurement-pass-$stamp.txt"
$pulled = Pull "$M/katori-measurement-pass.txt" $local
if (-not $finished) { Say "stages 1-4: the process did not exit within 25 minutes (link dropped, or the model stalled: reboot, do not debug)" }
# How the process ended, from the runner and from the kernel: a low-memory kill is SIGKILL and
# leaves no END marker, so the file must carry the reason next to the missing marker.
Line "----- how stages 1-4 ended -----"
Ended "$M/pass-instrument.log"
if ($pulled) {
  $body = Get-Content $local -Encoding utf8
  Line "----- device file: katori-measurement-pass.txt -----"
  $body | ForEach-Object { Line $_ }
  Line "----- end -----"
  foreach ($n in 1, 2, 3, 4) {
    $began = ($body | Select-String "=== STAGE $n BEGIN").Count -gt 0
    $ended = ($body | Select-String "=== STAGE $n END").Count -gt 0
    $stages["$n"] = if ($ended) { 'completed' } elseif ($began) { "NOT completed (began, no END marker; process $verdict)" } elseif ($body -match 'NOT RUN') { 'not run (see the file: model not staged)' } else { "not reached (process $verdict)" }
  }
} else {
  Say "stages 1-4: could not pull the device file; the link is down"
  foreach ($n in 1, 2, 3, 4) { $stages["$n"] = "unknown (file not pulled; process $verdict)" }
}

# --- stage 5: Jacob's clips, cold then warm --------------------------------------------------
Say "stage 5: AsrDeviceTest.b cold (fresh process) then warm (second run)"
Sh "rm -f $M/katori-asr-report.txt" | Out-Null
$asrDone = 0; $asrHow = @()
foreach ($kind in 'cold', 'warm') {
  RunDetached 'io.github.vedant7007.katori.ml.asr.AsrDeviceTest#b_transcribesTheStagedClips' "$M/asr-instrument-$kind.log" ""
  if (-not (WaitForProcessExit 300)) { Say "stage 5 ($kind): process did not exit in 5 minutes" }
  Line "----- how stage 5 ($kind) ended -----"
  Ended "$M/asr-instrument-$kind.log"
  if ($verdict -eq 'ok') { $asrDone++ }
  $asrHow += "$kind $verdict"
}
$asrLocal = Join-Path $env:TEMP "katori-asr-$stamp.txt"
if (Pull "$M/katori-asr-report.txt" $asrLocal) {
  Line "----- device file: katori-asr-report.txt (first run = cold process, second run = warm) -----"
  Get-Content $asrLocal -Encoding utf8 | ForEach-Object { Line $_ }
  Line "----- end -----"
  $stages['5'] = if ($asrDone -eq 2) { 'completed' } else { "NOT completed ($asrDone of 2 runs passed: $($asrHow -join ', '))" }
} else { $stages['5'] = "unknown (file not pulled: $($asrHow -join ', '))" }

Sh 'settings put system screen_off_timeout 120000; svc power stayon false; dumpsys deviceidle enable >/dev/null' | Out-Null
Line ""
Line ("state after: " + ((Sh "cat /proc/meminfo | grep MemAvailable; dumpsys battery | grep -E 'level'; dumpsys thermalservice | grep 'Thermal Status'") | ForEach-Object { $_.Trim() }) -join ' | ')
Line ""
Line "STAGES:"
foreach ($k in $stages.Keys) { Line ("  stage {0}: {1}" -f $k, $stages[$k]) }
Line "claimable: $claimable"
Say "results: $out"
