# Agent Governance Cache

<!--
Sync Impact Report
==================
2026-08-27: hand-corrected. This file is generated evidence, but the captured
snapshot had rotted badly: it named eight workflows that do not exist, listed
`app/` (renamed `androidApp/` long ago) as a source path, recorded build-intermediate
zip-cache blobs and `.agent_refs/` firmware .cpp files as "MCP configs", and listed
seven top-level areas (`.agent_refs/`, `app/`, `desktop/`, `docs-site/`, `ios/`,
`iosApp/`, `offline-repository/`) that are not in the tree.
Corrected by hand rather than by running
`.specify/extensions/agent-governance/scripts/refresh_agent_governance.py`, because a
full refresh also appends a ~95-line managed SPECKIT GOVERNANCE section to
.github/copilot-instructions.md, which this repo deliberately does not carry.
-->

## Final Output

- active agent platform governance file
- managed `SPECKIT GOVERNANCE` section
- cache: internal

## Directory Governance

- Responsibility: one primary purpose per directory.
- Depth: 2.
- Coverage: include visible, hidden, generated, cache, config/env, tool, and agent directories.
- Mixed concerns: follow existing repo convention or split responsibility.
- Change impact: review linked code, tests, docs, config/env, data, assets, generated files, and tool outputs; update only when in scope and authorized.

## Repository Evidence

- README: `README.md`
- Package manifest: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`
  (`docs/Gemfile` + `docs/Gemfile.lock` belong to the Jekyll docs site only)
- Task runners: Gradle wrapper (`gradlew`), convention plugins in `build-logic/`
- CI workflows: `.github/workflows/create-or-promote-release.yml`,`.github/workflows/dependency-graph-submit.yml` `.github/workflows/docs-deploy.yml`,`.github/workflows/docs-release.yml` `.github/workflows/main-check.yml`,`.github/workflows/merge-queue.yml` `.github/workflows/msstore-publish.yml`,`.github/workflows/post-release-cleanup.yml` `.github/workflows/pr-closed-cleanup.yml`,`.github/workflows/promote.yml` `.github/workflows/pull-request-target.yml`,`.github/workflows/pull-request.yml` `.github/workflows/release.yml`,`.github/workflows/reusable-check.yml` `.github/workflows/scheduled-baseline.yml`,`.github/workflows/scheduled-updates.yml` `.github/workflows/stale.yml`,`.github/workflows/update-changelog.yml` `.github/workflows/verify-flatpak.yml`,`.github/workflows/winget-publish.yml`
- Source paths: `androidApp/`, `desktopApp/`, `core/`, `feature/`, `build-logic/`, `scripts/`
- Test paths: `**/src/commonTest/`, `**/src/jvmTest/`, `**/src/androidHostTest/`,
  `screenshot-tests/`, `docs-screenshots/`, `baselineprofile/`, `core/konsist/`
- Repository areas: (see "Repository Areas" section below for top-level listing)
- Existing agent context files: `.github/copilot-instructions.md`, `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`
- Contextual instruction files: `.github/instructions/*.instructions.md`
- Repository-local skills: `.claude/skills/baseline/SKILL.md`,`.claude/skills/crashlytics-triage/SKILL.md` `.claude/skills/pr/SKILL.md`,`.claude/skills/proto-bump/SKILL.md` `.skills/ci-cost-control/SKILL.md`,`.skills/code-review/SKILL.md` `.skills/compose-ui/SKILL.md`,`.skills/design-standards/SKILL.md` `.skills/implement-feature/SKILL.md`,`.skills/kmp-architecture/SKILL.md` `.skills/navigation-and-di/SKILL.md`,`.skills/new-branch/SKILL.md` `.skills/project-overview/SKILL.md`,`.skills/speckit/SKILL.md` `.skills/testing-ci/SKILL.md`
- MCP configs: `.mcp.json`
- Active integration: `copilot`
- Resolved context file: `AGENTS.md`

## Repository Areas

**Policy**: Subdirectories inherit parent area governance rules. When modifying a subdirectory,
review the parent area's context for impact. Top-level directories require review before
changing linked areas; child directories change with their parent.

**Top-level areas requiring review**: `.claude/`, `.github/`, `.skills/`, `.specify/`,
`androidApp/`, `baselineprofile/`, `build-logic/`, `config/`, `core/`, `desktopApp/`,
`docs/`, `docs-screenshots/`, `fastlane/`, `feature/`, `gradle/`, `obtainium/`,
`screenshot-tests/`, `scripts/`, `specs/`

`.agent_memory/` and `.agent_plans/` are git-ignored agent scratch — never staged, never
reviewed.

## Development Commands

- Baseline: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
- Cross-target compile: `./gradlew kmpSmokeCompile`
- After adding strings: `python3 scripts/sort-strings.py`
- Docs checks: `node scripts/check-doc-coverage.js`, `node scripts/validate-doc-links.js`
- Full detail: `.skills/testing-ci/SKILL.md`

## Scope

- agent collaboration rules
- tool and MCP permissions
- write boundaries
- skill invocation contracts
- project governance: external

## Write Boundaries

- Scope: active task only
- Preserve: user-authored edits
- Preserve managed markers verbatim: `<!-- SPECKIT GOVERNANCE START -->` and `<!-- SPECKIT GOVERNANCE END -->`
- Protected files: implementation, CI, MCP config, secrets, permissions, tool settings
- Protected-file writes: explicit user request only

## Skill Contract

- Repository-local skill specs should declare purpose, trigger, allowed read paths, allowed write paths, forbidden paths, outputs, and validation command.

## MCP Policy

- Default: read-only
- Mutation: explicit user intent
- External writes: target, action, result
- Secrets: never log, never write

## Validation

- changed files
- commands run
- tests/validation result
- unresolved risks
