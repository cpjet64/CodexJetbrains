Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
try {
  if (Test-Path .\build\idea-sandbox) {
    Write-Host "Removing build/idea-sandbox..."
    Remove-Item -Recurse -Force .\build\idea-sandbox
  }
  if (Test-Path .\build\logs\runIde.log) {
    Write-Host "Removing build/logs/runIde.log..."
    Remove-Item -Force .\build\logs\runIde.log
  }
  Write-Host "Running Gradle clean and prepareSandbox..."
  & .\gradlew.bat --no-daemon --console=plain clean prepareSandbox
}
finally {
  Pop-Location
}

