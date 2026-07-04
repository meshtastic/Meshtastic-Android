# Feature Specification: Compose Preview Screenshot Testing

**Feature Branch**: `018-compose-screenshot-testing`  
**Created**: 2026-05-08  
**Status**: Draft  
**Input**: User description: "Set up official Compose Preview Screenshot Testing, add comprehensive CMP previews across modules, and leverage generated screenshots in the automated docs feature."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Catch Visual Regressions Automatically (Priority: P1)

A contributor changes a shared UI component (for example, the message bubble composable in `feature/messaging`). When they run the local verification command or push a PR, the screenshot validation task detects that the rendered output differs from the approved reference image and fails, surfacing the exact visual diff before the change can merge.

**Why this priority**: Without regression detection the entire feature has no value. This is the foundational capability that all other stories build on.

**Independent Test**: Modify a composable's padding or color, run the validate task, and confirm the task fails with a diff report showing the change.

**Acceptance Scenarios**:

1. **Given** reference images exist for a previewed component, **When** a contributor changes that component's layout or styling, **Then** the validate task fails and produces an HTML report highlighting the pixel differences.
2. **Given** the contributor intentionally changed the UI, **When** they run the update task, **Then** new reference images replace the old ones and the validate task passes on the next run.
3. **Given** no UI changes were made, **When** the validate task runs, **Then** it passes without producing diff artifacts.

---

### User Story 2 — Add and Maintain Preview Composables in commonMain (Priority: P2)

A contributor wants to add a new preview for a component they built in a KMP feature module. They create or enhance a `*Previews.kt` file in `commonMain` with `@Preview` or `@PreviewLightDark`, see the preview in the IDE, and then add a thin `@PreviewTest` wrapper in the screenshot test module so the preview is included in automated screenshot validation.

**Why this priority**: Previews are the input to the screenshot testing pipeline. Without comprehensive coverage, regression detection is limited to a handful of components.

**Independent Test**: Create a new `@PreviewLightDark` composable in a feature module's `commonMain`, add a `@PreviewTest` wrapper in the screenshot test module, run the update task, and verify that reference images are generated for both light and dark variants.

**Acceptance Scenarios**:

1. **Given** a contributor creates a new `@PreviewLightDark` composable in `commonMain`, **When** they add a `@PreviewTest` wrapper in the screenshot test module and run the update task, **Then** reference images are generated for both light and dark theme variants.
2. **Given** an existing preview uses `private` visibility, **When** the contributor upgrades it to `internal`, **Then** the screenshot test module can import and wrap it without other changes.
3. **Given** a preview wraps its content in the project theme, **When** the reference image is generated, **Then** the image reflects the correct Material 3 theming and icon set.

---

### User Story 3 — Supply Screenshot Assets to the Docs Pipeline (Priority: P3)

The automated docs feature (spec 003) needs screenshot assets that stay in sync with the codebase. A build step or CI task copies selected reference images from the screenshot test output into the docs asset directory so they can be embedded in the published documentation site and in-app help browser.

**Why this priority**: Screenshots that drift from the actual UI erode user trust. Tying docs assets to the same validated reference images eliminates manual screenshot capture.

**Independent Test**: Generate reference images, run the docs asset copy task, and verify the target directory contains the expected PNGs matching the current UI state.

**Acceptance Scenarios**:

1. **Given** reference images exist in the screenshot test module, **When** the docs asset sync task runs, **Then** selected images are copied to the docs asset directory with stable, predictable file names.
2. **Given** a UI change updates a reference image, **When** the docs pipeline rebuilds, **Then** the published docs reflect the updated screenshot without manual intervention.
3. **Given** a reference image is deleted or renamed, **When** the docs build runs, **Then** the build warns about missing expected assets rather than silently producing broken image links.

---

### User Story 4 — Validate Screenshots in CI (Priority: P4)

When a pull request is opened against `main`, the CI workflow runs the screenshot validation task as part of the check suite. If any reference images are missing or differ from the rendered output, the check fails and the contributor is directed to review the HTML diff report or update references.

**Why this priority**: CI enforcement ensures that visual regressions are caught even when contributors skip local validation.

**Independent Test**: Open a PR that changes a previewed component without updating reference images. Confirm the CI check fails and the diff report artifact is uploaded.

**Acceptance Scenarios**:

1. **Given** a PR changes a previewed composable without updating references, **When** CI runs, **Then** the screenshot validation check fails.
2. **Given** a PR includes updated reference images that match the new UI, **When** CI runs, **Then** the screenshot validation check passes.
3. **Given** the screenshot validation task fails, **When** the CI workflow completes, **Then** the HTML diff report is uploaded as a build artifact for review.

---

### User Story 5 — Audit and Upgrade Existing Previews (Priority: P5)

The project already has preview composables in several modules (`core/ui`, `feature/messaging`, `feature/node`). These need to be audited for visibility (must be `internal`, not `private`), theme wrapping (must use `AppTheme`), and sample data quality (must be synthetic). Modules without any previews need initial coverage for their primary components.

**Why this priority**: Existing previews must meet the conventions before screenshot tests can reference them. New previews for uncovered modules expand regression coverage.

**Independent Test**: Run the screenshot test suite after the audit and confirm that all existing previews generate valid reference images and that newly added previews for previously uncovered modules also produce references.

**Acceptance Scenarios**:

1. **Given** an existing preview uses `private` visibility, **When** the audit upgrades it to `internal`, **Then** the screenshot test module can import it without other code changes.
2. **Given** a feature module has no previews, **When** previews are added for its primary components, **Then** reference images are generated and the module's UI is covered by regression detection.
3. **Given** all audited and new previews are wrapped in `AppTheme`, **When** reference images are generated, **Then** they consistently reflect the project's Material 3 theme in both light and dark variants.

---

### Edge Cases

- What happens when a preview composable references a CompositionLocal that is not provided in the screenshot test context? The preview must supply its own defaults or use `LocalInspectionMode` guards.
- What happens when a preview depends on Compose resources that are not available in the `screenshotTest` source set? The screenshot test module must depend on the module that owns the resources.
- What happens when the CST plugin version is incompatible with the current AGP version? The build fails at configuration time with a clear error; the version catalog entry makes the version easy to update.
- What happens when a contributor adds a `@PreviewTest` wrapper but forgets to generate reference images? The validate task fails because no reference exists for the new test, prompting the contributor to run the update task.
- What happens when reference images differ across operating systems (macOS vs Ubuntu CI)? Both recording and validation should run on the same OS (CI uses `ubuntu-24.04`); contributors regenerate references via CI or accept minor rendering differences with a documented threshold.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST integrate the official Compose Preview Screenshot Testing plugin (`com.android.compose.screenshot`) at version 0.0.1-alpha14 or later.
- **FR-002**: The `screenshotTest` source set MUST live in a dedicated Android-only module (`screenshot-tests/`) rather than in the `app` module or in KMP modules.
- **FR-003**: The `screenshot-tests` module MUST depend on KMP feature and core modules so it can import their `commonMain` preview composables.
- **FR-004**: Preview composables referenced by screenshot tests MUST have `internal` or `public` visibility (not `private`).
- **FR-005**: Preview composables MUST wrap their content in the project theme (`AppTheme`) and use synthetic sample data only — never real user data, PII, or location coordinates.
- **FR-006**: Screenshot test wrappers MUST be annotated with `@PreviewTest` and the corresponding `@Preview` or `@PreviewLightDark` annotation so the CST plugin knows what configurations to render.
- **FR-007**: The version catalog (`gradle/libs.versions.toml`) MUST include entries for the CST plugin and the `screenshot-validation-api` library.
- **FR-008**: The `android.experimental.enableScreenshotTest=true` property MUST be set in `gradle.properties`.
- **FR-009**: The `screenshot-tests` module MUST be included in `settings.gradle.kts`.
- **FR-010**: The `screenshot-tests` module MUST apply `meshtastic.detekt` and `meshtastic.spotless` convention plugins so screenshot test code passes the same static analysis gates as all other code.
- **FR-011**: Existing preview composables in `core/ui`, `feature/messaging`, and `feature/node` MUST be audited and upgraded to meet the visibility and theming conventions.
- **FR-012**: New preview composables MUST be added for primary components in `feature/connections`, `feature/settings`, `feature/firmware`, `feature/intro` (commonMain portions), and `core/ui` (shared components, icons, indicators). Modules without standard Compose UI composables (`feature/metrics`, `feature/auto`, `feature/discovery`, `feature/docs`) and modules relying on platform-specific renderers (`feature/map`, `feature/widget`) are excluded.
- **FR-013**: A Gradle task or script MUST exist to copy selected reference images from the screenshot test output into a docs asset directory for consumption by the docs feature (spec 003).
- **FR-014**: The CI workflow MUST include a screenshot validation step that runs `validateFdroidDebugScreenshotTest` and uploads the HTML diff report as a build artifact on failure.
- **FR-015**: Reference images MUST be stored in version control so they are reviewable in pull request diffs.
- **FR-016**: All user-visible strings used in preview sample data MUST use string resources from `core/resources` or hardcoded synthetic placeholders — never production user data.
- **FR-017**: The screenshot test module's `build.gradle.kts` MUST enable the experimental screenshot test flag in the `android {}` block.

### Key Entities

- **Preview Composable**: A `@Composable` function annotated with `@Preview` or `@PreviewLightDark` in a module's `commonMain` source set. Serves as both an IDE preview and screenshot test input.
- **Screenshot Test Wrapper**: A `@Composable` function annotated with `@PreviewTest` in the `screenshotTest` source set that calls a preview composable.
- **Reference Image**: A PNG file generated by the CST update task, stored in `screenshotTestDebug/reference/`, used as the ground truth for validation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The screenshot validation task passes when no UI components have changed and fails within 60 seconds when a previewed component's appearance changes.
- **SC-002**: At least 80% of eligible modules have at least one preview composable covered by a screenshot test. Eligible modules are those containing standard Compose UI composables in `commonMain`: `core:ui`, `feature:messaging`, `feature:node`, `feature:wifi-provision`, `feature:connections`, `feature:settings`, `feature:firmware`, `feature:intro` (8 modules; threshold = 7).
- **SC-003**: The CI pipeline includes screenshot validation as a required check, and the HTML diff report is available as an artifact when validation fails.
- **SC-004**: Reference images for docs-relevant components are consumable by the docs build pipeline (spec 003) without manual screenshot capture.
- **SC-005**: A new contributor can add a preview and its screenshot test wrapper by following the quickstart guide in under 10 minutes.
- **SC-006**: All screenshot test code passes `spotlessCheck` and `detekt` without new violations.

## Assumptions

- The official Compose Preview Screenshot Testing plugin (0.0.1-alpha14) is compatible with AGP 9.2.1 and Kotlin 2.3.21, which the project already uses.
- The CST plugin does not support KMP module targets directly; the workaround of a dedicated Android-only module is the recommended approach for KMP projects.
- CMP 1.11+ `@Preview` in `commonMain` is the standard annotation supported by the CST plugin when composables are consumed from an Android module.
- Reference images may differ slightly between macOS (local dev) and Ubuntu (CI). The project will standardize on CI-generated references or accept a documented comparison threshold.
- The docs feature (spec 003) will define the exact directory and naming convention for screenshot assets it consumes; this spec provides the generation and copy mechanism.
- Preview composables do not require ViewModel, DI, or network access — they render stateless UI with synthetic data only.
