param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Gradle {
  param(
    [Parameter(Mandatory=$true)][string]$Command,
    [Parameter(Mandatory=$true)][string]$LogPath
  )
  Write-Host "Running: $Command"
  # PowerShell 5.1 treats native stderr as errors with ErrorActionPreference=Stop.
  # Temporarily relax, and use cmd.exe to handle streams reliably.
  $eap = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    cmd.exe /c $Command 2>&1 | Tee-Object -FilePath $LogPath
    return $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $eap
  }
}

# Normalize Java
$java = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $java -and $env:JAVA_HOME) {
  $javaBin = Join-Path $env:JAVA_HOME 'bin\java.exe'
  if (Test-Path $javaBin) {
    $env:PATH = (Split-Path $javaBin) + ';' + $env:PATH
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
  }
}

if (-not $java) {
  Write-Warning "Java runtime not found. Set JAVA_HOME to Temurin JDK 21 and ensure its \bin is on PATH."
} else {
  # Capture java -version without producing NativeCommandError on PS 5.1
  $ver = cmd /c 'java -version 2>&1' | Select-Object -First 1
  Write-Host "Java detected: $ver"
}

Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
try {
  if (-not (Test-Path .\gradlew.bat)) { throw 'gradlew.bat not found in repo root' }
  New-Item -Force -ItemType Directory -Path .\build\logs | Out-Null

  $cmd = '.\gradlew.bat --no-daemon --console=plain test verifyPlugin buildPlugin'
  $exit = Invoke-Gradle -Command $cmd -LogPath '.\build\logs\verify.log'

  if ($exit -ne 0) {
    Write-Error "Gradle exited with code $exit"
  } else {
    Write-Host "Artifacts:"
    Get-ChildItem build\distributions -Force -ErrorAction SilentlyContinue | Format-Table -AutoSize
  }

  exit $exit
}
finally {
  Pop-Location
}

