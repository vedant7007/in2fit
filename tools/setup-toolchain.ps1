# Installs the Android NDK and CMake into the canonical SDK root.
# Everything is logged. Nothing is reported that is not in this log.
$ErrorActionPreference = 'Continue'
$log = Join-Path $PSScriptRoot '..\logs\sdk-install.log'
$log = [System.IO.Path]::GetFullPath($log)

function Log($m) {
  $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}

Set-Content -Path $log -Value "=== toolchain setup $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$sdkMgr  = Join-Path $env:USERPROFILE 'Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat'

Log "SDK_ROOT=$sdkRoot"
Log "SDKMANAGER=$sdkMgr"
Log "SDK_ROOT exists: $(Test-Path $sdkRoot)"
Log "SDKMANAGER exists: $(Test-Path $sdkMgr)"

if (-not (Test-Path $sdkMgr)) { Log "FATAL: sdkmanager not found"; exit 2 }
if (-not (Test-Path $sdkRoot)) { Log "FATAL: sdk root not found"; exit 2 }

# --- pick a JDK sdkmanager can run on -------------------------------------
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$candidates += @(
  'C:\Program Files\Android\Android Studio\jbr',
  "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
  'C:\Program Files\Android\Android Studio1\jbr'
)
Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
  ForEach-Object { $candidates += $_.FullName }

$picked = $null
foreach ($c in $candidates) {
  if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) {
    $v = & (Join-Path $c 'bin\java.exe') -version 2>&1 | Out-String
    Log "JDK candidate OK: $c :: $($v.Trim() -replace "`r`n", ' | ')"
    if (-not $picked) { $picked = $c }
  } else {
    Log "JDK candidate missing: $c"
  }
}
if (-not $picked) { Log "FATAL: no JDK found"; exit 3 }
$env:JAVA_HOME = $picked
Log "USING JAVA_HOME=$picked"

# --- list available packages ----------------------------------------------
Log "--- sdkmanager --list (this needs network and may take a minute) ---"
$listFile = Join-Path $PSScriptRoot '..\logs\sdk-list.log'
$listFile = [System.IO.Path]::GetFullPath($listFile)
& cmd /c "`"$sdkMgr`" --sdk_root=`"$sdkRoot`" --list" 1> $listFile 2>&1
Log "sdkmanager --list exit code: $LASTEXITCODE"
if (-not (Test-Path $listFile)) { Log "FATAL: no list output"; exit 4 }

$listText = Get-Content $listFile -Raw
$ndks = [regex]::Matches($listText, 'ndk;(2[78])\.([0-9]+)\.([0-9]+)') |
        ForEach-Object { $_.Value } | Sort-Object -Unique
Log "NDK 27/28 versions offered: $($ndks -join ', ')"

if (-not $ndks -or $ndks.Count -eq 0) {
  Log "FATAL: no ndk 27.x or 28.x offered. See sdk-list.log"
  exit 5
}

# newest by numeric sort
$ndkPick = $ndks | Sort-Object {
  $p = ($_ -replace '^ndk;','').Split('.')
  [int]$p[0]*1000000 + [int]$p[1]*10000 + [int]$p[2]
} | Select-Object -Last 1
Log "NDK SELECTED: $ndkPick"

$cmakePick = 'cmake;3.22.1'
if ($listText -notmatch [regex]::Escape($cmakePick)) {
  Log "WARNING: $cmakePick not found in offered list; attempting install anyway"
}
Log "CMAKE SELECTED: $cmakePick"

# --- install ---------------------------------------------------------------
Log "--- installing. this is several GB and can take 20+ minutes ---"
$installLog = Join-Path $PSScriptRoot '..\logs\sdk-install-raw.log'
$installLog = [System.IO.Path]::GetFullPath($installLog)
$yes = ("y`r`n" * 60)
$yes | & cmd /c "`"$sdkMgr`" --sdk_root=`"$sdkRoot`" --install `"$ndkPick`" `"$cmakePick`"" 1> $installLog 2>&1
Log "install exit code: $LASTEXITCODE"

# --- verify on disk, not from the installer's claims -----------------------
$ndkDir   = Join-Path $sdkRoot 'ndk'
$cmakeDir = Join-Path $sdkRoot 'cmake'
Log "VERIFY ndk dir exists: $(Test-Path $ndkDir)"
if (Test-Path $ndkDir) {
  Get-ChildItem $ndkDir -Directory | ForEach-Object { Log "VERIFY ndk installed: $($_.Name)" }
}
Log "VERIFY cmake dir exists: $(Test-Path $cmakeDir)"
if (Test-Path $cmakeDir) {
  Get-ChildItem $cmakeDir -Directory | ForEach-Object { Log "VERIFY cmake installed: $($_.Name)" }
}
Get-ChildItem $sdkRoot -Directory | ForEach-Object { Log "SDKROOT entry: $($_.Name)" }
Log "=== DONE ==="
