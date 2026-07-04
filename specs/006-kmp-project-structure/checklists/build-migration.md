# Build Migration Checklist: KMP Recommended Project Structure Alignment

**Purpose**: Validate the quality, completeness, and clarity of requirements for migrating 27 KMP modules from legacy `android {}` blocks to `kotlin.androidLibrary {}` DSL, hardening convention plugins, and verifying module boundaries.
**Created**: 2025-07-16
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [tasks.md](../tasks.md)
**Focus**: Build infrastructure migration — DSL correctness, convention plugin hardening, build verification
**Audience**: PR Reviewer
**Depth**: Standard

---

## Requirement Completeness

- [ ] CHK001 — Are migration requirements specified for all 27 KMP modules individually, with per-module expected outcome documented? [Completeness, Spec §FR-001, Data Model §Migration Matrix]
- [ ] CHK002 — Are the 4 excluded modules (`feature:widget`, `core:api`, `core:barcode`, `screenshot-tests`) explicitly listed with justification for exclusion? [Completeness, Spec §FR-004]
- [ ] CHK003 — Is the convention plugin enhancement requirement (`androidResources.enable = false` default) specified with exact insertion point and backward-compatibility rationale? [Completeness, Spec §FR-003, §FR-007]
- [ ] CHK004 — Are build verification requirements defined for every migration phase, not just the final state? [Completeness, Plan §Phase Dependencies]
- [ ] CHK005 — Are requirements defined for what constitutes a "clean build baseline" measurement in Phase 1? [Completeness, Gap — T001 mentions "record timings" but format/storage unspecified]
- [ ] CHK006 — Is the redundant `jvm()` cleanup requirement documented with the full list of affected modules (14 modules per data-model.md)? [Completeness, Data Model §Redundant jvm() cleanup]
- [ ] CHK007 — Are requirements specified for updating `quickstart.md` if migration steps differ from what was actually executed? [Completeness, Tasks §T047]

## Requirement Clarity

- [x] CHK008 — Is the distinction between "28 KMP modules" (spec §Summary) and "27 KMP modules" (plan, data-model) resolved with an authoritative count? [Clarity, Ambiguity — ✅ Fixed: all references now say 27]
- [ ] CHK009 — Is "legacy `android {}` blocks inside `kotlin {}`" precisely defined to distinguish from `android {}` in non-KMP modules and from `androidLibrary {}` blocks? [Clarity, Spec §FR-001]
- [ ] CHK010 — Is the NFR-001 build-time threshold ("not increase by more than 5%") specified with measurement methodology — cold vs warm build, CI vs local, which tasks? [Clarity, Spec §NFR-001]
- [ ] CHK011 — Is "auto-derived namespace" behavior documented with the exact derivation formula (`:core:di` → `org.meshtastic.core.di`) so reviewers can verify correctness? [Clarity, Quickstart §Step 1]
- [ ] CHK012 — Is the `feature:wifi-provision` namespace mismatch clearly documented — why auto-derived `feature.wifi.provision` differs from required `feature.wifiprovision` and that this is the only override? [Clarity, Tasks §T030, Data Model §Tier 2c]
- [ ] CHK013 — Is "uses only `KotlinMultiplatformAndroidLibraryTarget` API" (SC-007) defined with enough specificity to distinguish compliant from non-compliant patterns in the convention plugin? [Clarity, Spec §SC-007]
- [ ] CHK014 — Is the `core:proto` minSdk override requirement (`minSdk = 21`) specified with rationale ("ATAK compatibility") that a reviewer can validate as still necessary? [Clarity, Tasks §T032]

## Requirement Consistency

- [ ] CHK015 — Are the tier classifications consistent between data-model.md, tasks.md, and quickstart.md for all 27 modules? [Consistency, Cross-Artifact]
- [ ] CHK016 — Does the `core:model` module appear in exactly one tier across all artifacts (Tier 2a in data-model.md, not duplicated in Tier 1)? [Consistency, Plan §Notes — explicit callout of double-count]
- [ ] CHK017 — Are the `withHostTest` configurations (empty `{}` vs `{ isIncludeAndroidResources = true }`) consistent between data-model.md and tasks.md for all 18 Tier 2 modules? [Consistency]
- [ ] CHK018 — Do quickstart.md code examples match the exact DSL patterns specified in data-model.md "After migration" column? [Consistency, Cross-Artifact]
- [ ] CHK019 — Are convention plugin file paths consistent across spec.md (§Key Components), plan.md (§Source Code), and tasks.md (T004, T043)? [Consistency]
- [ ] CHK020 — Is the `core:resources` module consistently documented as the sole module requiring `androidResources.enable = true` across all artifacts? [Consistency, Data Model §Validation Rules]

## Acceptance Criteria Quality

- [ ] CHK021 — Are success criteria SC-001 through SC-007 all objectively measurable with specific commands or grep patterns? [Measurability, Spec §Success Criteria]
- [ ] CHK022 — Is SC-006 ("new contributor can add a new KMP module") testable as-stated, or does it need a concrete verification procedure? [Measurability, Spec §SC-006 — no test procedure defined]
- [ ] CHK023 — Is SC-005 ("build time within 5%") measurable given that build times vary by machine — are multiple-run averaging or CI-based measurements specified? [Measurability, Spec §SC-005]
- [ ] CHK024 — Are the grep validation commands in tasks.md (T041, T042) specified with sufficient exclusion patterns to avoid false positives from Android-only modules and unrelated string matches? [Measurability, Tasks §T041]
- [ ] CHK025 — Does each user story's "Independent Test" section provide a concrete, copy-pasteable verification command? [Measurability, Spec §US1–US4]

## Scenario Coverage

- [ ] CHK026 — Are requirements defined for what happens if a single module migration fails mid-batch — can other modules in the same tier proceed? [Coverage, Exception Flow, Gap]
- [x] CHK027 — Are rollback requirements specified at per-module, per-tier, and full-migration granularity? [Coverage — ✅ Fixed: quickstart.md Rollback Plan now includes per-module, per-tier, and full-migration commands]
- [ ] CHK028 — Are requirements specified for the `DESKTOP_ONLY=true` build mode after each migration phase, or only at final verification? [Coverage, Spec §FR-005 — only tested in Phase 6 T039]
- [ ] CHK029 — Are CI/CD pipeline implications documented — will the migration PR pass existing CI checks without CI configuration changes? [Coverage, Gap]
- [ ] CHK030 — Are requirements specified for what happens if Gradle configuration cache is invalidated by the DSL migration? [Coverage, Spec §NFR-002]

## Edge Case Coverage

- [ ] CHK031 — Is the `core:database` module's `withDeviceTest` + `withHostTest` combination requirement fully specified, including the exact `instrumentationRunner` value? [Edge Case, Data Model §Tier 3]
- [ ] CHK032 — Is the `core:resources` module's resource prefix (`meshtastic_`) override requirement specified alongside the `androidResources.enable = true` override? [Edge Case, Data Model §Tier 3]
- [ ] CHK033 — Are requirements defined for modules that use the `meshtastic.kmp.jvm.android` plugin — does the DSL migration affect the `jvmAndroidMain` shared source set? [Edge Case, Spec §Assumptions]
- [ ] CHK034 — Is the behavior specified when a module's auto-derived namespace collides with another module's namespace? [Edge Case, Gap]
- [ ] CHK035 — Are requirements specified for the convention plugin's behavior when `namespace` is already set by a module (explicit override takes precedence)? [Edge Case, Quickstart §Step 1 — "if null" guard]

## Non-Functional Requirements

- [ ] CHK036 — Is NFR-003 ("no intermediate broken states on main branch") achievable as specified — does the plan ensure atomic commit boundaries? [NFR, Spec §NFR-003]
- [ ] CHK037 — Is NFR-004 ("documentable as step-by-step checklist for other repositories") satisfied by quickstart.md, or is additional documentation needed? [NFR, Spec §NFR-004]
- [ ] CHK038 — Are Gradle compatibility requirements specified — minimum Gradle version, configuration cache compatibility, isolated projects mode? [NFR, Spec §NFR-002]
- [ ] CHK039 — Are formatting and static analysis requirements (spotlessCheck, detekt) specified as part of every phase verification, or only in Phase 7? [NFR, Tasks — only T045 in Phase 7]

## Dependencies & Assumptions

- [ ] CHK040 — Is the assumption "project already runs AGP 9.2.1" validated with a specific version check requirement? [Assumption, Spec §Assumptions]
- [ ] CHK041 — Is the assumption "no module uses deprecated `androidTarget {}` call" specified as a pre-migration verification step? [Assumption, Spec §Assumptions]
- [ ] CHK042 — Is the Phase 2 → Phase 3/4/5 blocking dependency clearly specified — convention plugin hardening MUST complete before any module migration? [Dependency, Plan §Phase Dependencies]
- [ ] CHK043 — Is the `jvmAndroidMain` shared source set compatibility assumption validated — are there requirements to verify this pattern works with the new DSL? [Assumption, Spec §Assumptions]
- [ ] CHK044 — Is the backward-compatibility assumption for T004 (adding `androidResources.enable = false` default) documented with rationale that existing explicit overrides become no-ops? [Assumption, Plan §Key Risk Mitigation]

## Ambiguities & Conflicts

- [x] CHK045 — Is the "28 vs 27 modules" discrepancy resolved authoritatively in spec.md, or does the spec still say 28 while plan says 27? [Conflict — ✅ Fixed: all 5 instances of "28" updated to "27" in spec.md]
- [x] CHK046 — Does FR-008 ("prevent future modules from using legacy pattern") have a concrete enforcement mechanism specified, or is it aspirational? [Ambiguity — ✅ Fixed: T048 added to tasks.md — CI grep check or Gradle afterEvaluate assertion]
- [ ] CHK047 — Is it clear whether the `kotlin.androidLibrary {}` block should be removed entirely for Tier 1 modules or left as an empty block? [Ambiguity, Data Model says "removed", Quickstart shows no block]
- [ ] CHK048 — Is the scope of "convention plugin hardening" bounded — does FR-007 ("audit and update") have a defined completion criterion beyond "zero legacy references"? [Ambiguity, Spec §FR-007]

## Cross-Artifact Consistency

- [ ] CHK049 — Do task IDs in tasks.md (T001–T047) align with plan.md phase references and user story assignments? [Consistency, Cross-Artifact]
- [ ] CHK050 — Do data-model.md tier classifications match the tier groupings in tasks.md Phases 3–5? [Consistency, Cross-Artifact]
- [ ] CHK051 — Do quickstart.md migration patterns match the "After migration" column in data-model.md for all three tiers? [Consistency, Cross-Artifact]
- [ ] CHK052 — Are success criteria (SC-001–SC-007) in spec.md traceable to specific verification tasks in tasks.md? [Traceability, Cross-Artifact]
- [ ] CHK053 — Do the parallel execution opportunities documented in tasks.md (§Parallel Opportunities) align with the phase dependency constraints in plan.md? [Consistency, Cross-Artifact]

## Constitution Compliance

- [ ] CHK054 — Principle I (KMP Core): Are requirements specified to verify no `commonMain` source files are modified — only `build.gradle.kts` and convention plugin files? [Consistency, Spec §Source-Set Impact]
- [ ] CHK055 — Principle II (Zero Lint Tolerance): Are `spotlessCheck` and `detekt` verification requirements included in the migration acceptance criteria? [Consistency, Plan §Constitution Check]
- [ ] CHK056 — Principle IV (Privacy First): Is the `core/proto` submodule explicitly excluded from modifications in the requirements? [Consistency, Spec §Privacy Assessment]
- [ ] CHK057 — Principle VI (Verify Before Push): Are all six verification commands from the constitution mapped to specific tasks? [Consistency, Plan §Constitution Check]

---

## Notes

- Check items off as completed: `[x]`
- Items marked `[Gap]` indicate requirements that may be missing entirely
- Items marked `[Ambiguity]` or `[Conflict]` indicate requirements needing clarification before implementation
- The 28-vs-27 module count discrepancy (CHK008, CHK045) is the most critical ambiguity — resolve in spec.md before proceeding
- Cross-artifact consistency (CHK049–CHK053) is especially important given 5 design documents
