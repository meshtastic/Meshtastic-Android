---
applyTo: "**/*.yml"
excludeAgent: "code-review"
---

# CI Workflow Rules

- Prefer explicit Gradle task paths (`app:lintFdroidDebug`) over shorthand (`lintDebug`).
- CI uses `.github/ci-gradle.properties` — don't assume local `gradle.properties` values.
- CI passes `-Pci=true` to enable full processor usage via `maxParallelForks`.
- Use `fetch-depth: 0` only where needed (spotless ratcheting, version code). Use `fetch-depth: 1` otherwise.
- Desktop build matrix: `macos-latest`, `windows-latest`, `ubuntu-24.04`, `ubuntu-24.04-arm`.
- Lightweight jobs (labelers, triage, stale): use `ubuntu-24.04-arm` runners.
- Gradle-heavy jobs: use `ubuntu-24.04` runners.
