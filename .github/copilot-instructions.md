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

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

