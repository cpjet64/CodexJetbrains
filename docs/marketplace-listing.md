# Marketplace Listing Draft

## Name
Codex (Unofficial)

## Tagline
Codex workflow inside JetBrains IDEs with approvals, diagnostics, and diff apply.

## Short Description
Codex (Unofficial) integrates the Codex CLI into JetBrains IDEs with a dedicated ToolWindow
for chat, command/patch approvals, diagnostics, and diff preview/apply flows.

## Long Description
Codex (Unofficial) is a community JetBrains plugin designed to keep Codex-assisted development
inside the IDE. It maintains a long-lived Codex session, streams responses, and surfaces key
execution controls so users can safely review and apply changes.

Key capabilities:
- App Server v2-first protocol flow with compatibility fallback
- Approval prompts for command execution and patch application
- Diff preview/apply workflow integrated with project files
- Diagnostics panel for stderr/troubleshooting visibility
- ToolWindow + status indicators for active session health

Requirements:
- JetBrains IDE 2025.2+
- Codex CLI configured in plugin settings

Disclaimer:
This is an unofficial community plugin and is not affiliated with or endorsed by JetBrains or
OpenAI.
