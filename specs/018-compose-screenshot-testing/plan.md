# Implementation Plan: Compose Preview Screenshot Testing

**Branch**: `018-compose-screenshot-testing` | **Date**: 2026-05-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/018-compose-screenshot-testing/spec.md`

## Summary

Integrate the official Compose Preview Screenshot Testing (CST) plugin into the Meshtastic Android KMP project. A dedicated Android-only `screenshot-tests/` module imports `commonMain` preview composables from KMP feature and core modules, runs host-side JVM screenshot validation (no emulator), and feeds reference images into the docs pipeline and CI checks. Existing previews are audited for visibility/theming, and new previews are added for uncovered modules to achieve 80%+ component coverage.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JDK 21
**Primary Dependencies**: AGP 9.2.1, CMP 1.11.0-rc01, CST plugin 0.0.1-alpha14, screenshot-validation-api 0.0.1-alpha14
**Storage**: N/A — reference images stored as PNGs in version control
**Testing**: CST `validateDebugScreenshotTest` (host-side JVM, no emulator)
**Target Platform**: Android (screenshot tests); previews authored in CMP `commonMain` (Android + Desktop)
**Project Type**: Mobile app (KMP) with Android-only screenshot testing module
**Performance Goals**: Validation task completes in <60 seconds for the full preview suite
**Constraints**: CST plugin is Android-only; cannot apply to KMP modules directly
**Scale/Scope**: ~50+ preview composables across ~10 modules, generating ~100+ reference images

## Constitution Check (Pre-Design)

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ PASS — Preview composables are authored in `commonMain` using CMP `@Preview`/`@PreviewLightDark`. The `screenshot-tests/` module is intentionally Android-only (it is a test harness, not business logic). No `java.*` or `android.*` imports are added to any `commonMain` source set. Platform-specific work is isolated to the screenshot test module and CI workflow.

- **II. Zero Lint Tolerance**: ✅ PASS — The `screenshot-tests` module will apply `meshtastic.detekt` and `meshtastic.spotless` convention plugins. Verification commands:
  - `./gradlew :screenshot-tests:detekt`
  - `./gradlew spotlessApply spotlessCheck`
  - All existing module lint commands remain unchanged.

- **III. Compose Multiplatform UI**: ✅ PASS — All preview composables use CMP `@Preview`/`@PreviewLightDark` from `androidx.compose.ui.tooling.preview` (CMP 1.11+ uses identical package to Jetpack). Previews wrap content in `AppTheme` (CMP, not Android-only). No navigation or float formatting in scope (previews are stateless component snapshots).

- **IV. Privacy First**: ✅ PASS — Preview sample data uses hardcoded synthetic values only. No PII, location data, or cryptographic keys. No modifications to `core/proto` submodule. Reference images contain only synthetic UI renderings.

- **V. Design Standards Compliance**: ✅ N/A — This feature does not introduce new user-facing screens. Preview composables render existing components. The previews themselves serve as a visual audit tool to verify design standards compliance.

- **VI. Verify Before Push**: ✅ PASS — Local verification commands:
  ```bash
  ./gradlew spotlessApply detekt assembleDebug test allTests :screenshot-tests:validateFdroidDebugScreenshotTest
  ```
  Post-push CI check:
  ```bash
  gh pr checks <PR_NUMBER>
  ```

## Project Structure

### Documentation (this feature)

```text
specs/018-compose-screenshot-testing/
├── plan.md              # This file
├── research.md          # Phase 0 output — technology decisions
├── data-model.md        # Phase 1 output — entity catalog
├── quickstart.md        # Phase 1 output — contributor guide
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
# New module
screenshot-tests/
├── build.gradle.kts                              # Android library + CST plugin
├── src/
│   ├── main/
│   │   └── AndroidManifest.xml                    # Minimal manifest
│   ├── screenshotTest/
│   │   └── kotlin/org/meshtastic/screenshots/
│   │       ├── core/                              # @PreviewTest wrappers for core/ui previews
│   │       │   ├── AlertScreenshotTests.kt
│   │       │   ├── ComponentScreenshotTests.kt
│   │       │   └── ...
│   │       └── feature/                           # @PreviewTest wrappers for feature previews
│   │           ├── MessagingScreenshotTests.kt
│   │           ├── NodeScreenshotTests.kt
│   │           ├── ConnectionsScreenshotTests.kt
│   │           ├── SettingsScreenshotTests.kt
│   │           ├── FirmwareScreenshotTests.kt
│   │           └── WifiProvisionScreenshotTests.kt
│   └── screenshotTestDebug/
│       └── reference/                             # Git-tracked reference PNGs
│           └── *.png

# Modified files (existing)
gradle/libs.versions.toml                          # + CST plugin & screenshot-validation-api
gradle.properties                                  # + android.experimental.enableScreenshotTest=true
settings.gradle.kts                                # + include(":screenshot-tests")
.github/workflows/pull-request.yml                 # + screenshot validation step

# Audited preview files (existing, visibility/theme fixes)
feature/node/src/commonMain/.../NodeDetailPreviews.kt
core/ui/src/commonMain/.../component/*.kt          # Theme wrapping consistency
feature/settings/src/commonMain/...                 # New previews (commonMain)
feature/connections/src/commonMain/...              # New previews
feature/firmware/src/commonMain/...                 # New previews
feature/intro/src/commonMain/...                    # New previews (migrate from androidMain)
```

**Structure Decision**: The `screenshot-tests/` module sits at the repository root (same level as `app/`, `desktop/`, `core/`, `feature/`) because it is a cross-cutting test harness that spans multiple core and feature modules. It is NOT a KMP module — it is intentionally Android-only, using `com.android.library` + CST plugin.

## Constitution Check (Post-Design)

*Re-evaluation after Phase 1 design.*

- **I. KMP Core**: ✅ Confirmed — no `commonMain` contamination. The `screenshot-tests/` module is Android-only test infrastructure. All preview composables remain in `commonMain`.
- **II. Zero Lint**: ✅ Confirmed — `meshtastic.detekt` and `meshtastic.spotless` applied to `screenshot-tests/`.
- **III. CMP UI**: ✅ Confirmed — all previews use CMP annotations. `AppTheme` is from `core/ui` (CMP).
- **IV. Privacy**: ✅ Confirmed — synthetic data only. No PII in reference images.
- **V. Design Standards**: ✅ N/A — no new user-facing screens.
- **VI. Verify Before Push**: ✅ Confirmed — commands listed above.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Android-only module in KMP project | CST plugin is Android-only; cannot apply to KMP module targets | Applying CST to `app` module couples tests to the host shell; applying to KMP modules is impossible |
