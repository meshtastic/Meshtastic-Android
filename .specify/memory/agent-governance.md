# Agent Governance Cache

<!--
Sync Impact Report
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
- Package manifest: `Gemfile`, `build.gradle.kts`
- Lockfiles: `Gemfile.lock`
- Task runners: none detected
- CI workflows: `.github/workflows/create-or-promote-release.yml`, `.github/workflows/dependency-submission.yml`, `.github/workflows/docs-deploy.yml`, `.github/workflows/docs-governance.yml`, `.github/workflows/docs-release.yml`, `.github/workflows/main-check.yml`, `.github/workflows/merge-queue.yml`, `.github/workflows/models_issue_triage.yml`, `.github/workflows/models_pr_triage.yml`, `.github/workflows/moderate.yml`, `.github/workflows/post-release-cleanup.yml`, `.github/workflows/pr_enforce_labels.yml`, `.github/workflows/promote.yml`, `.github/workflows/publish-core.yml`, `.github/workflows/pull-request-target.yml`, `.github/workflows/pull-request.yml`, `.github/workflows/release.yml`, `.github/workflows/reusable-check.yml`, `.github/workflows/scheduled-updates.yml`, `.github/workflows/stale.yml`, `.github/workflows/sync-android-docs.yml`, `.github/workflows/update-changelog.yml`
- Source paths: `app/`, `scripts/`
- Test paths: `specs/`
- Repository areas: (see "Repository Areas" section below for top-level listing)
- Existing agent context files: `.github/copilot-instructions.md`, `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`
- Repository-local skills: `.agent_refs/mqttastic-client-kmp/.github/skills/mqtt-kmp/SKILL.md`, `.skills/ci-cost-control/SKILL.md`, `.skills/code-review/SKILL.md`, `.skills/compose-ui/SKILL.md`, `.skills/design-standards/SKILL.md`, `.skills/implement-feature/SKILL.md`, `.skills/kmp-architecture/SKILL.md`, `.skills/navigation-and-di/SKILL.md`, `.skills/new-branch/SKILL.md`, `.skills/project-overview/SKILL.md`, `.skills/speckit/SKILL.md`, `.skills/testing-ci/SKILL.md`
- MCP configs: `.agent_refs/firmware/src/modules/Telemetry/Sensor/MCP9808Sensor.cpp`, `.agent_refs/firmware/src/modules/Telemetry/Sensor/MCP9808Sensor.h`, `androidApp/build/intermediates/incremental/googleDebug-mergeJavaRes/zip-cache/ut0r9CiGRq6mCprTK2blRg==`, `app/build/intermediates/incremental/fdroidDebug-mergeJavaRes/zip-cache/mcpnRT6ZHkD92YpTv5y52w==`, `app/build/intermediates/incremental/googleDebug-mergeJavaRes/zip-cache/mcpnRT6ZHkD92YpTv5y52w==`, `app/build/intermediates/incremental/googleDebug-mergeJavaRes/zip-cache/ut0r9CiGRq6mCprTK2blRg==`
- Active integration: `copilot`
- Resolved context file: `AGENTS.md`

## Repository Areas

**Policy**: Subdirectories inherit parent area governance rules. When modifying a subdirectory,
review the parent area's context for impact. Top-level directories require review before
changing linked areas; child directories change with their parent.

**Top-level areas requiring review**: `.agent_memory/`, `.agent_plans/`, `.agent_refs/`,
`.github/`, `.specify/`, `androidApp/`, `app/`, `build-logic/`, `config/`, `core/`,
`desktop/`, `desktopApp/`, `docs/`, `docs-site/`, `fastlane/`, `feature/`, `gradle/`,
`ios/`, `iosApp/`, `offline-repository/`, `screenshot-tests/`, `scripts/`, `specs/`

## Development Commands

- none detected

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
