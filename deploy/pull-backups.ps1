# Pull SmartTask backups from the VPS to this Windows machine.
# Intended to run from Task Scheduler, e.g. daily at 03:00.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\pull-backups.ps1 `
#     -VpsHost 203.0.113.10 -User smarttask `
#     -KeyFile "$env:USERPROFILE\.ssh\smarttask_backup"
#
# Uses only the built-in Windows OpenSSH client (scp). Requires key-based SSH
# (no password prompt) because Task Scheduler runs unattended.

param(
  [Parameter(Mandatory = $true)][string]$VpsHost,
  [string]$User = "smarttask",
  [string]$RemoteDir = "/opt/smarttask/deploy/backups",
  [string]$LocalDir = (Join-Path $PSScriptRoot "local-backups"),
  [string]$KeyFile = "",
  [int]$KeepDays = 30
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $LocalDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dest = Join-Path $LocalDir $stamp
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$scpArgs = @(
  "-r",
  "-o", "BatchMode=yes",
  "-o", "StrictHostKeyChecking=accept-new"
)
if ($KeyFile) {
  $scpArgs += @("-i", $KeyFile)
}
$scpArgs += @("${User}@${VpsHost}:${RemoteDir}/.", $dest)

& scp @scpArgs
if ($LASTEXITCODE -ne 0) {
  throw "scp failed with exit code $LASTEXITCODE"
}

# Keep local snapshots for KeepDays; never touch anything outside LocalDir.
$cutoff = (Get-Date).AddDays(-$KeepDays)
Get-ChildItem -Path $LocalDir -Directory |
  Where-Object { $_.CreationTime -lt $cutoff } |
  Remove-Item -Recurse -Force

Write-Host "Pulled backups to $dest"
