# Repository Guidelines

## Project Structure & Module Organization
The IntelliJ plugin lives under `src/main/kotlin`, organized by feature packages (service, ui, tooling). Resources such as plugin descriptors and UI assets belong in `src/main/resources`. Gradle build logic is defined in `build.gradle.kts`, while `settings.gradle.kts` manages project metadata. Use `TODO_JetBrains.md` to track task status and `KICKOFF_JB.txt` for high-level context.

## Build, Test, and Development Commands
- `./gradlew buildPlugin` — compiles sources, runs tests, and produces the distributable ZIP in `build/distributions`.
- `./gradlew test` — executes unit and integration tests; run before committing code.
- `./gradlew runIde` — launches a sandbox IDE for manual plugin verification.
Ensure `JAVA_HOME` points to JDK 17 before running any command.

## Coding Style & Naming Conventions
Write Kotlin with 4-space indentation and prefer expressive, single-purpose functions under 40 lines. Follow JetBrains’ Kotlin style: upper camel case for classes, lower camel case for methods and variables, and snake case for constants. Keep files under 300 lines and wrap lines at 100 characters. Use comments sparingly—only to clarify non-obvious intent.

## Testing Guidelines
Place tests in `src/test/kotlin`, mirroring the production package hierarchy. Name test classes with the `*Test` suffix and individual tests using descriptive `fun` names (backtick syntax allowed for readability). Target meaningful coverage of CLI process management, ToolWindow flows, and protocol handling. Run `./gradlew test` locally; add focused integration tests when introducing new UI or IPC features.

## Commit & Pull Request Guidelines
Commit after each completed subtask listed in `TODO_JetBrains.md`. Follow the required message pattern: `[T<task>.<sub>] <short>; post-test=<pass>; compare=<summary>`. Include the post-test command output summary in `<summary>`, or `N/A` if not applicable. Pull requests should enumerate completed subtasks, note remaining risks, attach screenshots or recordings for UI changes, and reference related issues or discussions.

## Security & Configuration Tips
Never hardcode credentials or tokens; rely on the Codex CLI for authentication. Validate paths and user input before invoking external processes. Document any new environment variables or configuration knobs in `README.md` and mirror them in future settings UI work.
