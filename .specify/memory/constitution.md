<!--
SYNC IMPACT REPORT
==================
Version change: 1.1.0 → 1.1.1
Modified principles:
  - Governance (compliance review wording clarified to require plan-level constitution checks)
Added sections: None.
Removed sections: None.
Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check now enumerates the six project principles.
  ✅ .specify/templates/spec-template.md — Validated against current constitution; no template changes required.
  ✅ .specify/templates/tasks-template.md — Added required verification and design review task guidance.
Follow-up TODOs: None.
-->

# Meshtastic Android (KMP) Constitution

## Core Principles

### I. Kotlin Multiplatform Core

Business logic MUST reside exclusively in `commonMain` source sets. KMP-equivalent libraries
MUST be used in place of JVM/Android-specific APIs:

- MUST use Okio (not `java.io`), Ktor (not `java.net`/OkHttp in common), Mutex/atomicfu
  (not `java.util.concurrent`), Room KMP, DataStore KMP, and Koin 4.2+.
- MUST NOT import `java.*` or `android.*` in any `commonMain` module.
- Platform-specific implementations belong in `androidMain`/`desktopMain` actual
  declarations only.
- Rationale: The project goal is multi-platform parity (Android, Desktop, iOS). Framework
  bleed in `commonMain` breaks compilability on non-Android targets and undermines the
  entire decoupling effort.

### II. Zero Lint Tolerance

All code contributions MUST pass static analysis before merge:

- `./gradlew spotlessApply` MUST be run and `spotlessCheck` MUST pass with no violations.
- `detekt` MUST pass with no new violations introduced.
- A task or PR is considered incomplete if either check fails.
- Rationale: Consistent code style and static analysis gates prevent technical debt
  accumulation and catch bugs that tests alone miss.

### III. Compose Multiplatform UI

All UI MUST use JetBrains Compose Multiplatform, not Android-only Jetpack Compose APIs:

- MUST use `MeshtasticNavDisplay` and `NavigationBackHandler` for navigation across all
  entry points.
- Floats MUST be pre-formatted using `NumberFormatter.format()` before display in any
  composable.
- UI MUST compile and render correctly on all supported targets (Android, Compose Desktop).
- Rationale: Compose Multiplatform ensures UI consistency across platforms and enforces the
  project's multi-platform architecture goal.

### IV. Privacy First

The application handles sensitive mesh network data; user privacy MUST be protected at all
times:

- MUST NOT log or expose PII, location data, or cryptographic keys in logs, crash reports,
  or any debug output.
- Secrets MUST be git-ignored and MUST NOT be committed to the repository under any
  circumstances.
- `core/proto` is a read-only upstream submodule (`meshtastic/protobufs`). MUST NOT modify
  `.proto` files directly; proto changes require an upstream issue labeled `upstream`.
- Rationale: Meshtastic users rely on the mesh for private, off-grid communications. Data
  leaks could endanger users in sensitive or adversarial deployments.

### V. Design Standards Compliance

All user-facing UI MUST conform to the Meshtastic Client Design Standards:

- The canonical reference lives at:
  `https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md`
- New screens and significant UI changes MUST be reviewed against the design standards
  before merge.
- Deviations from the design standards require explicit justification in the PR description
  with a rationale for why the standard cannot or should not be followed.
- Rationale: Consistent cross-platform UX across Android, iOS, and other clients ensures
  users have a predictable experience regardless of platform. The design standards are
  maintained collaboratively across all Meshtastic client teams.

### VI. Verify Before Push

Local verification MUST complete successfully before any `git push`:

- MUST run `./gradlew spotlessApply spotlessCheck detekt` plus relevant module `:test`
  tasks for all modules touched.
- After pushing, CI status MUST be confirmed via `gh pr checks <PR>` or
  `gh run list --branch <branch> --limit 5`. Phrases like "CI should be green" are
  explicitly prohibited.
- Rationale: CI has failed repeatedly due to skipped local checks. Verification is a hard
  gate, not an optimistic assumption.

## Development Workflow

The following workflow steps are non-negotiable for all contributors and agents:

- **Bootstrap First**: The mandatory bootstrap steps in `.skills/project-overview/SKILL.md`
  MUST be executed before any build operation in a new session.
- **Baseline Verification**: Before any PR is opened or pushed, run:
  `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
- **String Resources**: After adding any string resource, run
  `python3 scripts/sort-strings.py` to maintain alphabetical organization and regenerate
  `strings-index.txt`. Consult `strings-index.txt` before reading large string files.
- **Memory Persistence**: `.agent_memory/session_context.md` MUST be updated at the end of
  every agent session or major task to preserve context across sessions.
- **Plan Before Execution**: Complex refactors MUST have a plan written in `.agent_plans/`
  (git-ignored) before execution begins.
- **Context Discipline**: Agents MUST NOT read binary files (PNG, MP3, etc.) or vacuum the
  entire codebase for localized fixes. Limit context reads to relevant modules.

## Architecture Constraints

The following module boundaries and technology choices are fixed for this project:

- **KMP Modules**: `core:domain` (business logic), `core:data` (repositories),
  `core:database` (Room KMP), `core:datastore` (preferences), `core:network` (Ktor),
  `core:ble` (Kable multiplatform BLE).
- **State Management**: Unidirectional Data Flow (UDF) with ViewModels, Kotlin Coroutines,
  and Flow. No reactive frameworks other than Coroutines/Flow in `commonMain`.
- **Dependency Injection**: Koin 4.2+ with Koin Annotations and the K2 Compiler Plugin.
  No alternative DI framework may be introduced.
- **Navigation**: JetBrains Navigation 3 for multiplatform routing with RESTful deep
  linking. All navigation MUST use `MeshtasticNavDisplay`.
- **Data Protocol**: Protobuf for device communications (read-only upstream submodule).
  Room KMP for local persistence. DataStore for user preferences.
- **Language & Toolchain**: Kotlin 2.3+ targeting JDK 21. Java source files MUST NOT be
  introduced in KMP modules.

## Governance

This constitution supersedes all other practices, coding guidelines, and agent instructions.
`AGENTS.md` is the authoritative source of truth. The files
`.github/copilot-instructions.md`, `CLAUDE.md`, and `GEMINI.md` MUST redirect to
`AGENTS.md` and MUST NOT diverge from it.

**Amendment Procedure**:
1. Propose the amendment with rationale and a migration plan in a PR description.
2. Update `AGENTS.md` and this constitution atomically in the same commit.
3. Increment `CONSTITUTION_VERSION` per the versioning policy below.
4. All PRs and code reviews MUST verify compliance with the current constitution version.

**Versioning Policy**:
- MAJOR: Backward-incompatible principle removal or fundamental redefinition.
- MINOR: New principle or section added, or materially expanded guidance.
- PATCH: Clarifications, wording fixes, or non-semantic refinements.

**Compliance Review**: Every implementation plan and PR description MUST include a
Constitution Check confirming all six principles were evaluated. Complexity violations
require explicit justification in the Complexity Tracking table of the plan document.

**Version**: 1.1.1 | **Ratified**: 2026-05-07 | **Last Amended**: 2026-05-08
