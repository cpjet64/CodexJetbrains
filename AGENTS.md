# Development & Automation Guide

## Prereqs
- **JDK 21** installed. Ensure `java -version` reports 21 and set `JAVA_HOME` accordingly.
- Gradle Wrapper is checked in; use `./gradlew` (do not install Gradle system-wide).

## Common Commands
- `./gradlew clean build` — compile, test, assemble the plugin.
- `./gradlew runIde` — launch a sandbox IDE with the plugin installed.
- `./gradlew buildPlugin` — produce the ZIP at `build/distributions/`.
- `./gradlew printProductsReleases` — list valid IDE product baselines.
- `./gradlew printBundledPlugins` — list bundled plugin IDs for the selected baseline.

## JDK & Toolchains (we target 21)
The build enforces **JDK 21** via Gradle **and** Kotlin toolchains; Kotlin bytecode is compiled with `-jvm-target=21`. IDE runtime JBR is tied to the target IDE version (e.g., 2024.2+ runs on JBR 21).

## Target IDEs
- **Platform-only** (widest coverage) if we *don’t* depend on `com.intellij.java`.
- If `com.intellij.java` is enabled, the plugin targets IntelliJ IDEA–based IDEs.

### Toggle Java dependency at build time
Set `ORG_GRADLE_PROJECT_codex_withJava=true|false` to include/exclude the `com.intellij.java` dependency.

## CI Notes
Use the Gradle Wrapper; do not rely on system JDK selection. Cache `.gradle` and Gradle User Home as appropriate.
