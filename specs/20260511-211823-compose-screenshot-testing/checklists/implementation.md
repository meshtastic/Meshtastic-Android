# Implementation Quality Checklist: Compose Preview Screenshot Testing

**Purpose**: Deep pre-push self-check for the author across KMP architecture, build integration, and preview coverage
**Created**: 2026-05-08
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)

## Requirement Completeness

- [x] CHK001 - Are Gradle plugin configuration requirements specified for both `gradle.properties` AND module-level `experimentalProperties`? [Completeness, Spec §FR-008, §FR-017]
- [x] CHK002 - Are version catalog entries defined with exact group/name/version coordinates for both the CST plugin and the validation API library? [Completeness, Spec §FR-007]
- [x] CHK003 - Are the `screenshotTestImplementation` dependency configurations explicitly listed (validation-api + ui-tooling)? [Completeness, Spec §FR-006]
- [x] CHK004 - Is the `settings.gradle.kts` inclusion specified with the conditional `desktopOnly` exclusion guard? [Completeness, Spec §FR-009]
- [x] CHK005 - Are all 8 eligible modules from SC-002 explicitly addressed in the tasks — either with existing preview wrappers or new preview creation tasks? [Completeness, Spec §SC-002]
- [x] CHK006 - Are docs pipeline integration requirements defined (copy task, manifest, target directory, .gitignore)? [Completeness, Spec §FR-013]
- [x] CHK007 - Are CI workflow requirements specified (validation step, artifact upload on failure, correct `if:` condition)? [Completeness, Spec §FR-014]

## Requirement Clarity

- [x] CHK008 - Is the dual-annotation pattern (`@PreviewTest` + `@Preview`/`@PreviewLightDark`) unambiguously stated for all wrapper tasks? [Clarity, Spec §FR-006]
- [x] CHK009 - Is the `imageDifferenceThreshold` value specified with its unit and rationale (0.0005f = 0.05% for cross-OS JDK font differences)? [Clarity, Plan §Technical Context]
- [x] CHK010 - Is the reference image storage path (`screenshotTestDebug/reference/`) consistently used across spec, plan, data-model, and quickstart? [Clarity]
- [x] CHK011 - Is "primary components" defined with concrete composable names for each module needing new previews? [Clarity, Spec §FR-012]
- [x] CHK012 - Is the `namespace` for the screenshot-tests module explicitly specified (`org.meshtastic.screenshot.tests`)? [Clarity, Plan §data-model]
- [x] CHK013 - Is the Gradle task naming convention unambiguous — `validateFdroidDebugScreenshotTest` consistently used rather than the generic `validateDebugScreenshotTest`? [Clarity, Spec §FR-014]

## Requirement Consistency

- [x] CHK014 - Are the module exclusion lists consistent between FR-012 (spec), the deferred modules table (data-model), and the Dependencies section (tasks)? [Consistency, Spec §FR-012]
- [x] CHK015 - Is the SC-002 eligible module list (8 modules) consistent with FR-012's inclusion scope and the tasks that create wrappers? [Consistency, Spec §SC-002]
- [x] CHK016 - Are the convention plugins applied to `screenshot-tests` consistent across plan (`meshtastic.detekt` + `meshtastic.spotless`), spec (FR-010), and task T003? [Consistency]
- [x] CHK017 - Does the plan's Project Structure tree match the actual file paths referenced in tasks T012, T013, T019-T027a, T039? [Consistency]
- [x] CHK018 - Are the Constitution Check gates (plan.md pre-design and post-design) consistent with each other and with the verification commands in T047? [Consistency]

## KMP Architecture Compliance

- [x] CHK019 - Is it documented that `screenshot-tests/` is intentionally Android-only and does NOT use `meshtastic.kmp.*` convention plugins? [Completeness, Plan §Complexity Tracking]
- [x] CHK020 - Are requirements clear that NO `java.*` or `android.*` imports are added to any `commonMain` source set as part of this feature? [Completeness, Plan §Constitution Check I]
- [x] CHK021 - Is the visibility convention (`internal`, not `private`) specified for preview composables that need cross-module access? [Clarity, Spec §FR-004]
- [x] CHK022 - Are the KMP compile verification commands specified for audited modules (`compileKotlinJvm` for KMP modules, not `compileFdroidDebugKotlin`)? [Clarity, Tasks §T010]
- [x] CHK023 - Is it clear that preview composables live in `commonMain` (not `androidMain`) so they compile on all KMP targets? [Clarity, Spec §FR-004, §FR-005]
- [x] CHK024 - Are the modules with `androidMain`-only previews identified for migration to `commonMain` (`feature:settings`, `feature:intro`)? [Coverage, Spec §FR-012]
- [x] CHK025 - Is the justification for the Android-only module documented in Complexity Tracking with the rejected alternative? [Completeness, Plan §Complexity Tracking]

## Build Integration Correctness

- [x] CHK026 - Are both experimental flags documented (gradle.properties global + module-level `experimentalProperties`)? [Completeness, Spec §FR-008, §FR-017]
- [x] CHK027 - Is the plugin application order specified (`com.android.library` before `com.android.compose.screenshot`)? [Clarity, Gap]
- [x] CHK028 - Are `compileSdk` and `minSdk` sourcing requirements specified (from `config.properties` via convention, matching the rest of the project)? [Clarity, Gap]
- [x] CHK029 - Is the compose compiler plugin application requirement documented for the screenshot-tests module? [Completeness, Tasks §T003]
- [x] CHK030 - Are the `screenshotTestImplementation` vs `implementation` dependency scopes correctly distinguished in requirements? [Clarity, Spec §FR-003, §FR-006]
- [x] CHK031 - Is the minimal `AndroidManifest.xml` requirement documented (empty manifest, namespace from build.gradle.kts)? [Completeness, Tasks §T004]
- [x] CHK032 - Is the CI workflow step ordering specified (build before validate, validate before artifact upload, `if: failure()` on upload)? [Completeness, Tasks §T035-T038]

## Preview Convention Coverage

- [x] CHK033 - Are `AppTheme` wrapping requirements defined for ALL preview composables, not just new ones? [Coverage, Spec §FR-005]
- [x] CHK034 - Are the specific `core:ui` component files needing `AppTheme` fixes enumerated? [Completeness, Tasks §T008]
- [x] CHK035 - Are the specific `feature:node` files with `private` visibility listed with function count? [Completeness, Tasks §T009]
- [x] CHK036 - Is the synthetic sample data convention explicit — hardcoded strings or `core/resources` string resources, never production data? [Clarity, Spec §FR-016]
- [x] CHK037 - Are `@PreviewParameter` provider-based previews addressed (e.g., `SignalInfo`, `MaterialBatteryInfo`, `MaterialBluetoothSignalInfo` use `PreviewParameterProvider`)? [Coverage, Gap]
- [x] CHK038 - Are `CompositionLocal` edge cases addressed — do requirements specify that previews must supply defaults or use `LocalInspectionMode` guards? [Edge Case, Spec §Edge Cases]
- [x] CHK039 - Are Compose resource dependency requirements defined — `screenshot-tests` must depend on modules owning resources used by previews? [Edge Case, Spec §Edge Cases]

## Scenario Coverage

- [x] CHK040 - Are requirements defined for what happens when a contributor renames a `@PreviewTest` function (reference images invalidated)? [Coverage, Edge Case]
- [x] CHK041 - Are requirements defined for orphaned reference images (preview deleted but PNG remains)? [Coverage, Edge Case]
- [x] CHK042 - Are cross-OS rendering difference requirements specified (threshold value, CI-generated references as source of truth)? [Coverage, Spec §Assumptions]
- [x] CHK043 - Is the recovery flow defined when CST plugin version becomes incompatible with AGP? [Coverage, Spec §Edge Cases]
- [x] CHK044 - Are requirements defined for the case where a new `@PreviewTest` wrapper is added but `updateScreenshotTest` is not run? [Coverage, Spec §Edge Cases]
- [x] CHK045 - Is the scenario addressed where a preview depends on a `CompositionLocal` not provided in the screenshot test context? [Coverage, Spec §Edge Cases]

## Acceptance Criteria Quality

- [x] CHK046 - Is the 60-second performance budget (SC-001) measurable and is there a task to validate it? [Measurability, Spec §SC-001]
- [x] CHK047 - Is the 80% module coverage threshold (SC-002) calculable with the explicit denominator of 8 eligible modules? [Measurability, Spec §SC-002]
- [x] CHK048 - Is the "under 10 minutes" quickstart criterion (SC-005) testable — does a task exist to walkthrough the quickstart? [Measurability, Spec §SC-005, Tasks §T049]
- [x] CHK049 - Is SC-006 (spotlessCheck + detekt pass) verified by an explicit task with the correct Gradle commands? [Measurability, Spec §SC-006, Tasks §T047-T048]

## Non-Functional Requirements

- [x] CHK050 - Are privacy requirements specified for reference image content (synthetic data only, no PII in rendered screenshots)? [Privacy, Spec §FR-005, §FR-016]
- [x] CHK051 - Are lint/formatting requirements specified for the screenshot test source set (not just the module — does `screenshotTest` code get formatted by spotless)? [Consistency, Spec §FR-010]
- [x] CHK052 - Is the version control strategy for reference images documented (commit PNGs, reviewable in PR diffs)? [Completeness, Spec §FR-015]
- [x] CHK053 - Are the local verification commands documented and do they include the screenshot validation task? [Completeness, Plan §Constitution Check VI]

## Dependencies & Assumptions

- [x] CHK054 - Is the AGP 9.2.1 + CST alpha14 compatibility assumption documented and validated? [Assumption, Spec §Assumptions]
- [x] CHK055 - Is the CMP 1.11+ `@Preview` package alignment assumption documented (same `androidx.compose.ui.tooling.preview` as Jetpack)? [Assumption, Research §2]
- [x] CHK056 - Is the dependency on spec 003 (docs pipeline) scoped — does this spec define only the generation mechanism, leaving consumption to spec 003? [Dependency, Spec §Assumptions]
- [x] CHK057 - Is the assumption that previews require no ViewModel/DI/network access documented? [Assumption, Spec §Assumptions]

## Ambiguities & Gaps

- [x] CHK058 - Is the docs-screenshots manifest format defined precisely (glob syntax, one pattern per line, comment support)? [Ambiguity, Tasks §T032]
- [x] CHK059 - Is the `copyDocsScreenshots` task's file renaming strategy specified (how CST hash-based names map to stable doc-friendly names)? [Ambiguity, Tasks §T031]
- [x] CHK060 - Is it specified whether the `screenshot-tests` module needs product flavors (fdroid/google) or just debug/release build types? [Gap]
- [x] CHK061 - Is it clear whether T037 (reusable workflow check) supersedes T035-T036 or supplements them? [Ambiguity, Tasks §T037]
- [x] CHK062 - Are the `@PreviewTest` wrapper naming conventions defined (e.g., `{PreviewName}ScreenshotTest`)? [Gap, Data Model §Screenshot Test Wrapper]

## Notes

- This checklist covers all three requested domains (KMP architecture, build integration, preview coverage) at deep rigor
- Audience: author pre-push self-check — items are ordered for sequential validation during implementation
- 62 items total across 10 quality dimensions
- Items reference specific spec sections, plan sections, and task IDs for traceability
- CHK027, CHK028, CHK037, CHK060, CHK062 are marked `[Gap]` — these represent areas where requirements may need to be added
