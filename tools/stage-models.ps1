# Stages the models onto a phone over adb, in a way that survives a USB link that drops.
#
#   powershell -File tools\stage-models.ps1                 # every file the demo needs
#   powershell -File tools\stage-models.ps1 -Serial <id>    # a specific device
#   powershell -File tools\stage-models.ps1 -Only asr,llm   # a subset: llm, asr, tts
#
# WHY IT EXISTS. On the 26th somebody at a table with a laptop, a cable and a handset nobody has
# connected before moves 1.6 GB under time pressure, and that somebody may not be Rao. Decision
# 0012 records what a plain `adb push` of the 1.04 GB model did on the realme: died at 18.3 s and
# at 43.4 s with "no response: Broken pipe", because that USB link re-enumerates every few
# minutes. 0012's rule: bulk transfers go in chunks with per-chunk retry and an on-device
# sha256sum at the end. This is that rule as a script, written from 0012's prose by Nila on
# 20 Sep 2026 and NOT YET RUN ON A PHONE.
#
# RAO VERIFIES THIS INSIDE HIS NEXT CABLE SESSION and corrects it; the three numbers marked
# CONFIRM below are guesses until he has. A script he corrects is faster than one he dictates.
#
# WHAT IT DOES, per file: skips it if the device already holds a file with the right sha256;
# otherwise splits it into chunks locally, pushes each chunk with retries, concatenates on the
# device, checks the sha256 on the device against the local one, and only then deletes the
# chunks. A wrong hash deletes the assembled file and reports; nothing half-written is left
# looking whole. Every step is logged to logs\stage-models.log with the time it took, so the
# number in the pre-demo checklist is a stopwatch, not a guess.
#
# LAYOUT ON THE DEVICE is the one the app's loaders read, taken from the code, not from memory:
#   /sdcard/Android/media/<pkg>/models/
#     qwen2.5-1.5b-instruct-q4_k_m.gguf          LlmModels.kt
#     asr/<lang>/model.int8.onnx + tokens.txt    AsrModels.kt (te and hi share the root tokens.txt; en has its own)
#     tts/<voice>/model.onnx + tokens.txt        PiperVoice.kt (tokens.txt from tools/stamp_piper_voice.py)
# The demo language is Hindi with English answers (0022, 0019 addendum 9), so the default set is
# the LLM, ASR hi and en, TTS en_GB-cori-medium and hi_IN-pratham-medium. Telugu speech is
# post-battle and stays off the phone unless -Only asks for it.
param(
  [string]$Serial = '',
  [string[]]$Only = @('llm', 'asr', 'tts'),
  [string]$Package = 'io.github.vedant7007.katori',
  [int]$ChunkMB = 64,        # CONFIRM (Rao): 64 MB chunks means ~17 pushes for the LLM; the realme died at 18 s and 43 s into one push, so a chunk should take well under 18 s at the measured 6.3 MB/s
  [int]$Retries = 5,         # CONFIRM (Rao): per-chunk attempts before giving up
  [int]$RetryWaitSeconds = 3 # CONFIRM (Rao): the link re-enumerates; a pause lets `adb devices` see it again
)
$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$log  = Join-Path $root 'logs\stage-models.log'
New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
function Log($m) { $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m; Write-Host $line; Add-Content -Path $log -Value $line -Encoding UTF8 }
Add-Content -Path $log -Value "=== stage models $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Log "FATAL: adb not found at $adb"; exit 2 }
$A = @()
if ($Serial) { $A = @('-s', $Serial) }

# --- the device must be there, authorised, and have room ---------------------------------------
$devices = (& $adb devices) -split "`n" | Where-Object { $_ -match "`tdevice$" }
if (-not $devices) { Log "FATAL: no authorised device. 'adb devices' must list one as 'device', not 'unauthorized' or 'offline'. On a vivo/iQOO ROM check 'Install via USB' and 'USB debugging (Security settings)' too (countdown.md)."; exit 2 }
Log ("device: {0}" -f ($devices -join '; '))
$dest = "/sdcard/Android/media/$Package/models"
& $adb @A shell mkdir -p $dest | Out-Null
$df = (& $adb @A shell df -h /sdcard 2>&1 | Select-Object -Last 1)
Log "free space on /sdcard: $df   (the set is 1.6 GB; 0019 says 2 GB free before starting)"

# --- what goes, from data-sources, under the loaders' names --------------------------------
$src = Join-Path $root 'data-sources\models'
$files = @()
if ($Only -contains 'llm') {
  $files += @{ local = "$src\llm\qwen2.5-1.5b-instruct-q4_k_m.gguf"; remote = "$dest/qwen2.5-1.5b-instruct-q4_k_m.gguf" }
}
if ($Only -contains 'asr') {
  foreach ($lang in 'hi', 'en') {
    $tokens = if ($lang -eq 'en') { "$src\asr\indicconformer\en\tokens.txt" } else { "$src\asr\indicconformer\tokens.txt" }
    $files += @{ local = "$src\asr\indicconformer\$lang\model.int8.onnx"; remote = "$dest/asr/$lang/model.int8.onnx" }
    $files += @{ local = $tokens;                                          remote = "$dest/asr/$lang/tokens.txt" }
  }
}
if ($Only -contains 'te') {   # Telugu speech, post-battle; only on request
  $files += @{ local = "$src\asr\indicconformer\te\model.int8.onnx"; remote = "$dest/asr/te/model.int8.onnx" }
  $files += @{ local = "$src\asr\indicconformer\tokens.txt";         remote = "$dest/asr/te/tokens.txt" }
}
if ($Only -contains 'tts') {
  foreach ($voice in 'en_GB-cori-medium', 'hi_IN-pratham-medium') {
    $files += @{ local = "$src\tts\sherpa\$voice\model.onnx"; remote = "$dest/tts/$voice/model.onnx" }
    $files += @{ local = "$src\tts\sherpa\$voice\tokens.txt"; remote = "$dest/tts/$voice/tokens.txt" }
  }
}

function LocalSha($p) { (Get-FileHash $p -Algorithm SHA256).Hash.ToLower() }
function RemoteSha($r) {
  $out = (& $adb @A shell "sha256sum '$r' 2>/dev/null" 2>&1 | Select-Object -First 1)
  if ($out -match '^([0-9a-f]{64})') { $Matches[1] } else { '' }
}
function PushWithRetry($local, $remote) {
  for ($try = 1; $try -le $Retries; $try++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $out = & $adb @A push $local $remote 2>&1 | Select-Object -Last 1
    $sw.Stop()
    if ($LASTEXITCODE -eq 0 -and "$out" -notmatch 'error|Broken pipe|failed') { return $sw.Elapsed.TotalSeconds }
    Log ("    attempt {0}/{1} failed after {2:N1}s: {3}" -f $try, $Retries, $sw.Elapsed.TotalSeconds, $out)
    Start-Sleep -Seconds $RetryWaitSeconds
    & $adb @A wait-for-device 2>&1 | Out-Null
  }
  return -1
}

$failed = 0
$total = [System.Diagnostics.Stopwatch]::StartNew()
foreach ($f in $files) {
  if (-not (Test-Path $f.local)) { Log "MISSING LOCALLY: $($f.local)  (run tools\4-fetch-models.bat; for TTS also tools\stamp_piper_voice.py)"; $failed++; continue }
  $size = (Get-Item $f.local).Length
  $want = LocalSha $f.local
  $have = RemoteSha $f.remote
  if ($have -eq $want) { Log ("SKIP  {0}  already on the device with the right sha256" -f $f.remote); continue }
  & $adb @A shell mkdir -p (Split-Path $f.remote -Parent).Replace('\', '/') | Out-Null
  $chunk = $ChunkMB * 1MB
  if ($size -le $chunk) {
    Log ("PUSH  {0}  {1:N0} B, one piece" -f $f.remote, $size)
    $secs = PushWithRetry $f.local $f.remote
    if ($secs -lt 0) { Log "FAIL  $($f.remote): gave up after $Retries attempts"; $failed++; continue }
    Log ("      {0:N1}s  {1:N1} MB/s" -f $secs, ($size / 1MB / [math]::Max($secs, 0.1)))
  } else {
    $n = [math]::Ceiling($size / $chunk)
    Log ("PUSH  {0}  {1:N0} B in {2} chunks of {3} MB" -f $f.remote, $size, $n, $ChunkMB)
    $tmp = Join-Path $env:TEMP ("stage-" + [IO.Path]::GetFileName($f.local))
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $in = [IO.File]::OpenRead($f.local)
    $buf = New-Object byte[] $chunk
    $ok = $true
    $remoteParts = @()
    for ($i = 0; $i -lt $n; $i++) {
      $read = $in.Read($buf, 0, $chunk)
      $part = Join-Path $tmp ("part{0:D3}" -f $i)
      $fs = [IO.File]::Create($part); $fs.Write($buf, 0, $read); $fs.Close()
      $rpart = "$($f.remote).part{0:D3}" -f $i
      $secs = PushWithRetry $part $rpart
      Remove-Item $part -Force
      if ($secs -lt 0) { Log "FAIL  chunk $i of $($f.remote): gave up after $Retries attempts"; $ok = $false; break }
      Log ("      chunk {0}/{1}  {2:N1}s  {3:N1} MB/s" -f ($i + 1), $n, $secs, ($read / 1MB / [math]::Max($secs, 0.1)))
      $remoteParts += $rpart
    }
    $in.Close()
    if (-not $ok) { $failed++; continue }
    Log "      assembling on the device"
    & $adb @A shell "cat $($f.remote).part* > '$($f.remote)'" 2>&1 | ForEach-Object { Log "      $_" }
  }
  $got = RemoteSha $f.remote
  if ($got -eq $want) {
    & $adb @A shell "rm -f $($f.remote).part*" 2>&1 | Out-Null
    Log ("OK    {0}  sha256 {1}" -f $f.remote, $want.Substring(0, 16))
  } else {
    & $adb @A shell "rm -f '$($f.remote)' $($f.remote).part*" 2>&1 | Out-Null
    Log ("BAD   {0}  device sha256 {1} != local {2}; assembled file deleted, run again" -f $f.remote, $got, $want.Substring(0, 16))
    $failed++
  }
}
$total.Stop()
Log "--- what the device holds under $dest ---"
& $adb @A shell "find $dest -type f -exec ls -l {} \;" 2>&1 | ForEach-Object { Log "  $_" }
Log ("total {0:N0}s; {1} file(s) failed" -f $total.Elapsed.TotalSeconds, $failed)
if ($failed -gt 0) { Log "=== FAILED ==="; exit 1 }
Log "=== DONE: every file present with the right sha256. The app finds them on next launch; read the pre-flight card to confirm. ==="
