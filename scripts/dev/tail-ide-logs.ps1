param(
  [int]$Tail = 200
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$log = Join-Path $env:USERPROFILE '.codex-idea-sandbox\system\log\idea.log'
if (-not (Test-Path $log)) {
  Write-Error "Log file not found: $log"
}

Write-Host "Showing last $Tail lines of $log"
Get-Content $log -Tail $Tail

