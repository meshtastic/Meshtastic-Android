# Meshtastic Android — Copilot Instructions

> **`AGENTS.md` is the source of truth** for rules, architecture, and conventions. This file is a compact Copilot quick-reference plus Copilot-only behavior rules — it deliberately does not restate AGENTS.md. For build/test detail see `.skills/testing-ci/`; for the codebase map and bootstrap see `.skills/project-overview/`.

## Build, Test & Lint (essentials)

Requires JDK 25 and `ANDROID_HOME`. Per fresh clone:
```bash
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```
```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests   # full local verification (run before push)
./gradlew :core:data:allTests                                # single KMP module
./gradlew :androidApp:testFdroidDebugUnitTest                # single Android-only module
./gradlew kmpSmokeCompile                                    # cross-platform compile check, no tests
```
> Both `test` AND `allTests` are needed — `allTests` covers KMP modules, `test` covers pure-Android modules.

**KMP vs Android-only task naming** (wrong name silently skips tests or fails resolution): KMP modules (`core:*`, `feature:*`) use `:module:allTests` and `:module:compileKotlinJvm`; Android-only modules (`androidApp`, `desktopApp`, `core:barcode`) use `:module:testFdroidDebugUnitTest` (plain `:desktopApp:test` for the JVM-only desktop module). `:module:detekt` is the lifecycle task for both — never `detektMain`/`detektDebug`. Full matrix and pitfalls: `.skills/testing-ci/`.

Architecture, flavors, conventions, branch naming, protos, coding rules: **see `AGENTS.md`**. Contextual `.github/instructions/` files enforce conventions scoped to specific source sets.

## Copilot-Only Behavior Rules

These are specific to the Copilot CLI environment and are not covered in AGENTS.md.

- **Do it right the first time.** When refactoring, implement the correct solution fully — don't defer improvements as "out of scope". Research the proper API before implementing.
- **Preserve commit history.** Always make new commits. Never `--amend`, `rebase -i`, squash, or `--force-with-lease` unless the user explicitly requests it.
- **Workflow-scope push limit.** The Copilot CLI OAuth token cannot push to `.github/workflows/`. If a push fails with a scope/permission error on workflow files, tell the user to push manually (`gh auth refresh -s workflow && git push`) — do not retry or work around it.
- **No destructive git in parallel agents.** Agents sharing a worktree must never run `git reset --hard`, `git clean`, or `git checkout -- .`. Commit work before any index/working-tree operation.
- **Audit before applying.** When porting or migrating external code, evaluate each change for relevance and correctness in our context — don't blindly copy.

## Context & Cost Efficiency

- **Compact before research phases.** Open-ended research/exploration generates high-volume tool output — compact first.
- **Split sessions at phase boundaries.** Don't keep one session alive across multiple discrete deliverables (PR opened, spec finalized, merged). A compact summary in a fresh session is cheaper than carrying accumulated history.
- **Don't re-inject skill context.** If a skill's context is already in the session, reference it rather than re-injecting the full payload (a brief summary suffices after compaction).
- **PR check-ins get their own session.** Merge/CI-status polling needs only PR metadata — run it in a short-lived session, not the dev session.
- **Compact by ~turn 12.** Later turns pay for the full accumulated history; don't let exploratory sessions run 15+ turns uncompacted.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan at
specs/20260711-153545-message-markdown-styling/plan.md
<!-- SPECKIT END -->
