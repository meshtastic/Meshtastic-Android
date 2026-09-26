---
applyTo: "**/*.yml"
excludeAgent: "code-review"
---

# CI Workflow Rules

- Prefer explicit Gradle task paths (`androidApp:lintFdroidDebug`) over shorthand (`lintDebug`).
- CI uses `.github/ci-gradle.properties` — don't assume local `gradle.properties` values.
- CI passes `-Pci=true` to enable full processor usage via `maxParallelForks`.
- Use `fetch-depth: 0` only where needed (spotless ratcheting, version code). Use `fetch-depth: 1` otherwise.
- Runner labels are named by tier here; the workflows carry the versions.
- Desktop build matrix: `macos-latest`, `windows-latest`, and Ubuntu x64 and arm64; `build-desktop`
  in `reusable-check.yml` lists the labels.
- Lightweight jobs off the required-check path (labelers, run-cancellers, changelog/release
  cleanup): use `ubuntu-slim`. It is container-backed and starts in seconds, but it is
  single-CPU, unprivileged, x64-only, and its 15-minute job cap is a hard platform limit. It
  fits API/script work (`gh`, `jq`, `git`, stdlib `python3`, `github-script`) and nothing that
  needs `sudo`, `apt-get`, Docker, a mounted filesystem, or a long full-history clone.
- Lightweight jobs that break any of those constraints, and the required `Check Workflow Status`
  gates, which must not queue on slim's separate pool: use the pinned Ubuntu LTS arm label.
- Gradle-heavy jobs: use the pinned Ubuntu LTS x64 label that `reusable-check.yml` uses.
