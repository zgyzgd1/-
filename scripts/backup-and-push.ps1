[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Message,
    [string]$Branch = "main",
    [string]$Remote = "origin",
    [string]$BackupPrefix = "backup"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Args)

    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE."
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$status = git status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect git status."
}
if (-not $status) {
    throw "No local changes to commit."
}

$currentCommit = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to resolve current HEAD."
}
$shortCommit = (& git rev-parse --short HEAD).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to resolve current short HEAD."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupTag = "$BackupPrefix-$timestamp-$shortCommit"

Invoke-Git -Args @("tag", "-a", $backupTag, $currentCommit, "-m", "Backup before: $Message")
Invoke-Git -Args @("push", $Remote, $backupTag)

Invoke-Git -Args @("add", "-A")
Invoke-Git -Args @("commit", "-m", $Message)
Invoke-Git -Args @("push", $Remote, $Branch)

Write-Host "Backup tag: $backupTag"
Write-Host "Pushed branch: $Branch"
