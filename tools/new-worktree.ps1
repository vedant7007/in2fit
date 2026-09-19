# Gives one session its own working tree, so six sessions stop building in one app\build.
#
#   powershell -File tools\new-worktree.ps1 -Name nila
#
# creates C:\CODING\IQOOOOO-nila on branch `nila` (from master, or the branch if it exists),
# copies the three gitignored things a build needs and cannot fetch (local.properties,
# app\src\main\jniLibs, app\libs), and points data-sources at the main tree's copy with a
# junction, because 1.7 GB of weights and a llama.cpp clone are read by scripts, never built by
# Gradle, and one copy is enough. Then it prints the landing procedure.
#
# WHY. Ruled by Vedant on 20 Sep 2026 after two collisions in one hour: a Gradle run from one
# session died on a classes.jar another session's run held open, and a second session's test
# run cleared app\build\test-results under a first session's run, so the directory held 13
# tests where 245 had run. A green build that is somebody else's is the one thing this project
# cannot afford. Separate trees, separate app\build, separate logs.
#
# A branch can be checked out in only one worktree, so each session works on its own branch and
# LANDS on master with tools\land.ps1: rebase onto master, then fast-forward master in the main
# tree. Nothing merges; history stays a line.
param([Parameter(Mandatory = $true)][string]$Name)
$ErrorActionPreference = 'Stop'
$main = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dest = Join-Path (Split-Path $main -Parent) ((Split-Path $main -Leaf) + '-' + $Name)
if (Test-Path $dest) { throw "$dest already exists" }

Push-Location $main
try {
  $exists = (& git branch --list $Name) -ne $null -and (& git branch --list $Name).Trim() -ne ''
  if ($exists) { & git worktree add $dest $Name }
  else         { & git worktree add -b $Name $dest master }
  if ($LASTEXITCODE -ne 0) { throw "git worktree add failed" }
} finally { Pop-Location }

foreach ($rel in 'local.properties', 'app\src\main\jniLibs', 'app\libs') {
  $src = Join-Path $main $rel
  if (-not (Test-Path $src)) { Write-Host "WARNING: $rel is not in the main tree; the build will say what to run"; continue }
  $to = Join-Path $dest $rel
  New-Item -ItemType Directory -Force -Path (Split-Path $to -Parent) | Out-Null
  Copy-Item $src $to -Recurse -Force
  $n = (Get-ChildItem $to -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum)
  Write-Host ("copied  {0,-24} {1,4} file(s) {2,12:N0} bytes" -f $rel, $n.Count, $n.Sum)
}
$ds = Join-Path $main 'data-sources'
if (Test-Path $ds) {
  New-Item -ItemType Junction -Path (Join-Path $dest 'data-sources') -Target $ds | Out-Null
  Write-Host "junction data-sources -> $ds (shared; do not run build-llama-android from two trees at once)"
}
New-Item -ItemType Directory -Force -Path (Join-Path $dest 'logs') | Out-Null

Write-Host ""
Write-Host "READY: $dest on branch $Name"
Write-Host "  build and test THERE. Logs land in its own logs\. app\build is its own."
Write-Host "  land your commits on master with:  powershell -File tools\land.ps1   (from inside the worktree)"
Write-Host "  read what others landed with:      git rebase master                  (same)"
