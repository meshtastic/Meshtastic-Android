# Research: Compose Preview Screenshot Testing

**Feature**: 018-compose-screenshot-testing
**Date**: 2026-05-08

## 1. CST Plugin Compatibility

**Decision**: Use `com.android.compose.screenshot` version `0.0.1-alpha14`.

**Rationale**: Alpha14 (released 2026-04-08) is the latest stable alpha. It supports AGP 9.0+ (project uses 9.2.1), Kotlin 2.2.10+ (project uses 2.3.21), and JDK 17-24 (project uses 21). Alpha12 introduced AGP 9.x support; alpha14 includes critical multi-module bug fixes.

**Alternatives considered**:
- Roborazzi — user explicitly rejected; wants official Google tooling
- Paparazzi — user explicitly rejected; same reason
- Older CST alphas — no reason to use older versions; alpha14 has the best AGP 9.x support

## 2. CMP @Preview Annotation Compatibility

**Decision**: Use existing `androidx.compose.ui.tooling.preview.Preview` annotations from CMP 1.11+ `commonMain` composables directly — no wrapper annotations needed.

**Rationale**: CMP 1.11 relocated `@Preview` to the `androidx.compose.ui.tooling.preview` package — identical to Jetpack Compose's package. The CST plugin scans for exactly this annotation. Existing `commonMain` previews are already compatible.

**Alternatives considered**:
- Duplicating previews with Jetpack-specific imports — unnecessary since CMP 1.11+ uses the same package
- Using `org.jetbrains.compose.ui.tooling.preview.Preview` — deprecated in CMP 1.11+

## 3. @PreviewTest Annotation

**Decision**: Use `@PreviewTest` from `com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha14` in `screenshotTestImplementation`.

**Rationale**: Since alpha10, all preview functions in the `screenshotTest` source set MUST be annotated with `@PreviewTest` — unannotated `@Preview` functions are skipped. The annotation is provided by the `screenshot-validation-api` artifact.

**Key detail**: `@PreviewTest` composables can call composables from other modules. The `screenshotTest` source set has visibility to all `implementation` dependencies.

## 4. Experimental Flags

**Decision**: Set both the `gradle.properties` flag AND the module-level `experimentalProperties` flag.

**Rationale**: Both flags are still required in alpha14:
- `gradle.properties`: `android.experimental.enableScreenshotTest=true`
- Module `build.gradle.kts`: `experimentalProperties["android.experimental.enableScreenshotTest"] = true`

## 5. Module Architecture

**Decision**: Create a dedicated Android-only module `screenshot-tests/` (not inside `core/` or `feature/`). Apply `com.android.library` + `com.android.compose.screenshot` + `kotlin-android`.

**Rationale**: The CST plugin is Android-only — it cannot be applied to KMP modules. The official docs state: "Both the IDE and the underlying plugin are engineered exclusively for Android projects." A dedicated Android-only module that depends on KMP modules and imports their `commonMain` composables is the clean workaround.

**Alternatives considered**:
- Adding CST to the `app` module — couples screenshot tests to the app module; muddies separation
- Adding CST to individual KMP modules — impossible; plugin doesn't support non-Android targets
- Using `core:api` or `core:barcode` as a pattern — those are `com.android.library` modules but serve different purposes

## 6. Reference Image Management

**Decision**: Store reference images in `screenshot-tests/src/screenshotTestDebug/reference/` (default CST path) and commit to Git.

**Rationale**: Reference images must be version-controlled for PR review. The default path follows the CST convention. Set `imageDifferenceThreshold = 0.0005f` (0.05%) to absorb minor JDK font rendering differences between macOS and Linux CI.

## 7. Preview Visibility Audit

**Decision**: Upgrade `private` preview composables to `internal` in modules that need screenshot test coverage.

**Rationale**: Most existing previews are `private`. The `screenshot-tests` module cannot import private functions from other modules. Changing to `internal` preserves encapsulation (visible only within the module's compilation unit) while enabling cross-module screenshot testing.

**Modules requiring audit**:
- `feature/node/detail/NodeDetailPreviews.kt` — 4 previews are explicitly `private`
- Many `core/ui/component/*.kt` previews lack explicit visibility (default `public`) but need theme-wrapping consistency review

## 8. CI Integration

**Decision**: Add a screenshot validation step to the `pull-request.yml` workflow. Run `validateFdroidDebugScreenshotTest`. Upload HTML diff report as artifact on failure.

**Rationale**: The CST plugin runs entirely on the host JVM (no emulator needed), making it fast for CI. The `fdroidDebug` variant aligns with the project's primary OSS flavor. The HTML diff report includes side-by-side reference/actual/diff images.

**Alternatives considered**:
- Running both `fdroid` and `google` variants — unnecessary cost; UI is identical across flavors for most components
- Running on a separate workflow — adds complexity without benefit; better as a step in existing PR checks

## 9. Docs Pipeline Integration

**Decision**: Create a `copyDocsScreenshots` Gradle task that copies selected reference images to a docs asset directory.

**Rationale**: Spec 003 (app-docs-markdown) requires screenshot assets from automation. Rather than a separate screenshot tool, reuse the CST-generated reference images. The task selects docs-relevant images by naming convention or manifest file.

## 10. Convention Plugin Usage

**Decision**: Do NOT use existing `meshtastic.kmp.*` convention plugins for the `screenshot-tests` module. Use `meshtastic.detekt` and `meshtastic.spotless` only. Apply `com.android.library` and CST plugin directly.

**Rationale**: The `screenshot-tests` module is intentionally Android-only. KMP convention plugins would add unnecessary multiplatform machinery. The existing `AndroidLibraryConventionPlugin` could be referenced but the screenshot module has unique requirements (CST plugin, experimental flags) that don't fit the existing pattern cleanly.

## 11. Feature Modules Without Preview Coverage

**Decision**: Prioritize adding previews to `feature/connections`, `feature/settings` (commonMain), and `feature/firmware`. Defer `feature/map` (heavy platform-specific rendering) and `feature/widget` (Glance, not standard Compose).

**Rationale**: `feature/connections`, `feature/settings`, and `feature/firmware` have standard Compose UI in `commonMain` that is straightforward to preview. `feature/map` relies on platform-specific map renderers that don't work in preview context. `feature/widget` uses Glance composables which are incompatible with CST.
