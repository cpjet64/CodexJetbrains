# Contributing

Thanks for helping shape Codex for JetBrains! This guide summarizes the project conventions from
`AGENTS.md` and provides practical tips for day-to-day work.

## Before You Start
- Review `TODO_JetBrains.md` to understand outstanding tasks and their current status.
- Configure `JAVA_HOME` to JDK 17 before running Gradle tasks.
- Install the Codex CLI and ensure it is reachable from your shell for end-to-end testing.

## Workflow
1. Pick the next unchecked item in `TODO_JetBrains.md`. Mark it `[/]` when you begin.
2. Develop the change in focused commits. Keep Kotlin functions under 40 lines and wrap text at
   100 characters.
3. Run `./gradlew test` (or more focused tasks) before finalizing a commit.
4. Update the TODO entry to `[x]` once tests pass; use `[!]` when a blocker appears.
5. Follow the commit template `[T<task>.<sub>] <summary>; post-test=<result>; compare=<stdout>`.
6. Open a pull request that lists completed subtasks, notes remaining risks, and links tracking
   issues or discussions.

## Code Style
- Use 4-space indentation for Kotlin files.
- Prefer expressive, single-purpose functions instead of deep nesting.
- Add comments only when intent is not obvious from the code itself.
- Keep individual files under 300 lines whenever possible.

## Testing
- Place unit tests in `src/test/kotlin`, mirroring the production package structure.
- Name test classes with the `*Test` suffix and give each test a descriptive function name.
- Cover CLI process management, ToolWindow flows, protocol handling, and approval logic.

## Documentation
- Reflect new configuration knobs or behaviors in `README.md` or follow-up docs.
- Record troubleshooting notes or limitations in the tracker until a dedicated docs section exists.

## Communication
- Use GitHub issues to propose major changes or design adjustments.
- Attach screenshots or recordings for UI-affecting pull requests.
- Call out open questions directly in the PR description so reviewers can respond quickly.

Welcome aboard, and thanks again for contributing!
