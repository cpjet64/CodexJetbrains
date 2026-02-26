# Security Policy

## Supported Versions

Security fixes are provided for the latest development line on `main`.
The `0.1.x` release line is the current supported release family.

## Reporting a Vulnerability

Do not open public GitHub issues for sensitive vulnerabilities.

Report security concerns privately to:

- Email: `security@codexjb.dev`
- Subject prefix: `[CodexJetbrains Security]`

Please include:

- Affected version/commit
- Reproduction steps or proof of concept
- Impact assessment (confidentiality/integrity/availability)
- Suggested remediation (if available)

We target an initial response within 3 business days and will coordinate disclosure
timelines with the reporter.

## Security Scope

The most sensitive areas in this plugin are:

- CLI process launch and environment handling
- Diff/patch application in project files
- Approval prompts and execution gating
- Diagnostics logging and sensitive-data redaction

Reports in these areas are prioritized for rapid triage.
