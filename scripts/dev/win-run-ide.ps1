# Windows helper: launch sandbox IDE with console output logged to file
# Usage: powershell -ExecutionPolicy Bypass -File scripts/dev/win-run-ide.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
try {
  if (-not (Test-Path .\gradlew.bat)) { throw 'gradlew.bat not found in repo root' }
  New-Item -Force -ItemType Directory -Path .\build\logs | Out-Null

  $args = @('--no-daemon','--console=plain','runIde','--stacktrace','--info')
  Write-Host "Running: .\\gradlew.bat $($args -join ' ')"
  & .\gradlew.bat @args *>&1 | Tee-Object -FilePath .\build\logs\runIde.log
  if ($LASTEXITCODE -ne 0) { Write-Warning "Gradle exited with code $LASTEXITCODE" }
}
finally {
  Pop-Location
}
