# Migration: Gradle IntelliJ Plugin 1.x → 2.x (2025)

This plugin has been migrated to the IntelliJ Platform Gradle Plugin 2.x series to align with the
2025 Source-of-Truth guidance. The changes below summarize what moved where and how to adapt your
local environment if you’re used to the 1.x DSL.

## Highlights
- Plugin ID changed to `org.jetbrains.intellij.platform` (was `org.jetbrains.intellij`).
- Requires the settings plugin `org.jetbrains.intellij.platform.settings` in `settings.gradle.kts`.
- Java baseline set to 21 for the 252 (2025.2) branch.
- `ideaVersion` and `pluginConfiguration` moved under the `intellijPlatform { ... }` block.
- Platform dependencies moved under `dependencies { intellijPlatform { ... } }`.
- Built-in tasks preserved: `runIde`, `buildPlugin`, `verifyPlugin`, `publishPlugin`.

## Files changed

### settings.gradle.kts
- Applied `org.jetbrains.intellij.platform.settings` v2.9.0.
- Added `pluginManagement` repositories (Gradle Plugin Portal, Maven Central).
- Added `dependencyResolutionManagement` with `intellijPlatform.defaultRepositories()`.

### build.gradle.kts
- Replaced `id("org.jetbrains.intellij")` with `id("org.jetbrains.intellij.platform")`.
- Added `java.toolchain` and `kotlin.jvmToolchain(21)`.
- Introduced `intellijPlatform { pluginConfiguration { ... } ideaVersion { ... } }`.
- Added `intellijPlatform.signing` and `intellijPlatform.publishing` (wired to CI env vars).
- Configured `verification { ides { ide("IC", "2025.2.2") } }`.
- Moved platform artifacts to `dependencies { intellijPlatform { create("IC", "2025.2.2"); plugins("java") } }`.
- Disabled `buildSearchableOptions` by task name.
- Updated `runIde` sandbox paths via a generic `withType<JavaExec>` configuration.

## Environment
- Set `JAVA_HOME` to JDK 21 for local builds, or rely on Gradle toolchains which will provision a
  matching JDK automatically.
- CI updated to use Java 21 across OS matrices.

## Signing & Publishing
The configuration is present but inert locally. CI must provide the following secrets:

- `INTELLIJ_CERTIFICATE_CHAIN`
- `INTELLIJ_PRIVATE_KEY`
- `INTELLIJ_PRIVATE_KEY_PASSWORD`
- `INTELLIJ_PUBLISH_TOKEN`

## Known Differences vs 1.x
- The `intellij { ... }` block is replaced by `dependencies { intellijPlatform { ... } }` and
  `intellijPlatform { ideaVersion { ... } }`.
- Repository handling moved into settings via the settings plugin. Use
  `intellijPlatform.defaultRepositories()` instead of hardcoding JetBrains Maven URLs.

Refer to JetBrains’ official migration guide for 2.x if you add more advanced features like
custom IDE distributions, local IDE builds, or verification matrices.

