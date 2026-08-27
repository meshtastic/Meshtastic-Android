<!--
SYNC IMPACT REPORT
==================
Version change: 1.3.3 → 1.3.4
Modified principles:
  - I. Kotlin Multiplatform Core: "androidMain/desktopMain" → "androidMain/jvmMain" (no desktopMain source set exists)
  - VI. Documentation Freshness: rewrote the governance rules to describe the tooling that
    exists. The docs checks are three Node scripts run on demand, not CI gates: there is no
    docs-governance workflow, no blocking staleness gate and no skip-docs-check label, and
    no workflow references check-doc-coverage.js, validate-doc-links.js or
    check-doc-freshness.js. Doc paths are docs/en/user/ and docs/en/developer/, not
    docs/user/ and docs/developer/. sync-android-docs.js discovers slugs from the source
    tree (discoverSlugs), so the KNOWN_*_SLUGS sets are no longer hand-maintained.
Modified sections: None.
Added sections: None.
Removed sections: None.
Templates requiring updates:
  - .skills/speckit/SKILL.md (version 1.3.3 → 1.3.4; principle VI no longer "blocking CI gate")
  - .specify/templates/{plan,checklist}-template.md (drop the skip-docs-check label from the
    Principle VI gate; it does not exist)
Root cause (recorded because this was not aspirational text):
  docs-governance.yml existed — 419 lines, built by the app-docs-markdown spec (tasks T206,
  T250, T262, T280, still marked [X] complete in
  specs/20260507-161858-app-docs-markdown/tasks.md). It was deleted on 2026-06-28 by #6000
  "chore(ci): prune dead workflows", six days after this constitution was last amended, in the
  same commit that removed dependency-submission.yml, models_issue_triage.yml,
  models_pr_triage.yml and moderate.yml — exactly the workflows
  .specify/memory/agent-governance.md was still listing. One CI prune, no governance update,
  two documents left asserting a gate that had stopped running.
Follow-up TODOs:
  - Principle VI's checks are advisory by construction. Restoring the gate means restoring a
    workflow that was deliberately pruned as dead, so that is a decision to take rather than a
    repair to make; if it is taken, re-amend this principle to match.
-->

# Meshtastic Android (KMP) Constitution

## Core Principles

### I. Kotlin Multiplatform Core

Business logic MUST reside exclusively in `commonMain` source sets. KMP-equivalent libraries
MUST be used in place of JVM/Android-specific APIs:

- MUST use Okio (not `java.io`), Ktor (not `java.net`/OkHttp in common), Mutex/atomicfu
  (not `java.util.concurrent`), Room KMP, DataStore KMP, and Koin 4.2+.
- MUST NOT import `java.*` or `android.*` in any `commonMain` module.
- Platform-specific implementations belong in `androidMain`/`jvmMain` actual
  declarations only (there is no `desktopMain` source set; Desktop is the `jvm` target).
<!-- Rationale: Multi-platform parity (Android, Desktop, iOS). Framework bleed in commonMain breaks compilability on non-Android targets. -->

### II. Zero Lint Tolerance

All code contributions MUST pass static analysis before merge:

- `./gradlew spotlessApply` MUST be run and `spotlessCheck` MUST pass with no violations.
- `detekt` MUST pass with no new violations introduced.
- A task or PR is considered incomplete if either check fails.
<!-- Rationale: Consistent code style and static analysis gates prevent technical debt accumulation. -->

### III. Compose Multiplatform UI

All UI MUST use JetBrains Compose Multiplatform, not Android-only Jetpack Compose APIs:

- MUST use `MeshtasticNavDisplay` and `NavigationBackHandler` for navigation across all
  entry points.
- Floats MUST be pre-formatted using `NumberFormatter.format()` before display in any
  composable.
- UI MUST compile and render correctly on all supported targets (Android, Compose Desktop).
<!-- Rationale: Compose Multiplatform ensures UI consistency across platforms. -->

### IV. Privacy First

The application handles sensitive mesh network data; user privacy MUST be protected at all
times:

- MUST NOT log or expose PII, location data, or cryptographic keys in logs, crash reports,
  or any debug output.
- Secrets MUST be git-ignored and MUST NOT be committed to the repository under any
  circumstances.
- Protobuf models come from the upstream `org.meshtastic:protobufs` Maven dependency (pinned
  in `gradle/libs.versions.toml`). MUST NOT hand-edit generated proto; proto changes require
  an upstream change and a dependency version bump.
<!-- Rationale: Meshtastic users rely on the mesh for private, off-grid communications. Data leaks could endanger users in sensitive deployments. -->

### V. Design Standards Compliance

All user-facing UI MUST conform to the Meshtastic Client Design Standards:

- The canonical reference lives at:
  `https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md`
- New screens and significant UI changes (any screen with ≥3 composables or a new
  navigation destination) MUST be reviewed against the design standards before merge.
- Deviations from the design standards require explicit justification in the PR description
  with a rationale for why the standard cannot or should not be followed.
- Features that affect multiple platforms (messaging, settings, telemetry, etc.) MUST
  reference an existing cross-platform behavior spec in
  [`meshtastic/design/features/`](https://github.com/meshtastic/design/tree/master/features),
  or create one using the `TEMPLATE.md` in that directory before writing the
  Android implementation spec. Platform-specific-only features (e.g., Android widget,
  Wear OS tile) may mark the `Cross-Platform Spec` field as N/A with justification.
<!-- Rationale: Consistent cross-platform UX ensures users have a predictable experience regardless of platform. -->

### VI. Documentation Freshness

In-app documentation MUST remain accurate and current as the codebase evolves.
Documentation changes propagate to **three consumers** — all three MUST be considered:

1. **In-app docs browser** — `syncDocsToComposeResources` copies `docs/` into Compose
   Resources at build time. Changes are bundled into the app automatically.
2. **Jekyll site** (GitHub Pages) — `docs/` is served directly. The `docs-deploy.yml`
   workflow rebuilds on push to `main`.
3. **Docusaurus site** (meshtastic.org) — `scripts/sync-android-docs.js` transforms
   `docs/` for the external site. Runs weekly via the `meshtastic/meshtastic` repo.

Governance rules:

- Every doc page MUST include a `last_updated` frontmatter field (YYYY-MM-DD).
  Update this field whenever page content changes.
- A PR that changes user-facing behaviour MUST update the corresponding page(s) under
  `docs/en/user/` or `docs/en/developer/`, or state in the PR description why no page
  changed.
- Every user-facing feature module MUST have a corresponding page under `docs/en/user/`
  or `docs/en/developer/`.
- New doc pages MUST be registered in `DocBundleLoader.kt` (the in-app index) with a
  `navOrder`. Jekyll picks new pages up automatically via `_config.yml` scope-based
  defaults, and `sync-android-docs.js` discovers slugs from the source tree — neither
  needs a manual registration step.
- Image references MUST use root-relative paths (`/assets/screenshots/filename.png`) so
  they resolve correctly in both Jekyll and the in-app renderer. The sync script rewrites
  these to Docusaurus paths automatically.
- English pages are the only ones written by hand. `docs/<locale>/user/` is downloaded
  from Crowdin (`crowdin.yml`) — never hand-edit a locale page; deleting an English page
  means deleting its locale copies in the same commit.

Verification tooling (run on demand; **none of these is wired into CI** — an inaccurate
page will not fail a build, which is why the rules above are on the author):

```bash
node scripts/check-doc-coverage.js    # every user-facing feature module has a page
node scripts/validate-doc-links.js    # internal cross-references and image paths resolve
node scripts/check-doc-freshness.js   # advisory: pages >180 days old, or missing last_updated
```
<!-- Rationale: Documentation drift misleads users and increases support burden. Three distinct consumers means changes must be verified across all delivery channels. -->

### VII. Verify Before Push

Local verification MUST complete successfully before any `git push`:

- MUST run `./gradlew spotlessApply spotlessCheck detekt` plus relevant module `:test`
  tasks for all modules touched.
- After pushing, CI status MUST be confirmed via `gh pr checks <PR>` or
  `gh run list --branch <branch> --limit 5`. Phrases like "CI should be green" are
  explicitly prohibited.
<!-- Rationale: Verification is a hard gate, not an optimistic assumption. Skipped local checks are the leading cause of CI failures. -->

## Development Workflow

Non-negotiable workflow steps are defined in `AGENTS.md` `<process_essentials>`. Key
requirements: bootstrap before build, baseline verification before push, sort-strings after
adding resources, update `.agent_memory/session_context.md` per session, plan complex
refactors (touching ≥3 modules or >200 LOC changed) in `.agent_plans/`, limit context
reads to relevant modules.

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
- **Data Protocol**: Protobuf for device communications (the `org.meshtastic:protobufs`
  Maven dependency). Room KMP for local persistence. DataStore for user preferences.
- **Language & Toolchain**: Kotlin 2.4+ targeting JDK 21. Java source files MUST NOT be
  introduced in KMP modules.

## Operational Standards

The following coding standards are enforced by contextual instruction files
(`.github/instructions/`) scoped to relevant source sets. They are acknowledged by this
constitution but defined and maintained in their respective files:

- `safeCatching {}` over `runCatching {}` in coroutine/suspend contexts
- `org.meshtastic.core.common.util.ioDispatcher` over `Dispatchers.IO`
- `MeshtasticIcons` (from `core/ui/icon/`) over `material.icons.Icons`
- `MetricFormatter` for display strings (temperature, voltage, percent, signal)
- `stringResource(Res.string.key)` with `python3 scripts/sort-strings.py` after additions
- `kotlinx.coroutines.CancellationException` (not `kotlin.coroutines.cancellation.*`)
- Branch naming: `feat/`, `fix/`, `chore/`, `docs/`, `build/`, `ci/`, `refactor/`,
  `test/`, `deps/`, or numeric spec prefix; always off `origin/main`

## Governance

This constitution is the canonical governance document and supersedes all other practices,
coding guidelines, and agent instructions. `AGENTS.md` is the agent-facing operational
summary derived from this constitution. The files `.github/copilot-instructions.md`,
`CLAUDE.md`, and `GEMINI.md` MUST redirect to `AGENTS.md` and MUST NOT diverge from it.

**Amendment Procedure**:
1. Propose the amendment with rationale and a migration plan in a PR description.
2. Update `AGENTS.md` and this constitution atomically in the same commit.
3. Update all downstream references in the same commit:
   - `.skills/speckit/SKILL.md` (principle count and descriptions)
   - `.specify/templates/checklist-template.md` (checklist items)
   - `.specify/templates/plan-template.md` (Constitution Check section)
   - The SYNC IMPACT REPORT comment at the top of this file
4. Increment `CONSTITUTION_VERSION` per the versioning policy below.
5. All PRs and code reviews MUST verify compliance with the current constitution version.

**Versioning Policy**:
- MAJOR: Backward-incompatible principle removal or fundamental redefinition.
- MINOR: New principle or section added, or materially expanded guidance.
- PATCH: Clarifications, wording fixes, or non-semantic refinements.

**Compliance Review**: Every implementation plan and PR description MUST include a
Constitution Check confirming all seven principles were evaluated. Complexity violations
require explicit justification in the Complexity Tracking table of the plan document.

**Version**: 1.3.4 | **Ratified**: 2026-05-07 | **Last Amended**: 2026-08-27
