# THE COLD PHONE, FROM NEVER-SEEN-THIS-APP TO DEMO-READY, AS ONE RUN WITH CHECKS BETWEEN STEPS.
#
# Written 21 Sep 2026 for the iQOO the team collects on the day: a different chip, a different
# skin, no profile, no diary, no report, and a first sixty seconds nobody has run end to end.
# Every number the team holds is the realme's; this script makes the iQOO's starting state true
# and then measures it with the pre-flight rather than assuming it.
#
#   powershell -File tools\cold-phone.ps1                     # the one USB device on the default adb server
#   powershell -File tools\cold-phone.ps1 -Serial <id> -Report  # also seed the lab report (the Beat 3 fallback)
#   powershell -File tools\cold-phone.ps1 -SkipModels           # the models are already staged and verified
#
# Every step prints what it did, its check, and how long it took; the whole run's time is the
# last line, because that number decides whether this happens before the judges arrive or in
# front of them. The steps that need a human thumb (permission dialogs on a skin that refuses
# `pm grant`, the platform voice data, USB debugging itself) are printed as a numbered card at
# the moment they are needed, and the script waits for the check to pass. Log:
# logs\cold-phone-<stamp>.txt. Exit 0 when the pre-flight at the end says READY.
param(
  [string]$Serial = "",
  [switch]$Report,
  [switch]$SkipModels,
  [switch]$SkipInstall,
  # Dry runs only: the voice-data card needs a human and a network; an emulator is not the phone.
  [switch]$SkipVoices,
  [int]$WaitForThumbSec = 300
)
$ErrorActionPreference = 'Continue'
$adb  = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pkg  = 'io.github.vedant7007.katori'
$runner = "$pkg.test/androidx.test.runner.AndroidJUnitRunner"
$M    = "/sdcard/Android/media/$pkg"
New-Item -ItemType Directory -Force (Join-Path $repo 'logs') | Out-Null
$out  = Join-Path $repo ("logs\cold-phone-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".txt")
$t00 = Get-Date
function Line($s) { Add-Content -Encoding utf8 $out $s; Write-Host $s }
function Say($s) { Line ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $s) }
function Fail($why) { Say "STOPPED: $why"; Line ("total so far: {0:n0} s" -f ((Get-Date) - $t00).TotalSeconds); exit 2 }
function Adb([string[]]$argv, [int]$timeoutSec) {
  $quoted = ($argv | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
  $tmp = [IO.Path]::GetTempFileName()
  $p = Start-Process -FilePath $adb -ArgumentList $quoted -NoNewWindow -PassThru -RedirectStandardOutput $tmp -RedirectStandardError "$tmp.err"
  if ($p.WaitForExit($timeoutSec * 1000)) { $r = @(Get-Content $tmp -ErrorAction SilentlyContinue) + @(Get-Content "$tmp.err" -ErrorAction SilentlyContinue) } else { try { $p.Kill() } catch {}; $r = @("ADB TIMEOUT after ${timeoutSec}s: $quoted") }
  Remove-Item $tmp, "$tmp.err" -Force -ErrorAction SilentlyContinue
  $r | ForEach-Object { "$_" }
}
function Sh($cmd) { Adb @('-s', $Serial, 'shell', $cmd) 60 }
function Step($n, $title) { $script:stepStart = Get-Date; Line ""; Say "STEP $n. $title" }
function Done($what) { Say ("  done: {0} ({1:n0} s)" -f $what, ((Get-Date) - $script:stepStart).TotalSeconds) }
function Card($lines) { Line ""; Line "  ===== A HUMAN WITH A THUMB, NOW ====="; $i = 1; foreach ($l in $lines) { Line ("  {0}. {1}" -f $i, $l); $i++ }; Line "  =====================================" }
function WaitUntil($what, $test) {
  $t = Get-Date
  while (((Get-Date) - $t).TotalSeconds -lt $WaitForThumbSec) { if (& $test) { return $true }; Start-Sleep 3 }
  Fail "waited $WaitForThumbSec s for: $what"
}

Line "COLD PHONE  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
Line "commit:  $(& git -C $repo rev-parse HEAD 2>$null)"

# --- 0. the link ---------------------------------------------------------------------------------
Step 0 'the link: USB debugging authorised, default adb server, one device'
$servers = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'fork-server server' }
if (@($servers | Where-Object { $_.CommandLine -notmatch 'tcp:5037' }).Count -gt 0) { Fail 'a second adb server is running (5bd5458); stop it first' }
$devices = @((& $adb devices 2>&1) | ForEach-Object { "$_" } | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | ForEach-Object { $f = $_ -split '\s+'; [pscustomobject]@{ Serial = $f[0]; State = $f[1] } })
if (@($devices | Where-Object { $_.State -eq 'unauthorized' }).Count -gt 0) {
  Card @('On the phone: "Allow USB debugging?" -> tick "Always allow from this computer" -> Allow.')
  WaitUntil 'USB debugging authorised' { @((& $adb devices 2>&1) | Select-String 'unauthorized').Count -eq 0 } | Out-Null
  $devices = @((& $adb devices 2>&1) | ForEach-Object { "$_" } | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | ForEach-Object { $f = $_ -split '\s+'; [pscustomobject]@{ Serial = $f[0]; State = $f[1] } })
}
$usb = @($devices | Where-Object { $_.State -eq 'device' -and $_.Serial -notmatch '^emulator-|:\d+$' })
if (-not $Serial) { if ($usb.Count -ne 1) { Fail "expected one USB device on the default server, found $($usb.Count)" }; $Serial = $usb[0].Serial }
$model = (Sh 'getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.hardware') -join ' / '
Line "device:  $Serial  $model"
$sdk = "$(Sh 'getprop ro.build.version.sdk' | Select-Object -Last 1)".Trim()
if ([int]$sdk -lt 29) { Say "  NOTE: API $sdk is below 29; the Devanagari transliteration is off on this phone (android.icu needs 29) and Beat 1 in Hindi is not proven to extract" }
$abi = "$(Sh 'getprop ro.product.cpu.abi' | Select-Object -Last 1)".Trim()
if ($abi -ne 'arm64-v8a' -and $Serial -notmatch '^emulator-|:\d+$') { Fail "this build is arm64 only; the phone reports $abi" }
if ($Serial -match '^emulator-|:\d+$') { Say '  NOTE: an emulator: this run proves the script, not the phone' }
Done "link to $Serial, $model"

# --- 1. the voice data, before the radios go off -------------------------------------------------
Step 1 'the platform voice data (needs a network, so before airplane mode)'
if ($SkipVoices) { Done 'skipped (-SkipVoices)' } else {
  Card @(
    'Settings -> System -> Languages & input -> Text-to-speech output -> Speech Services by Google -> the gear -> Install voice data.',
    'Install "Hindi (India)" and "English (India)". Wait until both say Installed.',
    'Then press Enter here.'
  )
  Read-Host '  (press Enter when the voices are installed)' | Out-Null
  Done 'voice data, by hand (the pre-demo checklist row 1a verifies it with TtsVoiceProbeTest)'
}

# --- 2. install -----------------------------------------------------------------------------------
Step 2 'install the demo build and its test APK'
if (-not $SkipInstall) {
  foreach ($apk in @((Join-Path $repo 'app\build\outputs\apk\demo\debug\app-demo-debug.apk'), (Join-Path $repo 'app\build\outputs\apk\androidTest\demo\debug\app-demo-debug-androidTest.apk'))) {
    if (-not (Test-Path $apk)) { Fail "no APK at $apk (assembleDemoDebug and assembleDemoDebugAndroidTest first)" }
    $r = ''
    foreach ($try in 1..3) {
      $r = (Adb @('-s', $Serial, 'install', '-r', '-t', '-g', $apk) 300 | Select-Object -Last 1)
      Say "  install $(Split-Path $apk -Leaf) (try $try): $r"
      if ($r -match 'Success') { break }
      Adb @('-s', $Serial, 'wait-for-device') 60 | Out-Null; Start-Sleep 3
    }
    if ($r -notmatch 'Success') {
      if ($r -match 'INSTALL_FAILED_USER_RESTRICTED|Install via USB|VERIFICATION') { Card @('Developer options -> "Install via USB" ON, and "USB debugging (Security settings)" ON (vivo/iQOO ask for a sign-in for the second).', 'Then re-run this script.') }
      Fail "install failed: $r"
    }
  }
}
$installed = "$(Sh "dumpsys package $pkg | grep lastUpdateTime" | Select-Object -Last 1)".Trim()
if (-not $installed) { Fail 'the package is not on the phone' }
Done "installed: $installed"

# --- 3. permissions -------------------------------------------------------------------------------
Step 3 'permissions: microphone and camera'
function Granted($perm) { "$(Sh "dumpsys package $pkg | grep '$perm' | grep -c 'granted=true'" | Select-Object -Last 1)".Trim() -ne '0' }
foreach ($perm in @('android.permission.RECORD_AUDIO', 'android.permission.CAMERA')) {
  if (-not (Granted $perm)) { Sh "pm grant $pkg $perm" | Out-Null }
}
if (-not ((Granted 'android.permission.RECORD_AUDIO') -and (Granted 'android.permission.CAMERA'))) {
  Sh "am start -n $pkg/.ui.MainActivity" | Out-Null
  Card @(
    'The skin refused `pm grant` (ColorOS does; the iQOO may). On the phone, in IN2FIT:',
    'press and hold the microphone button once -> "Allow" -> let go.',
    'Scan report tab -> the camera asks -> "Allow".',
    'This script continues the moment both show granted.'
  )
  WaitUntil 'RECORD_AUDIO and CAMERA granted' { (Granted 'android.permission.RECORD_AUDIO') -and (Granted 'android.permission.CAMERA') } | Out-Null
}
Done 'RECORD_AUDIO and CAMERA granted'

# --- 4. the models --------------------------------------------------------------------------------
Step 4 'the models on the phone: LLM, Hindi and English recognisers, the voices'
if (-not $SkipModels) {
  Say '  tools\stage-models.ps1 (chunked, resumable, sha256 on the device); this is the long step'
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'stage-models.ps1') -Serial $Serial 2>&1 | ForEach-Object { Line "    $_" }
}
$need = @("$M/models/qwen2.5-1.5b-instruct-q4_k_m.gguf", "$M/models/asr/hi/model.int8.onnx", "$M/models/asr/hi/tokens.txt", "$M/models/asr/en/model.int8.onnx", "$M/models/asr/en/tokens.txt")
$missing = @($need | Where-Object { "$(Sh "test -s $_ && echo yes" | Select-Object -Last 1)".Trim() -ne 'yes' })
if ($missing.Count -gt 0) { Fail "models missing or empty on the phone: $($missing -join ', ')" }
Done "the five files are present and non-empty"

# --- 5. the seed: profile, language, the week, optionally the report -------------------------------
Step 5 'the seed, through the app''s own stores (DemoSeedTest into the app''s database)'
Sh "am force-stop $pkg; rm -f $M/katori-seed-report.txt" | Out-Null
# -Report seeds the lab report BY BEAT 3'S ROUTE (Arjun, 21 Sep 13:00): DemoSeedTest draws it as a
# PDF, renders it, reads it with ML Kit on this phone and saves only what was read; the PDF is then
# put in Downloads so the presenter opens the same file through the picker on stage.
$rep = if ($Report) { 'pdf' } else { 'false' }
Adb @('-s', $Serial, 'shell', "am instrument -w -r -e report $rep -e class io.github.vedant7007.katori.orchestration.DemoSeedTest $runner") 600 | Where-Object { $_ -match 'INSTRUMENTATION_(RESULT|CODE)|STATUS_CODE: -2' } | ForEach-Object { Line "    $_" }
$seed = Sh "cat $M/katori-seed-report.txt"
$seed | ForEach-Object { Line "    $_" }
if (($seed -join "`n") -notmatch 'SEED END') { Fail 'the seed did not finish; read the lines above' }
if ($Report) {
  Sh "mkdir -p /sdcard/Download; cp $M/in2fit-lab-report.pdf /sdcard/Download/in2fit-lab-report.pdf" | Out-Null
  $pdfSize = "$(Sh 'stat -c %s /sdcard/Download/in2fit-lab-report.pdf' | Select-Object -Last 1)".Trim()
  if (-not $pdfSize -or $pdfSize -eq '0') { Fail 'the report PDF is not in Downloads; the picker will have nothing to open on stage' }
  Line "    the report PDF is in Downloads ($pdfSize bytes): Scan -> Open a PDF report -> Downloads -> in2fit-lab-report.pdf"
}
Done ('profile (speech hi), six meals over six days, three spoken in Hindi and three typed' + $(if ($Report) { ', the report by the PDF route' } else { ', no report' }))

# --- 6. airplane mode -------------------------------------------------------------------------------
Step 6 'radios off'
Sh 'cmd connectivity airplane-mode enable; svc wifi disable; svc bluetooth disable' | Out-Null
Start-Sleep 2
$air = "$(Sh 'settings get global airplane_mode_on' | Select-Object -Last 1)".Trim(); $wifi = "$(Sh 'settings get global wifi_on' | Select-Object -Last 1)".Trim()
if ($air -ne '1') { Card @('Quick settings -> Airplane mode ON, Wi-Fi OFF, Bluetooth OFF.'); WaitUntil 'airplane mode' { "$(Sh 'settings get global airplane_mode_on' | Select-Object -Last 1)".Trim() -eq '1' } | Out-Null }
Done "airplane_mode_on=$air wifi_on=$wifi"

# --- 7. warm --------------------------------------------------------------------------------------
Step 7 'the model resident and warm (the app''s own 0032 warm-up at launch)'
Sh "logcat -c; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; svc power stayon usb; am start -n $pkg/.ui.MainActivity" | Out-Null
$warm = $null
WaitUntil 'the katori-warmup line' { $script:warm = (Sh "logcat -d | grep -E 'katori-warmup' | tail -1"); "$warm" -match 'total' } | Out-Null
Line "    $warm"
Done 'warm-up reported'

# --- 8. pre-flight ----------------------------------------------------------------------------------
Step 8 'pre-flight'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'preflight.ps1') -Serial $Serial 2>&1 | ForEach-Object { Line "    $_" }
$ready = ($LASTEXITCODE -eq 0)
Done $(if ($ready) { 'READY' } else { 'WAIT (read the pre-flight above; a fresh boot needs ten quiet minutes)' })

Line ""
Line ("TOTAL: {0:n0} s ({1:n1} min), READY={2}" -f ((Get-Date) - $t00).TotalSeconds, ((Get-Date) - $t00).TotalMinutes, $ready)
Line "log: $out"
if ($ready) { exit 0 } else { exit 1 }
