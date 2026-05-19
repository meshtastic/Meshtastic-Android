---
title: Developer Guide
layout: default
nav_order: 2
has_children: true
parent: ""
---

# Developer Guide

Technical documentation for contributing to the Meshtastic Android and Desktop app.

---

## Before You Open a PR

Things that trip up first-time contributors — check these before requesting review:

- **Formatting passes** — run `./gradlew spotlessApply` to auto-format, then verify with `spotlessCheck`
- **Detekt passes** — run `./gradlew detekt` and fix all reported issues
- **All tests pass** — run `./gradlew test allTests` (both are needed: `test` covers Android-only modules, `allTests` covers KMP)
- **Screenshot tests pass** — if you touched any Compose UI, run `./gradlew :screenshot-tests:validateFdroidDebugScreenshotTest` and update reference images if needed
- **Proto submodule unchanged** — `core/proto/` is a read-only git submodule. Never modify proto files directly
- **Docs updated** — if you changed user-visible UI, update the corresponding page under `docs/user/`. The `UI & Docs Governance` CI workflow will flag the PR if you didn't. Add the `skip-docs-check` label if it genuinely isn't needed
- **Previews updated** — if you changed UI composables, update the corresponding `*Previews.kt` file and screenshot tests. The governance workflow will post an advisory. Add `skip-preview-check` to dismiss
- **Branch naming** — branches must start with `feat/`, `fix/`, `chore/`, `docs/`, `build/`, `ci/`, `refactor/`, `test/`, or `deps/`

---

## What's New for Developers

<!-- DEV_WHATS_NEW_START -->
<!-- Add new entries at the top. Format:
**Month YYYY** — [Page or area](relative/path) — One sentence on what changed architecturally or procedurally.
Keep the last 5–8 entries and trim older ones from the bottom.
-->

**May 2026** — [Measurement & Formatting](developer/measurement) — New page documenting the `MetricFormatter` API, locale-aware unit conversion patterns, and how to add new measurement types.

**May 2026** — [Testing](developer/testing) — Compose Preview Screenshot Testing (CST) integrated: `screenshot-tests/` module, `@PreviewTest` wrappers, CI validation, docs asset pipeline.

**May 2026** — In-app documentation system added: markdown source under `docs/user/` and `docs/developer/` is bundled as Compose Resources and rendered via `multiplatform-markdown-renderer-m3`.

**May 2026** — [Architecture](developer/architecture) — Documented KMP module layering, Navigation 3 patterns, and feature module conventions.

**May 2026** — [Contributing](developer/contributing) — Established docs governance CI workflow for PRs that change UI without updating docs.

<!-- DEV_WHATS_NEW_END -->

