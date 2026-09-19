# Downloads model weights on Windows, which has unthrottled internet. The same job inside the
# Linux VM ran at roughly 8 KB/s through its proxy, which would have taken days for 1.7 GB.
#
# Only models whose licence permits redistribution in a shipped app are listed.
# Deliberately EXCLUDED, see docs/decisions/0005:
#   csukuangfj/sherpa-onnx-whisper-*  no licence stated anywhere, so no grant
#   facebook + willwade mms-tts-*     cc-by-nc-4.0, non-commercial
#   piper hi_IN-rohan                 custom IITM licence, unread
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
  @{ u="$hf/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf";                 p='llm\qwen2.5-1.5b-instruct-q4_k_m.gguf' }
)

foreach ($it in $items) {
  $out = Join-Path $dest $it.p
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

Log "--- sha256 ---"
Get-ChildItem $dest -Recurse -File | ForEach-Object {
  $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
  Log ("{0}  {1}  {2} bytes" -f $h, $_.FullName.Replace($dest,'').TrimStart('\'), $_.Length)
}
$sum = (Get-ChildItem $dest -Recurse -File | Measure-Object -Property Length -Sum).Sum
Log ("total downloaded: {0} MB" -f [math]::Round($sum/1MB,1))
$d = Get-PSDrive C
Log ("disk C: free {0} GB of {1} GB" -f [math]::Round($d.Free/1GB,1), [math]::Round(($d.Free+$d.Used)/1GB,1))
Log "=== DONE ==="
