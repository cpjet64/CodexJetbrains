# Privacy and Data Handling

Codex (Unofficial) for JetBrains is a local IDE plugin that connects to the Codex CLI.

## What the Plugin Processes

- User prompts and model responses for active Codex sessions
- CLI stderr diagnostics shown in the Diagnostics panel
- Diff content proposed/applied in the current project
- Local configuration values from plugin settings

## What the Plugin Does Not Do

- No built-in analytics or telemetry upload service is included
- No external data collection endpoint is configured by default
- No third-party SaaS dependency is required to run plugin features

## Where Data Is Stored

- IntelliJ sandbox/project settings files (IDE-managed storage)
- Session/diagnostic context in memory while the IDE is running
- Optional local logs produced by Gradle/IDE tooling

## User-Controlled External Data Flow

If the configured Codex CLI endpoint sends data to an external provider, that network
activity is performed by the CLI configuration you choose. Review your Codex provider
settings and policies before use.

## Sensitive Data

The plugin includes redaction logic in diagnostics paths for common token/API-key
patterns. Treat local logs and exported issue bundles as potentially sensitive and review
before sharing.

## Disclaimer

This is an unofficial community plugin and is not affiliated with or endorsed by
JetBrains or OpenAI.
