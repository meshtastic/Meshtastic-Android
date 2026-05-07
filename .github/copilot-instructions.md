# Meshtastic Android — Copilot Instructions

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
./gradlew :app:testFdroidDebugUnitTest

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

## Architecture

**Kotlin Multiplatform** project targeting Android, Desktop (JVM), and iOS. Business logic lives in `commonMain`; platform shells (`app/`, `desktop/`) wire DI and host UI.

### Module layers

| Layer | Modules | Role |
|-------|---------|------|
| Host | `app`, `desktop` | Platform shell, Koin root, theme |
| Feature | `feature/*` | Self-contained screens (KMP, `meshtastic.kmp.feature` plugin) |
| Core | `core/*` | Shared logic, data, networking, UI components |

### Key technologies

- **UI:** Compose Multiplatform + Material 3 Adaptive
- **Navigation:** JetBrains Navigation 3 (`@Serializable sealed interface` routes in `core:navigation`)
- **DI:** Koin 4.2+ with K2 Compiler Plugin (`@Module`, `@KoinViewModel`, `startKoin<AndroidKoinApp>`)
- **Networking:** Ktor (no OkHttp)
- **BLE:** Kable (via `core:ble`)
- **Database:** Room KMP
- **I/O:** Okio
- **Build:** Gradle Kotlin DSL with convention plugins in `build-logic/`
- **Flavors:** `fdroid` (OSS) / `google` (Maps + DataDog)

### Navigation pattern

Feature navigation graphs are extension functions on `EntryProviderScope<NavKey>` in `commonMain`. The host shell renders via `MeshtasticNavDisplay`. Use `NavigationBackHandler` (not Android's `BackHandler`).

## Key Conventions

### Source-set boundaries

- **`commonMain`** — All business logic, ViewModels, UI. No `java.*` or `android.*` imports.
- **`androidMain`** — Android framework integration only. No business logic.
- **`jvmMain` / `jvmAndroidMain`** — Shared JVM code (Android + Desktop).
- Platform capabilities: prefer interface + DI over `expect`/`actual`.

### Strings & formatting

- All strings in `core/resources/src/commonMain/composeResources/values/strings.xml`
- Use `stringResource(Res.string.key)` — never hardcoded strings.
- CMP only supports `%N$s` (string) and `%N$d` (int) — pre-format floats with `NumberFormatter.format()`.
- Run `python3 scripts/sort-strings.py` after adding strings.

### Error handling

- Use `safeCatching {}` (from `core:common`) instead of `runCatching {}` in suspend/coroutine code — `runCatching` swallows `CancellationException`.

### Dispatchers

- Use `org.meshtastic.core.common.util.ioDispatcher` — never `Dispatchers.IO` directly.
- Inject `CoroutineDispatchers` from `core:di`.

### Build-logic

- Convention plugins: `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.kmp.jvm.android`, `meshtastic.koin`
- Use `libs.library("alias-name")` string-based lookups (not type-safe accessors) in convention plugins.
- Prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs).

### Icons

- Use `MeshtasticIcons` (from `core/ui/icon/`) instead of `material.icons.Icons`.

### Protos

- `core/proto/` is a **read-only git submodule** from `meshtastic/protobufs`. Never modify proto files.

### Design standards

- All UI must conform to the [Meshtastic Client Design Standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md).
- Review new screens/significant UI changes against the standards before merge.

### Branch naming

Branches must start with: `bugfix/`, `enhancement/`, `dependencies/`, or `repo/`.

### Branching workflow

- `origin` = `meshtastic/Meshtastic-Android` (upstream, source of truth). Personal forks are typically behind.
- Always create branches off fetched upstream: `git fetch origin && git checkout -b <name> origin/main`
- Never branch from a personal fork's `main` — it may be stale.

### Push workflow (verify-then-push)

**Before push:**
```bash
./gradlew spotlessApply detekt assembleDebug test allTests  # or targeted module tasks
```
Only push when the above passes locally.

**After push:**
```bash
gh pr checks <PR_NUMBER>        # or: gh run list --branch <branch> --limit 3
```
Report CI status only after fetching actual results. Never say "CI should be green now" — check and confirm.

### Scope discipline

When a working branch grows beyond ~5 logical commits or starts spanning unrelated concerns, proactively propose:
1. A fresh branch off `origin/main`
2. Cherry-pick only the high-impact, low-blast-radius changes
3. Defer tangential work to follow-up PRs

Don't pile unrelated changes onto an existing branch. Squash fixup commits before pushing.

### Multi-flavor device installs

Two app flavors exist: `com.geeksville.mesh` (fdroid) and `com.geeksville.mesh.google` (google). Only one can be installed at a time (different signing keys). When switching flavors on a device:
- Uninstall the other flavor first, or the install will fail silently.
- Be aware that uninstalling loses onboarding state, permissions, and bonded-device data. Ask before uninstalling if the user has an active session.

## Deeper guidance

Consult `.skills/` for detailed playbooks:
- `.skills/project-overview/` — Full codebase map and bootstrap
- `.skills/kmp-architecture/` — Source-set rules, expect/actual
- `.skills/compose-ui/` — Adaptive UI, string resources
- `.skills/navigation-and-di/` — Nav 3 & Koin patterns
- `.skills/testing-ci/` — CI architecture, verification matrix
- `.skills/implement-feature/` — Feature development workflow
- `.skills/code-review/` — PR hygiene checklist
- `.skills/speckit/` — Spec Kit SDD workflow, slash commands, constitution
