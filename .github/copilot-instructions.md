# Meshtastic Android — Copilot Instructions

> **Full rules**: `AGENTS.md` is the source of truth. This file is a compact quick-reference for build commands and task naming. For architecture, conventions, and workflow details, consult `AGENTS.md` and the `.skills/` playbooks.

## Build, Test & Lint

**Requires:** JDK 21, `ANDROID_HOME` set, proto submodule initialized.

```bash
# Bootstrap (run once per fresh clone)
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties

# Full local verification (formatting → lint → compile → tests)
./gradlew spotlessApply detekt assembleDebug test allTests

# Single module tests (KMP module)
./gradlew :core:data:allTests

# Single module tests (Android-only module like :app)
./gradlew :androidApp:testFdroidDebugUnitTest

# Cross-platform compilation check (no tests)
./gradlew kmpSmokeCompile

# Flavor-specific lint
./gradlew lintFdroidDebug lintGoogleDebug
```

> Both `test` AND `allTests` are needed. `allTests` covers KMP modules; `test` covers pure-Android modules. Neither alone catches everything.

### Gradle task naming (KMP vs Android-only)

KMP modules have different task names than pure-Android modules. Using the wrong name silently skips tests or fails resolution.

| Intent | KMP modules (`core:*`, `feature:*`) | Android-only (`app`, `core:api`, `core:barcode`) |
|--------|--------------------------------------|--------------------------------------------------|
| Run tests | `:module:allTests` | `:module:testFdroidDebugUnitTest` |
| Detekt | `:module:detekt` (lifecycle task) | `:module:detekt` |
| Compile check | `:module:compileKotlinJvm` | `:module:compileFdroidDebugKotlin` |

**Common mistakes:**
- ❌ `:core:network:detektMain` — does not exist in KMP; variants are `detektJvmMain`, `detektMetadataCommonMain`, etc. Use `:core:network:detekt` instead.
- ❌ `:feature:connections:testDebugUnitTest` — ambiguous in KMP modules. Use `:feature:connections:allTests`.
- ❌ `:feature:connections:compileFdroidDebugKotlin` — wrong for KMP. Use `:feature:connections:compileKotlinJvm` or `kmpSmokeCompile`.

## Quick Reference

- **Architecture**: KMP project (Android, Desktop, iOS). Business logic in `commonMain`; platform shells (`androidApp/`, `desktopApp/`) wire DI and host UI.
- **Flavors**: `fdroid` (OSS) / `google` (Maps + DataDog). Only one installable at a time (different signing keys).

> See `AGENTS.md` for full rules (verify before push, branch naming, protos, coding conventions).
> Contextual `.github/instructions/` files enforce conventions scoped to relevant source sets.

## Agent Behavior Rules

### Code quality — do it right the first time

When refactoring or improving code, implement the correct solution fully. Do not defer improvements as "too large", "out of scope", or "a separate refactoring". Research proper APIs and patterns before implementing — take the correct approach, not the simple one.

### Git hygiene — preserve commit history

Always make new commits. Never `--amend`, `rebase -i`, squash, or `--force-with-lease` unless the user explicitly requests it. Commit history is rollback safety — destroying it without permission is unacceptable.

### Workflow scope push limitation

The Copilot CLI OAuth token cannot push changes to `.github/workflows/` files (requires `workflow` scope). If a push fails with a permission/scope error on workflow files, immediately tell the user to push manually (`gh auth refresh -s workflow && git push`). Do not retry or attempt workarounds.

### Parallel agents — no destructive git operations

When dispatching parallel agents on a shared worktree, agents must never run `git reset --hard`, `git clean`, or `git checkout -- .`. Commit work immediately before running any git operations that could affect the index or working tree.

### Audit before applying

When porting, migrating, or applying external code/patterns, audit each change for relevance and correctness in our context. Do not blindly copy — evaluate whether each piece is appropriate and worth keeping.

## Context & Cost Efficiency

### Compact before research phases

When a session shifts from implementation to open-ended research or exploration (e.g., "do deep research on…", "check documentation for…"), proactively compact the session first. Research generates high-volume tool output that inflates context rapidly.

### Split sessions at phase boundaries

Do not keep a single session alive across multiple discrete deliverables. When a phase completes (PR opened, spec finalized, implementation merged), suggest starting a fresh session for the next phase. A compact summary in the new session's first message is far cheaper than carrying forward accumulated checkpoint history.

### Avoid redundant skill-context re-injection

If a skill's context has already been injected in the current session, do not re-inject the full payload on subsequent invocations. Reference the earlier context instead. If compaction occurred since the last injection, a brief summary is sufficient — not the full skill document again.

### Agent-merge check-ins belong in their own session

PR lifecycle check-ins (merge condition checks, CI status polling) should run in a dedicated short-lived session, not in the development session. They only need PR metadata and CI status — not the accumulated dev context.

### Compact by turn 12 in exploratory sessions

Any session that passes ~10-12 turns without compaction should be compacted. Context cost grows with every turn — later turns pay for the full accumulated history of all prior turns. Don't let sessions run 15+ turns uncompacted.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

