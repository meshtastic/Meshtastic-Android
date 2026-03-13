# Testing and CI Playbook

Use this matrix to choose the right verification depth for a change.

## 1) Baseline local verification order

Run in this order for routine changes:

```bash
./gradlew clean
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew detekt
./gradlew assembleDebug
./gradlew test
```

Notes:
- This order aligns with repository guidance in `AGENTS.md` and `.github/copilot-instructions.md`.
- CI additionally runs `testDebugUnitTest` in `.github/workflows/reusable-check.yml`.

## 2) Change-type matrix

- `docs-only` changes:
  - Usually no Gradle run required.
  - If you touched code examples or command docs, at least run `spotlessCheck` if practical.
  - If you changed architecture, CI, validation commands, or agent workflow guidance, update the mirrored docs in `AGENTS.md`, `.github/copilot-instructions.md`, `GEMINI.md`, and `docs/kmp-status.md` in the same slice.
- `UI text/resource` changes:
  - `spotlessCheck`, `detekt`, `assembleDebug`.
- `feature/commonMain logic` changes:
  - `spotlessCheck`, `detekt`, `test`, `assembleDebug`.
- `navigation/DI wiring` changes (app graph, Koin module/wrapper changes):
  - `spotlessCheck`, `detekt`, `assembleDebug`, `test`, plus `testDebugUnitTest` if available locally.
  - If touching any KMP module, also run the relevant `:compileKotlinJvm` task. CI validates all 22 KMP modules + `desktop:test`.
- `worker/service/background` changes:
  - `spotlessCheck`, `detekt`, `assembleDebug`, `test`, and targeted tests around WorkManager/service behavior.
- `BLE/networking/core repository` changes:
  - `spotlessCheck`, `detekt`, `assembleDebug`, `test`.

## 3) Flavor and instrumentation checks

Run these when relevant to map/provider/flavor-specific behavior:

```bash
./gradlew lintFdroidDebug lintGoogleDebug
./gradlew testFdroidDebug
./gradlew testGoogleDebug
./gradlew connectedAndroidTest
```

## 4) CI parity checks

Current reusable check workflow includes:

- `spotlessCheck detekt`
- `testDebugUnitTest testFdroidDebugUnitTest testGoogleDebugUnitTest`
- `koverXmlReport app:koverXmlReportFdroidDebug app:koverXmlReportGoogleDebug`
- JVM smoke compile (all 16 core + all 6 feature modules + `desktop:test`):
  `:core:proto:compileKotlinJvm :core:common:compileKotlinJvm :core:model:compileKotlinJvm :core:repository:compileKotlinJvm :core:di:compileKotlinJvm :core:navigation:compileKotlinJvm :core:resources:compileKotlinJvm :core:datastore:compileKotlinJvm :core:database:compileKotlinJvm :core:domain:compileKotlinJvm :core:prefs:compileKotlinJvm :core:network:compileKotlinJvm :core:data:compileKotlinJvm :core:ble:compileKotlinJvm :core:service:compileKotlinJvm :core:ui:compileKotlinJvm :feature:intro:compileKotlinJvm :feature:messaging:compileKotlinJvm :feature:map:compileKotlinJvm :feature:node:compileKotlinJvm :feature:settings:compileKotlinJvm :feature:firmware:compileKotlinJvm :desktop:test`
- `assembleDebug`
- `lintDebug`
- `connectedDebugAndroidTest` (when emulator tests are enabled)

Reference: `.github/workflows/reusable-check.yml`

PR workflow note:

- `.github/workflows/pull-request.yml` ignores docs-only changes (`**.md`, `docs/**`), so doc-only PRs may skip Android CI by design.
- Android CI on PRs runs with `run_instrumented_tests: false`; emulator tests are handled in other workflow contexts.

## 5) Practical guidance for agents

- Start with the smallest set that validates your touched area.
- Keep documentation continuously in sync with architecture, CI, and workflow changes; do not defer doc fixes to a later PR.
- If modifying cross-module contracts (routes, repository interfaces, DI graph), run the broader baseline.
- If unable to run full validation locally, report exactly what ran and what remains.


