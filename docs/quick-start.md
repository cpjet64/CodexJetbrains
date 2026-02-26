# Quick Start

## 1. Prerequisites

- IntelliJ-based IDE 2025.2 or newer
- JDK 21 for local build/test work
- Codex CLI installed and reachable

## 2. Build the Plugin

```powershell
./gradlew.bat test verifyPlugin buildPlugin
```

Output ZIP:

- `build/distributions/CodexJetbrains-<version>.zip`

## 3. Install in IDE

1. Open `Settings > Plugins`
2. Click gear icon
3. Select `Install Plugin from Disk...`
4. Pick the ZIP from `build/distributions`
5. Restart IDE if prompted

## 4. Configure Codex

1. Open `Settings > Tools > Codex`
2. Set Codex CLI path (absolute path recommended)
3. Configure optional settings:
   - WSL preference (Windows)
   - Approval mode
   - Sandbox mode
4. Use `Test connection` when available

## 5. First Validation

1. Open the Codex tool window
2. Send a short prompt
3. Confirm:
   - Response streaming appears
   - Diff preview (if applicable) renders
   - Approval prompts appear when required
   - Diagnostics tab receives CLI stderr when errors occur

## 6. Uninstall

1. `Settings > Plugins`
2. Find `Codex (Unofficial)`
3. `Uninstall`
4. Restart IDE
