---
applyTo: "**/*.yml"
excludeAgent: "code-review"
---

# CI Workflow Rules

- Prefer explicit Gradle task paths (`androidApp:lintFdroidDebug`) over shorthand (`lintDebug`).
- CI uses `.github/ci-gradle.properties` — don't assume local `gradle.properties` values.
- CI passes `-Pci=true` to enable full processor usage via `maxParallelForks`.
- Use `fetch-depth: 0` only where needed (spotless ratcheting, version code). Use `fetch-depth: 1` otherwise.
- Desktop build matrix: `macos-latest`, `windows-latest`, `ubuntu-24.04`, `ubuntu-24.04-arm`.
- Lightweight jobs (status gates, labelers, triage, stale, run-cancellers, changelog/release
  cleanup): use `ubuntu-slim`. It is container-backed and starts in seconds, but it is
  single-CPU, unprivileged, x64-only, and its 15-minute job cap is a hard platform limit — so it
  fits API/script work (`gh`, `jq`, `git`, stdlib `python3`, `github-script`) and nothing that
  needs `sudo`, `apt-get`, Docker, a mounted filesystem, or a long full-history clone.
- Lightweight jobs that break any of those constraints: use `ubuntu-24.04-arm` runners.
- Gradle-heavy jobs: use `ubuntu-24.04` runners.
