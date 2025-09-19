# Codex for JetBrains (Unofficial) — Starter

This is a minimal IntelliJ Platform plugin scaffold that integrates with the Codex CLI by
spawning `codex proto` and exchanging JSON lines over stdin/stdout. It provides a ToolWindow
named "Codex", a simple chat panel, and an editor action "Ask Codex" that sends the current
selection to the running session.

Build: `./gradlew buildPlugin` (Java 17 required). Install the ZIP from `build/distributions`.

## Prerequisites
Ensure `JAVA_HOME` points to a Java 17 runtime before building. Run `scripts/dev/check-java.sh` to verify the configuration.
