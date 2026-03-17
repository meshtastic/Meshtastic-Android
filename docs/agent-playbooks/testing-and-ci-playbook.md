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
- CI runs host verification and Android build/device verification in separate jobs inside `.github/workflows/reusable-check.yml`.

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
- Android lint for all directly runnable Android modules:
  `app:lintFdroidDebug app:lintGoogleDebug core:barcode:lintFdroidDebug core:barcode:lintGoogleDebug core:api:lintDebug mesh_service_example:lintDebug`
- Host tests plus coverage aggregation:
  `test koverXmlReport app:koverXmlReportFdroidDebug app:koverXmlReportGoogleDebug core:api:koverXmlReportDebug core:barcode:koverXmlReportFdroidDebug core:barcode:koverXmlReportGoogleDebug mesh_service_example:koverXmlReportDebug`
- JVM smoke compile for all KMP JVM targets (all compile-only modules remain explicit):
  `:core:proto:compileKotlinJvm :core:common:compileKotlinJvm :core:model:compileKotlinJvm :core:repository:compileKotlinJvm :core:di:compileKotlinJvm :core:navigation:compileKotlinJvm :core:resources:compileKotlinJvm :core:datastore:compileKotlinJvm :core:database:compileKotlinJvm :core:domain:compileKotlinJvm :core:prefs:compileKotlinJvm :core:network:compileKotlinJvm :core:data:compileKotlinJvm :core:ble:compileKotlinJvm :core:nfc:compileKotlinJvm :core:service:compileKotlinJvm :core:testing:compileKotlinJvm :core:ui:compileKotlinJvm :feature:intro:compileKotlinJvm :feature:messaging:compileKotlinJvm :feature:connections:compileKotlinJvm :feature:map:compileKotlinJvm :feature:node:compileKotlinJvm :feature:settings:compileKotlinJvm :feature:firmware:compileKotlinJvm`
- Android build tasks:
  `app:assembleFdroidDebug app:assembleGoogleDebug mesh_service_example:assembleDebug`
- Instrumented tests (when emulator tests are enabled):
  `app:connectedFdroidDebugAndroidTest app:connectedGoogleDebugAndroidTest core:barcode:connectedFdroidDebugAndroidTest core:barcode:connectedGoogleDebugAndroidTest`
- Coverage uploads happen once from the host job; instrumented test results upload once from the first Android matrix API to avoid duplicate reporting.

Reference: `.github/workflows/reusable-check.yml`

PR workflow note:

- `.github/workflows/pull-request.yml` ignores docs-only changes (`**/*.md`, `docs/**`), so doc-only PRs may skip Android CI by design.
- PR change detection includes workflow/build/config paths such as `.github/workflows/**`, `desktop/**`, `mesh_service_example/**`, `config/**`, `gradle/**`, `settings.gradle.kts`, and `test.gradle.kts`.
- Android CI on PRs runs with `run_instrumented_tests: false`; merge queue keeps the full emulator matrix on API 26 and 35.

## 5) Practical guidance for agents

- Start with the smallest set that validates your touched area.
- Keep documentation continuously in sync with architecture, CI, and workflow changes; do not defer doc fixes to a later PR.
- If modifying cross-module contracts (routes, repository interfaces, DI graph), run the broader baseline.
- If unable to run full validation locally, report exactly what ran and what remains.


