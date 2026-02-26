# Dependency License Validation

Last validated: 2026-02-26

## Scope

Direct dependencies declared in `build.gradle.kts` and plugin platform dependencies used
for build/test/runtime integration.

## Direct Dependency Review

- `junit:junit:4.13.2` -> EPL 1.0
- `org.junit.jupiter:junit-jupiter-api:5.10.1` -> EPL 2.0
- `org.junit.jupiter:junit-jupiter-engine:5.10.1` -> EPL 2.0
- `org.junit.vintage:junit-vintage-engine:5.10.1` -> EPL 2.0
- `org.opentest4j:opentest4j:1.3.0` -> Apache License 2.0

## Platform/Bundled Components

- IntelliJ Platform (`intellijIdeaCommunity`) and bundled plugins (for example `Git4Idea`)
  are provided under JetBrains platform terms.

## Outcome

- No direct dependency in the declared set conflicts with Apache-2.0 distribution of this
  repository's source.
- Attribution/notice requirements are tracked in `NOTICE`.

## Follow-up

- Re-run review on each dependency version bump or when adding new third-party libraries.
