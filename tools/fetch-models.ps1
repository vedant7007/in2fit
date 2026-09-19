# Downloads model weights on Windows, which has unthrottled internet. The same job inside the
# Linux VM ran at roughly 8 KB/s through its proxy, which would have taken days for 1.7 GB.
#
# Only models whose licence permits redistribution in a shipped app are listed.
# Deliberately EXCLUDED, see docs/decisions/0005:
#   csukuangfj/sherpa-onnx-whisper-*  no licence stated anywhere, so no grant
#   facebook + willwade mms-tts-*     cc-by-nc-4.0, non-commercial
#   piper hi_IN-rohan                 custom IITM licence, unread
#
# Items with an `h` carry the sha256 published for them (decision records 0019 and the ASR
# record); a downloaded or already-present file that does not match is DELETED and the script
# exits 1, so a truncated download cannot sit on disk looking complete. Items with an `abs`
# land at that path under the repo root instead of under data-sources\models: the sherpa-onnx
# AAR goes to app\libs, where app/build.gradle.kts fails preBuild by name if it is absent.
$ErrorActionPreference = 'Continue'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dest = Join-Path $root 'data-sources\models'
$log  = Join-Path $root 'logs\model-fetch.log'

function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}
Set-Content -Path $log -Value "=== model fetch (windows) $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$hf = 'https://huggingface.co'
$items = @(
  @{ u="$hf/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/te/model.int8.onnx"; p='asr\indicconformer\te\model.int8.onnx' },
  @{ u="$hf/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/hi/model.int8.onnx"; p='asr\indicconformer\hi\model.int8.onnx' },
  @{ u="$hf/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/en/model.int8.onnx"; p='asr\indicconformer\en\model.int8.onnx' },
  @{ u="$hf/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/tokens.txt";         p='asr\indicconformer\tokens.txt' },
  @{ u="$hf/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/en/tokens.txt";      p='asr\indicconformer\en\tokens.txt' },
  @{ u="$hf/rhasspy/piper-voices/resolve/main/te/te_IN/padmavathi/medium/te_IN-padmavathi-medium.onnx";      p='tts\piper\te_IN-padmavathi-medium.onnx' },
  @{ u="$hf/rhasspy/piper-voices/resolve/main/te/te_IN/padmavathi/medium/te_IN-padmavathi-medium.onnx.json"; p='tts\piper\te_IN-padmavathi-medium.onnx.json' },
  @{ u="$hf/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf";                 p='llm\qwen2.5-1.5b-instruct-q4_k_m.gguf' },
  # Hindi voice. CC-BY-NC-SA-4.0, in the non-commercial register in 0005. Not on the demo path.
  @{ u="$hf/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx";      p='tts\piper\hi_IN-pratham-medium.onnx';
     h='169964b0871667f6793416d4b35e97357a68ba1ad01df8580c28048989ee7693' },
  @{ u="$hf/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx.json"; p='tts\piper\hi_IN-pratham-medium.onnx.json' },
  # espeak-ng data, the phonemiser's tables. GPL-3.0-or-later (0005). Trimmed to 1.07 MB and
  # committed as an asset by tools/stamp_piper_voice.py espeak; this is the upstream source.
  @{ u='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2'; abs='data-sources\espeak-ng-data-src\espeak-ng-data.tar.bz2';
     h='4135ccf82e1f40613491c0874d4945ae9e9c7840933d8e25a6f9e003d9ebf533' },
  # The ASR and TTS runtime. Official release AAR, static-link variant (ONNX Runtime inside, no
  # exported Ort* symbol). Gitignored; app/build.gradle.kts refuses to build without it.
  @{ u='https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar'; abs='app\libs\sherpa-onnx-static-link-onnxruntime-1.13.8.aar';
     h='b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471' }
)

foreach ($it in $items) {
  if ($it.abs) { $it.p = $it.abs }
  $out = if ($it.abs) { Join-Path $root $it.abs } else { Join-Path $dest $it.p }
  New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
  if ((Test-Path $out) -and ((Get-Item $out).Length -gt 0)) {
    Log ("SKIP  {0} (already {1} bytes)" -f $it.p, (Get-Item $out).Length); continue
  }
  Log ("GET   {0}" -f $it.p)
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  $ok = $false
  try { Start-BitsTransfer -Source $it.u -Destination $out -ErrorAction Stop; $ok = $true }
  catch {
    Log ("BITS failed: {0}; falling back to Invoke-WebRequest" -f $_.Exception.Message)
    try { Invoke-WebRequest -Uri $it.u -OutFile $out -MaximumRedirection 10 -ErrorAction Stop; $ok = $true }
    catch { Log ("FAIL  {0} :: {1}" -f $it.p, $_.Exception.Message) }
  }
  $sw.Stop()
  if ($ok) {
    $len = (Get-Item $out).Length
    $secs = [math]::Max($sw.Elapsed.TotalSeconds, 1)
    Log ("OK    {0}  {1} bytes  {2}s  {3} KB/s" -f $it.p, $len, [math]::Round($sw.Elapsed.TotalSeconds,1), [math]::Round($len/1KB/$secs,0))
  }
}

Log "--- sha256, checked against the published hash where one is recorded ---"
$bad = 0
foreach ($it in $items) {
  if (-not $it.h) { continue }
  $out = if ($it.abs) { Join-Path $root $it.abs } else { Join-Path $dest $it.p }
  if (-not (Test-Path $out)) { Log ("MISSING  {0}" -f $it.p); $bad++; continue }
  $h = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
  if ($h -eq $it.h) { Log ("MATCH    {0}" -f $it.p) }
  else {
    Log ("MISMATCH {0}: got {1}, expected {2}. Deleted; run again." -f $it.p, $h, $it.h)
    Remove-Item $out -Force; $bad++
  }
}

Log "--- sha256 of everything under data-sources\models ---"
Get-ChildItem $dest -Recurse -File | ForEach-Object {
  $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
  Log ("{0}  {1}  {2} bytes" -f $h, $_.FullName.Replace($dest,'').TrimStart('\'), $_.Length)
}
$sum = (Get-ChildItem $dest -Recurse -File | Measure-Object -Property Length -Sum).Sum
Log ("total downloaded: {0} MB" -f [math]::Round($sum/1MB,1))
$d = Get-PSDrive C
Log ("disk C: free {0} GB of {1} GB" -f [math]::Round($d.Free/1GB,1), [math]::Round(($d.Free+$d.Used)/1GB,1))
if ($bad -gt 0) { Log "=== FAILED: $bad file(s) missing or with the wrong hash ==="; exit 1 }
Log "=== DONE ==="
