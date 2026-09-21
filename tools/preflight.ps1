# PRE-FLIGHT: is the phone in a state to be measured, or demoed, right now? One command, one
# verdict. Written 21 Sep 2026 after a warm ANSWER took 66 s on a phone that was seven minutes
# past a reboot with its indexer on two cores (COORDINATION, Rao 08:40): the demo's speed depends
# on what Android happens to be doing, and nobody at a borrowed table will know until the judges
# are watching. Run it before every measurement, so its numbers accumulate beside the timings and
# "READY" becomes a measured shape rather than a guess. The thresholds below are first guesses
# marked as such; move them when the log says so, and say why in the commit.
#
#   powershell -File tools\preflight.ps1                # the one USB device on the default adb server
#   powershell -File tools\preflight.ps1 -Serial <id>
#
# Prints every raw value with the threshold beside it, writes the same to logs\preflight-<stamp>.txt,
# and ends with one line: READY, or WAIT: <reasons>. Exit 0 READY, 1 WAIT, 2 no device.
# The demo-day procedure that uses it is in docs/demo/run-of-show.md ("Reboot, wait, check, demo").
param([string]$Serial = "")
$ErrorActionPreference = 'Continue'
$adb  = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pkg  = 'io.github.vedant7007.katori'
New-Item -ItemType Directory -Force (Join-Path $repo 'logs') | Out-Null
$out  = Join-Path $repo ("logs\preflight-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".txt")
$reasons = New-Object System.Collections.Generic.List[string]
function Line($s) { Add-Content -Encoding utf8 $out $s; Write-Host $s }
function Check($name, $value, $ok, $rule) { $mark = if ($ok) { 'ok  ' } else { 'WAIT' }; Line ("  {0} {1,-28} {2,-34} ({3})" -f $mark, $name, $value, $rule); if (-not $ok) { $reasons.Add("$name $value") } }
function Adb([string[]]$argv, [int]$timeoutSec) {
  $quoted = ($argv | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
  $tmp = [IO.Path]::GetTempFileName()
  $p = Start-Process -FilePath $adb -ArgumentList $quoted -NoNewWindow -PassThru -RedirectStandardOutput $tmp -RedirectStandardError "$tmp.err"
  if ($p.WaitForExit($timeoutSec * 1000)) { $r = @(Get-Content $tmp -ErrorAction SilentlyContinue) + @(Get-Content "$tmp.err" -ErrorAction SilentlyContinue) } else { try { $p.Kill() } catch {}; $r = @("ADB TIMEOUT after ${timeoutSec}s") }
  Remove-Item $tmp, "$tmp.err" -Force -ErrorAction SilentlyContinue
  $r | ForEach-Object { "$_" }
}
function Sh($cmd) { Adb @('-s', $Serial, 'shell', $cmd) 30 }
function Num($text) { $t = "$text".Trim(); if ($t -match '^-?\d+(\.\d+)?$') { [double]$t } else { $null } }
function First($lines, $regex) { $m = $lines | Select-String $regex | Select-Object -First 1; if ($m) { $m.Matches.Groups[1].Value } else { '' } }

Line "PRE-FLIGHT  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"

# --- the link: default server only, one USB device --------------------------------------------
$servers = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'fork-server server' }
$other = @($servers | Where-Object { $_.CommandLine -notmatch 'tcp:5037' })
$devices = @((& $adb devices 2>&1) | ForEach-Object { "$_" } | Select-Object -Skip 1 | Where-Object { $_ -match '\S' } | ForEach-Object { $f = $_ -split '\s+'; [pscustomobject]@{ Serial = $f[0]; State = $f[1] } })
$usb = @($devices | Where-Object { $_.State -eq 'device' -and $_.Serial -notmatch '^emulator-|:\d+$' })
if (-not $Serial) { if ($usb.Count -ne 1) { Line "NO DEVICE: expected one USB device on the default adb server, found $($usb.Count)"; exit 2 }; $Serial = $usb[0].Serial }
Line "device:  $Serial  $((Sh 'getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release') -join ' / ')"
Check 'adb: default server owns it' ((@($devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq 'device' }).Count -eq 1)) ((@($devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq 'device' }).Count -eq 1)) 'port 5037 lists it as device'
Check 'adb: no second server' ($other.Count -eq 0) ($other.Count -eq 0) "5bd5458; found $($other.Count)"

# --- radios -----------------------------------------------------------------------------------
$air  = "$(Sh 'settings get global airplane_mode_on' | Select-Object -Last 1)".Trim()
$wifi = "$(Sh 'settings get global wifi_on' | Select-Object -Last 1)".Trim()
Check 'airplane mode' $air ($air -eq '1') 'must be 1'
Check 'wifi_on' $wifi ($wifi -eq '0') 'must be 0 (3 = on under airplane mode)'

# --- time since boot ----------------------------------------------------------------------------
$up = Num (Sh 'cut -d" " -f1 /proc/uptime' | Select-Object -Last 1)
$upMin = if ($up -ne $null) { [math]::Round($up / 60, 1) } else { 'unreadable' }
Check 'minutes since boot' $upMin (($up -ne $null) -and ($up -ge 600) -and ($up -le 6 * 3600)) 'first guess: 10 min to 6 h (indexing before, the 0 %-CPU stall after idling)'

# --- load ---------------------------------------------------------------------------------------
$la = "$(Sh 'cat /proc/loadavg' | Select-Object -Last 1)".Trim() -split '\s+'
$load1 = Num $la[0]; $runnable = if ($la.Count -ge 4) { Num (($la[3] -split '/')[0]) } else { $null }
Check 'loadavg 1-min' $(if ($load1 -ne $null) { $load1 } else { 'unreadable' }) (($load1 -ne $null) -and ($load1 -lt 4.0)) 'first guess: below 4.0 (the 66 s turn ran at 20; idle settles to 1-3)'
Check 'runnable now' $(if ($runnable -ne $null) { $runnable } else { 'unreadable' }) (($runnable -ne $null) -and ($runnable -le 3)) 'first guess: at most 3, one of them this shell'

# --- thermal ----------------------------------------------------------------------------------
$thermal = Sh "dumpsys thermalservice"
$status = First $thermal 'Thermal Status: (\d+)'
$cpuT = Num (First $thermal 'mValue=([0-9.]+), mType=0, mName=CPU')
$skinT = First $thermal 'mValue=([0-9.]+), mType=3, mName=SKIN'
Check 'thermal status' $(if ($status -ne '') { $status } else { 'unreadable' }) ($status -eq '0') '0 = none; 3 = SEVERE cost the three-food LOG 2x'
Check 'CPU temperature C' $(if ($cpuT -ne $null) { $cpuT } else { 'unreadable' }) (($cpuT -ne $null) -and ($cpuT -lt 50)) 'first guess: below 50 (56 after the slow run)'
Line ("       skin temperature C         {0}" -f $skinT)

# --- what else is running -----------------------------------------------------------------------
$top = Sh 'top -b -n 1 | tail -n +5'
$hogs = @($top | ForEach-Object { $f = ($_ -replace '^\s+', '') -split '\s+'; $c = if ($f.Count -ge 12) { Num $f[8] } else { $null }; if ($c -ne $null) { [pscustomobject]@{ Pid = $f[0]; Cpu = $c; Name = $f[11] } } } |
  Where-Object { $_ -ne $null -and $_.Cpu -ge 30 -and $_.Name -notmatch '^top$' -and $_.Name -ne $pkg })
if ($hogs.Count -gt 0) { $hogs | ForEach-Object { Check 'process above 30 % CPU' ("$($_.Name) $($_.Cpu)%") $false 'name it; stop it or wait (acore = post-boot indexing)' } }
else { Check 'process above 30 % CPU' 'none' $true 'other than this shell and the app' }

# --- the model ---------------------------------------------------------------------------------
$appPid = "$(Sh "pidof $pkg" | Select-Object -Last 1)".Trim()
$pss = if ($appPid -match '\d') { "$((Sh "dumpsys meminfo $pkg | grep 'TOTAL PSS'" | Select-Object -First 1) -replace '.*TOTAL PSS:\s*(\d+).*', '$1')".Trim() } else { '' }
$pssMb = if ($pss -match '^\d+$') { [math]::Round([int]$pss / 1024) } else { 0 }
Check 'app process' $(if ($appPid -match '\d') { "pid $appPid" } else { 'not running' }) ($appPid -match '\d') 'open the app first'
Check 'model resident (PSS MB)' $pssMb ($pssMb -ge 1400) 'first guess: at least 1400 MB with the 1.1 GB model mapped (1990 MB after load on 21 Sep)'

# --- verdict ------------------------------------------------------------------------------------
if ($reasons.Count -eq 0) { Line "READY"; exit 0 } else { Line ("WAIT: " + ($reasons -join '; ')); exit 1 }
