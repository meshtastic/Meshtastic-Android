---
title: Developer Guide
layout: default
nav_order: 2
has_children: true
---

# Developer Guide

Technical documentation for contributing to the Meshtastic Android and Desktop app.

---

## Before You Open a PR

Things that trip up first-time contributors — check these before requesting review:

- **Formatting passes** — run `./gradlew spotlessApply` to auto-format, then verify with `spotlessCheck`
- **Detekt passes** — run `./gradlew detekt` and fix all reported issues
- **All tests pass** — run `./gradlew test allTests` (both are needed: `test` covers Android-only modules, `allTests` covers KMP)
- **Screenshot tests pass** — if you touched any Compose UI, run `./gradlew :screenshot-tests:validateDebugScreenshotTest` and update reference images if needed
- **Protos are an external dependency** — protobuf models come from the `org.meshtastic:protobufs` Maven artifact (pinned in `gradle/libs.versions.toml`); change protos upstream and bump the version, never edit generated code locally
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

**June 2026** — [Architecture](developer/architecture) / [Codebase](developer/codebase) — Protos migrated from the `core/proto` git submodule to the `org.meshtastic:protobufs` Maven artifact; there is no longer a local proto module to build or sync.

**June 2026** — AIDL/`IMeshService` removed (#5586). The mesh service is now in-process only, driven entirely through `RadioController` — no cross-process binder, no `aidl` stubs.

**June 2026** — [Testing](developer/testing) — Split the screenshot pipeline: the new generate-only `:docs-screenshots` module holds doc-framed compositions, while `:screenshot-tests` stays the CI visual-regression gate — so reframing a doc image no longer churns a test baseline.

**June 2026** — New feature modules: `feature:discovery` (mesh network discovery, #5275) and `feature:car` (Android Auto / Car App Library, google flavor only, #5633).

**June 2026** — [Testing](developer/testing) — Added the `:baselineprofile` module (#5735): a Macrobenchmark cold-start journey generates a Baseline Profile for `:androidApp` to AOT-compile hot startup paths.

**June 2026** — [Persistence](developer/persistence) — FTS5 full-text message search (#5373): a `PacketFts` virtual table mirrors `Packet.messageText`, kept in sync by Room-managed triggers.

**May 2026** — [Measurement & Formatting](developer/measurement) — New page documenting the `MetricFormatter` API, locale-aware unit conversion patterns, and how to add new measurement types.

**May 2026** — [Testing](developer/testing) — Compose Preview Screenshot Testing (CST) integrated: `screenshot-tests/` module, `@PreviewTest` wrappers, CI validation, docs asset pipeline.

<!-- DEV_WHATS_NEW_END -->

