# Windows smoke test for Codex JetBrains plugin
# - Builds plugin, launches sandbox IDE, and tails sandbox log location.
# - Requires JDK 21 (recommended) and network to download IntelliJ platform IDE artifacts.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Ensure we run from repo root even if script is invoked from elsewhere
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
try {

function Require-Cmd($name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $name"
  }
}

Write-Host "Checking prerequisites..."
Require-Cmd powershell
Require-Cmd cmd

# Ensure Gradle wrapper exists
if (-not (Test-Path .\gradlew.bat)) { throw 'gradlew.bat not found in repo root' }

# Check Java presence (Gradle toolchains will fetch JDK 21 for compile if Java exists)
$javaLine = $null
try { $javaLine = (& java -version) 2>&1 | Select-Object -First 1 } catch { }
if ($javaLine) {
  Write-Host "java -version => $javaLine"
} else {
  Write-Warning @'
No Java detected on PATH. Install Temurin JDK 21 and set JAVA_HOME (examples):
  winget install EclipseAdoptium.Temurin.21.JDK
  # or: choco install temurin21-jdk -y
'@
}

Write-Host "Building plugin..."
& .\gradlew.bat --no-daemon --console=plain buildPlugin
if ($LASTEXITCODE -ne 0) { throw "buildPlugin failed with exit code $LASTEXITCODE" }

$zip = Get-ChildItem build\distributions\*.zip | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $zip) { throw 'Plugin ZIP not found under build/distributions' }
Write-Host "Built: $($zip.FullName)"

$sandbox = Join-Path $env:USERPROFILE '.codex-idea-sandbox'
$log = Join-Path $sandbox 'system\log\idea.log'
Write-Host "Sandbox: $sandbox"
Write-Host "Log: $log"

Write-Host "Launching sandbox IDE (closes when IDE exits)..."
& .\gradlew.bat --no-daemon --console=plain runIde
if ($LASTEXITCODE -ne 0) { Write-Warning "runIde exited with code $LASTEXITCODE" }

if (Test-Path $log) {
  Write-Host "Recent sandbox log lines:" -ForegroundColor Cyan
  Get-Content $log -Tail 200
} else {
  Write-Warning "Sandbox log not found yet. It will appear after the IDE starts: $log"
}

Write-Host "Done. In the IDE: open the Codex tool window and verify CLI detection, approvals, and a simple chat turn." -ForegroundColor Green

} finally {
  Pop-Location
}
