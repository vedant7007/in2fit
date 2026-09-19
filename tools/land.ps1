# Lands this worktree's commits on master. Run from inside a session worktree, with a clean tree.
#
#   powershell -File tools\land.ps1
#
# Two steps, both refuse rather than improvise:
#   1. git rebase master           your commits move on top of whatever others landed since
#   2. git -C <main> merge --ff-only <branch>   master in the MAIN tree advances to your tip
# A rebase conflict stops at step 1 with git's own message; resolve it, `git rebase --continue`,
# run this again. Step 2 cannot conflict: it is a fast-forward or it is refused, and the one way
# it is refused is a dirty file in the main tree that your commits touch, which means somebody is
# still editing there. Say so in COORDINATION.md rather than forcing anything.
#
# History stays a single line, authored as it always was. No merge commits, no branch names in
# the log; the branch is a workspace, not a feature.
$ErrorActionPreference = 'Stop'
$here = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $here
try {
  # --git-common-dir is the MAIN tree's .git, relative or absolute; its parent is the main tree.
  $common = (& git rev-parse --git-common-dir).Trim()
  if (-not [System.IO.Path]::IsPathRooted($common)) { $common = Join-Path $here $common }
  $main = Split-Path ([System.IO.Path]::GetFullPath($common)) -Parent
  $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
  if ($branch -eq 'master') { throw "this IS master ($here). land.ps1 runs from a session worktree." }
  if ((& git status --porcelain --untracked-files=no | Measure-Object).Count -gt 0) {
    throw "uncommitted changes in $here; commit or stash before landing"
  }
  Write-Host "--- rebase $branch onto master ---"
  & git rebase master
  if ($LASTEXITCODE -ne 0) { throw "rebase stopped; resolve, 'git rebase --continue', then run land.ps1 again" }
  Write-Host "--- fast-forward master in $main ---"
  & git -C $main merge --ff-only $branch
  if ($LASTEXITCODE -ne 0) { throw "master did not fast-forward; see the message above (a dirty file in the main tree?)" }
  Write-Host ("LANDED: master is now {0}" -f (& git -C $main rev-parse --short HEAD).Trim())
} finally { Pop-Location }
