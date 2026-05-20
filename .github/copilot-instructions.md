# Meshtastic Android — Copilot Instructions

> **Full rules**: `AGENTS.md` is the source of truth. This file is a compact quick-reference for build commands and task naming. For architecture, conventions, and workflow details, consult `AGENTS.md` and the `.skills/` playbooks listed at the bottom.

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

- **Architecture**: KMP project (Android, Desktop, iOS). Business logic in `commonMain`; platform shells (`androidApp/`, `desktopApp/`) wire DI and host UI. See `AGENTS.md` and `.skills/kmp-architecture/`.
- **Flavors**: `fdroid` (OSS) / `google` (Maps + DataDog). Only one installable at a time (different signing keys).
- **Verify before push**: Run `./gradlew spotlessApply detekt assembleDebug test allTests`, then confirm CI with `gh pr checks <PR>`.
- **Strings**: `stringResource(Res.string.key)` — run `python3 scripts/sort-strings.py` after adding strings.
- **Icons**: `MeshtasticIcons` (from `core/ui/icon/`), not `material.icons.Icons`.
- **Error handling**: `safeCatching {}` (not `runCatching {}`) in coroutine code.
- **Dispatchers**: `org.meshtastic.core.common.util.ioDispatcher`, not `Dispatchers.IO`.
- **Navigation**: `MeshtasticNavDisplay` + `NavigationBackHandler` (not Android `BackHandler`).
- **Protos**: `core/proto/` is a read-only git submodule. Never modify proto files.
- **Branches**: Must start with `feat/`, `fix/`, `chore/`, `docs/`, `build/`, `ci/`, `refactor/`, `test/`, `deps/`, or a numeric spec prefix. Always branch off `origin/main`.

<!-- SPECKIT START -->
## Active Plan

- **Feature**: Reorder Bottom Navigation Tab Labels
- **Plan**: `specs/20260520-153412-nav-tab-labels/plan.md`
- **Branch**: `jamesarich/issue-5543-alignment-reorder-bottom-navigation-tab-91d55d`
<!-- SPECKIT END -->

## Deeper Guidance

Consult `.skills/` for detailed playbooks:
- `.skills/project-overview/` — Full codebase map and bootstrap
- `.skills/kmp-architecture/` — Source-set rules, expect/actual
- `.skills/compose-ui/` — Adaptive UI, string resources
- `.skills/navigation-and-di/` — Nav 3 & Koin patterns
- `.skills/testing-ci/` — CI architecture, verification matrix
- `.skills/implement-feature/` — Feature development workflow
- `.skills/code-review/` — PR hygiene checklist
- `.skills/speckit/` — Spec Kit SDD workflow, slash commands, constitution
