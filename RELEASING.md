# Releasing & Versioning

This project follows Semantic Versioning (semver): `MAJOR.MINOR.PATCH`.

## Version Policy
- Bump `MINOR` for new features or non-breaking changes.
- Bump `PATCH` for bug fixes and internal-only changes.
- Reserve `MAJOR` for breaking changes.

## Tagging Policy
- Tags are prefixed with `v`, for example: `v0.2.1`.
- Create an annotated tag that matches the version in `build.gradle.kts`.

## Release Steps
1. Update `version = "x.y.z"` in `build.gradle.kts`.
2. Update `RELEASE_NOTES_TEMPLATE.md` into actual release notes for the version.
3. Commit with message: `[Trelease.bump] Bump to x.y.z; post-test=N/A; compare=N/A`.
4. Tag the commit: `git tag -a vx.y.z -m "Release vx.y.z" && git push origin vx.y.z`.
5. Open a GitHub Release using the tag and paste the release notes.
6. CI attaches artifacts to the workflow run. For Marketplace publishing:
   - Ensure CI secrets are configured: `INTELLIJ_CERTIFICATE_CHAIN`,
     `INTELLIJ_PRIVATE_KEY`, `INTELLIJ_PRIVATE_KEY_PASSWORD`, `INTELLIJ_PUBLISH_TOKEN`.
   - Run `./gradlew publishPlugin` locally or via a protected CI job.

## First Upload Guidance
- Perform the first upload manually via the JetBrains Marketplace UI to validate configuration.
- Subsequent releases can be automated using `publishPlugin` once secrets are in place.

