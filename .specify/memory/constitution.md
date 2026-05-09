<!--
SYNC IMPACT REPORT
==================
Version change: 1.2.2 → 1.2.3
Modified principles:
  - Principle I: "where feasible" → "unless the platform API has no KMP
    equivalent" (interpretability fix)
  - Principle V: "significant UI changes" → "new screens, layout
    restructuring, or navigation changes" (interpretability fix)
  - Principle IX: "~5 logical commits" → "5 logical commits" (precision)
Compressed sections:
  - Development Workflow: replaced verbose procedural content with
    3-line summary referencing AGENTS.md and skills
  - Architecture Constraints: replaced verbose tech stack listings with
    constraint summaries referencing kmp-architecture SKILL
Removed sections: None (content preserved in AGENTS.md and skills)
Templates requiring updates: None.
Follow-up TODOs: None.
-->

# Meshtastic Android (KMP) Constitution

## Core Principles

### I. Kotlin Multiplatform Core

Business logic MUST reside exclusively in `commonMain` source sets.
KMP-equivalent libraries MUST be used in place of JVM/Android-specific
APIs:

- MUST use Okio (not `java.io`), Ktor (not `java.net`/OkHttp in common),
  Mutex/atomicfu (not `java.util.concurrent`), Room KMP, DataStore KMP,
  and Koin 4.2+ with K2 Compiler Plugin.
- MUST NOT import `java.*` or `android.*` in any `commonMain` module.
- Platform-specific implementations belong in `androidMain`/`desktopMain`
  actual declarations only.
- Platform capabilities MUST prefer interface + DI over `expect`/`actual`
  unless the platform API has no KMP equivalent.
- Rationale: The project goal is multi-platform parity (Android, Desktop,
  iOS). Framework bleed in `commonMain` breaks compilability on non-Android
  targets and undermines the entire decoupling effort.

### II. Zero Lint Tolerance

All code contributions MUST pass static analysis before merge:

- `./gradlew spotlessApply` MUST be run and `spotlessCheck` MUST pass with
  no violations.
- `detekt` MUST pass with no new violations introduced.
- A task or PR is considered incomplete if either check fails.
- Rationale: Consistent code style and static analysis gates prevent
  technical debt accumulation and catch bugs that tests alone miss.

### III. Compose Multiplatform UI

All UI MUST use JetBrains Compose Multiplatform, not Android-only Jetpack
Compose APIs:

- MUST use `MeshtasticNavDisplay` and `NavigationBackHandler` for
  navigation across all entry points (not Android's `BackHandler`).
- Navigation routes MUST be `@Serializable sealed interface` types defined
  in `core:navigation`.
- Feature navigation graphs MUST be extension functions on
  `EntryProviderScope<NavKey>` in `commonMain`.
- Floats MUST be pre-formatted using `NumberFormatter.format()` before
  display — CMP only supports `%N$s` (string) and `%N$d` (int) format
  specifiers.
- UI MUST compile and render correctly on all supported targets (Android,
  Compose Desktop).
- Material 3 Adaptive MUST be used for responsive layouts.
- Rationale: Compose Multiplatform ensures UI consistency across platforms
  and enforces the project's multi-platform architecture goal.

### IV. Privacy First

The application handles sensitive mesh network data; user privacy MUST be
protected at all times:

- MUST NOT log or expose PII, location data, or cryptographic keys in
  logs, crash reports, or any debug output.
- Secrets MUST be git-ignored and MUST NOT be committed to the repository
  under any circumstances.
- `core/proto` is a read-only upstream submodule (`meshtastic/protobufs`). MUST NOT modify
  `.proto` files directly; proto changes require an upstream issue labeled `upstream`.
- Rationale: Meshtastic users rely on the mesh for private, off-grid
  communications. Data leaks could endanger users in sensitive or
  adversarial deployments.

### V. Design Standards Compliance

All user-facing UI MUST conform to the Meshtastic Client Design Standards:

- The canonical reference lives at:
  `https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md`
- New screens, layout restructuring, or navigation changes MUST be
  reviewed against the design standards before merge.
- Deviations from the design standards require explicit justification in
  the PR description with a rationale for why the standard cannot or
  should not be followed.
- Rationale: Consistent cross-platform UX across Android, iOS, and other
  clients ensures users have a predictable experience regardless of
  platform. The design standards are maintained collaboratively across
  all Meshtastic client teams.

### VI. Verify Before Push

Local verification MUST complete successfully before any `git push`:

- MUST run the full verification command:
  `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
- Both `test` AND `allTests` are required: `allTests` covers KMP modules;
  `test` covers pure-Android modules. Neither alone catches everything.
- After pushing, CI status MUST be confirmed via `gh pr checks <PR>` or
  `gh run list --branch <branch> --limit 5`. Phrases like "CI should be
  green" are explicitly prohibited — check and confirm.
- Rationale: CI has failed repeatedly due to skipped local checks.
  Verification is a hard gate, not an optimistic assumption.

### VII. Coroutine Safety

Coroutine code MUST use project-standard utilities that preserve
structured concurrency and cancellation semantics:

- MUST use `safeCatching {}` (from `core:common`) instead of
  `runCatching {}` in suspend/coroutine code — `runCatching` silently
  swallows `CancellationException`, breaking structured concurrency.
- MUST use `org.meshtastic.core.common.util.ioDispatcher` — never
  `Dispatchers.IO` directly. Inject `CoroutineDispatchers` from
  `core:di` for testability.
- Rationale: Incorrect exception handling in coroutines causes silent
  failures and resource leaks. Centralizing dispatcher injection enables
  deterministic testing.

### VIII. Resource Discipline

All user-visible text, icons, and formatting MUST use project-standard
resource APIs:

- All strings MUST reside in
  `core/resources/src/commonMain/composeResources/values/strings.xml`.
  Use `stringResource(Res.string.key)` — never hardcoded strings in UI.
- After adding any string resource, MUST run
  `python3 scripts/sort-strings.py` to maintain alphabetical order and
  regenerate `strings-index.txt`.
- Consult `strings-index.txt` before reading large string files to
  minimize context waste.
- MUST use `MeshtasticIcons` (from `core/ui/icon/`) instead of
  `material.icons.Icons` for all iconography.
- Rationale: Centralized resources enable localization via Crowdin,
  maintain consistency with the Meshtastic design language, and prevent
  scattered hardcoded strings that resist translation.

### IX. Branch & Scope Hygiene

All branches and PRs MUST follow naming conventions and scope discipline:

- Branch names MUST start with one of: `feat/`, `fix/`, `chore/`, `docs/`,
  `build/`, `ci/`, `refactor/`, `test/`, `deps/`, or a numeric spec prefix
  (e.g., `002-feature-name` for spec-driven feature branches created by
  Spec Kit).
- Branches MUST be created off fetched upstream: `git fetch origin &&
  git checkout -b <name> origin/main`. Never branch from a personal
  fork's `main` — it may be stale.
- When a working branch grows beyond 5 logical commits or spans
  unrelated concerns, contributors MUST propose a fresh branch off
  `origin/main`, cherry-pick high-impact changes, and defer tangential
  work to follow-up PRs.
- Fixup commits MUST be squashed before pushing.
- Rationale: Focused branches reduce review burden, minimize merge
  conflicts, and maintain a clean git history. Upstream-based branching
  prevents stale-fork drift.

## Development Workflow

Follow the bootstrap, verification, and workflow procedures defined in
AGENTS.md `<process_essentials>` and the relevant `.skills/` playbooks
(`project-overview`, `testing-ci`, `new-branch`, `code-review`). All
workflow steps there are non-negotiable.

Key constraints reiterated here for governance compliance:
- Baseline verification (`spotlessApply spotlessCheck detekt assembleDebug
  test allTests`) MUST pass before any push or PR.
- Gradle task naming differs between KMP and Android-only modules — see
  `.github/copilot-instructions.md` §Gradle task naming for the
  authoritative table.
- Two app flavors (`fdroid` / `google`) use different signing keys. Only
  one can be installed at a time — uninstall before switching.

## Architecture Constraints

Module boundaries and technology choices are fixed. See AGENTS.md
`<context_and_memory>` and `.skills/kmp-architecture/SKILL.md` for the
full stack and module map.

Key constraints:
- No alternative DI, networking, or BLE frameworks may be introduced.
- Java source files MUST NOT be introduced in KMP modules.
- No reactive frameworks other than Coroutines/Flow in `commonMain`.
- All navigation MUST use `MeshtasticNavDisplay`.
- Gradle Kotlin DSL with convention plugins in `build-logic/`. Two
  flavors: `fdroid` (OSS) / `google` (Maps + DataDog).

## Governance

This constitution supersedes all other practices, coding guidelines, and
agent instructions. `AGENTS.md` is the authoritative source of truth. The
files `.github/copilot-instructions.md`, `CLAUDE.md`, and `GEMINI.md`
MUST redirect to `AGENTS.md` and MUST NOT diverge from it.

**Amendment Procedure**:
1. Propose the amendment with rationale and a migration plan in a PR
   description.
2. Update `AGENTS.md` and this constitution atomically in the same
   commit.
3. Increment `CONSTITUTION_VERSION` per the versioning policy below.
4. All PRs and code reviews MUST verify compliance with the current
   constitution version.

**Versioning Policy**:
- MAJOR: Backward-incompatible principle removal or fundamental
  redefinition.
- MINOR: New principle or section added, or materially expanded guidance.
- PATCH: Clarifications, wording fixes, or non-semantic refinements.

**Compliance Review**: Every PR description MUST include a Constitution
Check confirming all nine principles were evaluated. Complexity violations
require explicit justification in the Complexity Tracking table of the
plan document.

**Version**: 1.2.3 | **Ratified**: 2026-05-07 | **Last Amended**: 2026-05-09
