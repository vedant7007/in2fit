# Appends one row per APK on disk to the MAIN tree's logs\apk-size.log, so an APK figure quoted
# anywhere in this project can be traced to a build rather than to memory. Three untraceable
# APK sizes were in circulation before 0016 measured one; a row here is what stops a fourth.
#
#   time  commit[-dirty]  variant  bytes  sha256-of-the-apk  tree-it-was-built-in
#
# ONE ledger whichever session worktree built the APK: --git-common-dir is the main tree's
# .git, and its parent is the main tree; from the main tree that is itself. APPEND-ONLY, like
# the arbiter's measurement log: the trend is the finding. Run by build.ps1 after every
# assemble, or on its own after any gradlew assemble.
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $root
$commit = (& git rev-parse --short HEAD).Trim()
if ((& git status --porcelain --untracked-files=no | Measure-Object).Count -gt 0) { $commit += '-dirty' }
$common = (& git rev-parse --git-common-dir).Trim()
if (-not [System.IO.Path]::IsPathRooted($common)) { $common = Join-Path $root $common }
$main = Split-Path ([System.IO.Path]::GetFullPath($common)) -Parent
Pop-Location
$log = Join-Path $main 'logs\apk-size.log'
New-Item -ItemType Directory -Force -Path (Split-Path $log -Parent) | Out-Null
if (-not (Test-Path $log)) {
  Set-Content -Path $log -Value "# time  commit  variant  bytes  sha256  tree  (append-only; tools/apk-size.ps1)" -Encoding UTF8
}
Get-ChildItem (Join-Path $root 'app\build\outputs\apk') -Recurse -Filter *.apk -ErrorAction SilentlyContinue | ForEach-Object {
  $variant = $_.Directory.FullName.Replace((Join-Path $root 'app\build\outputs\apk\'), '').Replace('\', '/')
  $sha = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
  $line = "{0}  {1}  {2}  {3}  {4}  {5}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $commit, $variant, $_.Length, $sha, (Split-Path $root -Leaf)
  Write-Host $line
  Add-Content -Path $log -Value $line -Encoding UTF8
}
