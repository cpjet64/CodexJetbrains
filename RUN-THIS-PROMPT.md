# Standardization Verification Prompt

Run this prompt with an AI coding agent to verify and enforce project standards.
This is idempotent — safe to re-run at any time.

## Instructions for AI Agent

You are auditing this project for compliance with the dev tooling standards.
Check each item below. For each: report PASS if already correct, FIX if you
corrected it, or FAIL if it needs manual attention. Do not skip items.

### 1. Global Tools (verify installed, do not install)
- [ ] `sccache --show-stats` works
- [ ] `cargo nextest --version` works
- [ ] `cargo llvm-cov --version` works
- [ ] `cargo deny --version` works
- [ ] `cargo audit --version` works
- [ ] `cargo machete --version` works
- [ ] `just --version` works

### 2. Rust Project Checks (skip if no Cargo.toml)
- [ ] `rust-toolchain.toml` exists with channel = "1.93.1" and components = ["rustfmt", "clippy", "llvm-tools-preview"]
- [ ] `deny.toml` exists with deny vulns/unknown-registry/unknown-git, warn unmaintained/multi-versions
- [ ] `.nextest.toml` exists with at least a default profile
- [ ] `.cargo/config.toml` exists (verify sccache wrapper is configured globally, not per-project)
- [ ] `Cargo.lock` is committed (not in .gitignore)

### 3. Justfile (required for all projects)
- [ ] `Justfile` exists at project root
- [ ] Has `ci-fast` recipe (hygiene + fmt + lint + build + test-quick)
- [ ] Has `ci-deep` recipe (ci-fast + test-full + coverage + security + mutants + docs)
- [ ] Has language-appropriate recipes (Rust: fmt/lint/build/test/coverage/security/docs; Node: fmt-frontend/lint-frontend/test-frontend; Python: fmt-python/lint-python/test-python)
- [ ] `set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]` is present

### 4. Git Hooks
- [ ] `.githooks/pre-commit` exists and calls `just ci-fast`
- [ ] `.githooks/pre-push` exists and calls `just ci-deep`
- [ ] `git config core.hooksPath` returns `.githooks`
- [ ] Hook files are executable (chmod +x on Unix)

### 5. Node.js Checks (skip if no package.json)
- [ ] `pnpm-lock.yaml` exists (not package-lock.json or yarn.lock)
- [ ] `node_modules/` is in .gitignore
- [ ] `.prettierrc` exists
- [ ] `eslint.config.js` or equivalent exists

### 6. Python Checks (skip if no Python files)
- [ ] `pyproject.toml` exists (not just requirements.txt)
- [ ] `uv.lock` exists
- [ ] `ruff` is available (`uv run ruff --version`)
- [ ] `.venv/` is in .gitignore

### 7. Security
- [ ] `security/advisory-baseline.toml` exists (for Rust projects)
- [ ] `scripts/enforce_advisory_policy.py` exists (for Rust projects)

### 8. Repo Hygiene
- [ ] `scripts/hygiene.sh` exists
- [ ] No files > 10MB in tracked files (excluding vendor/)
- [ ] `.gitignore` includes: target/, node_modules/, dist/, build/, __pycache__/, .venv/
- [ ] No merge conflict markers in any tracked file

### 9. Governance
- [ ] `CLAUDE.md` or `AGENTS.md` exists
- [ ] `.env` is in .gitignore (if .env.example exists)

### 10. Smoke Test
- [ ] `just ci-fast` runs (report output, do not block on failures)

## Summary
Print a table: Item | Status (PASS/FIX/FAIL/SKIP) | Notes
