# Windows helper: build, test, verify with console output logged to file
# Usage: powershell -ExecutionPolicy Bypass -File scripts/dev/win-build.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
try {
  if (-not (Test-Path .\gradlew.bat)) { throw 'gradlew.bat not found in repo root' }
  New-Item -Force -ItemType Directory -Path .\build\logs | Out-Null

  $args = @('--no-daemon','--console=plain','test','verifyPlugin','buildPlugin')
  Write-Host "Running: .\\gradlew.bat $($args -join ' ')"
  & .\gradlew.bat @args *>&1 | Tee-Object -FilePath .\build\logs\gradle-build.log
  if ($LASTEXITCODE -ne 0) { throw "Gradle exited with code $LASTEXITCODE" }
}
finally {
  Pop-Location
}

