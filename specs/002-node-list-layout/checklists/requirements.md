# Requirements Quality Checklist — Node List Layout

**Purpose**: Validate the quality, completeness, and clarity of requirements in `spec.md` and related artifacts before implementation.
**Created**: 2026-05-07
**Updated**: 2026-05-09
**Artifact Sources**: `spec.md`, `plan.md`, `tasks.md`, `data-model.md`, `research.md`, `m3-accessibility-audit.md`

---

## Scope and User Value

- [ ] CHK001 — Is the user problem (large meshes requiring denser node lists) clearly articulated with a concrete threshold (100+ nodes)? [Completeness, Spec §Summary]
- [ ] CHK002 — Is the feature scope explicitly bounded to layout density switching, compact toggles, adaptive sizing, and help documentation? [Completeness, Spec §Goals]
- [ ] CHK003 — Are non-goals explicitly documented (no Complete layout toggles, no new data fields, no platform-specific UI)? [Completeness, Spec §Non-Goals]

## User Stories

- [ ] CHK004 — Are all four user stories present and prioritized P1–P3 with rationale for each priority level? [Completeness, Spec §User Scenarios]
- [ ] CHK005 — Is each user story independently testable with a documented test procedure? [Measurability, Spec §User Scenarios]
- [ ] CHK006 — Do acceptance scenarios cover both success paths and error/edge paths for each story? [Coverage, Spec §User Scenarios]
- [ ] CHK007 — Is the 300ms/60fps animation requirement in Story 1 Scenario 3 measurable and testable? [Measurability, Spec §US1-AS3]

## Architecture Fit

- [ ] CHK008 — Does the spec consistently use Compose Multiplatform + Material 3 terminology (not Android-only Jetpack Compose)? [Consistency, Spec §Architecture]
- [ ] CHK009 — Is the `commonMain`-only constraint explicitly stated with no platform-specific code paths? [Clarity, Spec §Architecture]
- [ ] CHK010 — Does the spec modify existing modules (`feature/node`, `feature/settings`, `core/prefs`) rather than creating unnecessary new modules? [Consistency, Plan §Project Structure]
- [ ] CHK011 — Is Navigation 3 integration documented (no new routes needed, settings embedded in existing screen)? [Completeness, Plan §Navigation]
- [ ] CHK012 — Is the data flow from DataStore → ViewModel → Screen → Item composable fully traced? [Completeness, Spec §Data Flow]

## Preferences and Persistence

- [ ] CHK013 — Are all 10 DataStore keys documented with names, types, and defaults? [Completeness, Spec §Toggle Reference]
- [ ] CHK014 — Is the density enum persistence format specified (string enum name) with a safe fallback for invalid values? [Clarity, Data Model §Density Enum]
- [ ] CHK015 — Are toggle defaults explicitly specified (all `true` except `lastHeardIsRelative` = `false`)? [Clarity, Spec §FR-005]
- [ ] CHK016 — Is the requirement to use `NodeListLayoutPreferences` enum values (not raw strings) for DataStore keys documented? [Clarity, Spec §NFR-003]
- [ ] CHK017 — Is the eager-seeded `StateFlow` pattern specified to prevent UI flicker during DataStore cold reads? [Completeness, Data Model §Preference Access Pattern]

## Functional Requirements — Density Switching (FR-001 to FR-002)

- [ ] CHK018 — Is the `SegmentedButton` component for density selection explicitly specified as `SingleChoiceSegmentedButtonRow`? [Clarity, Spec §FR-001]
- [ ] CHK019 — Is the DataStore key `nodeListDensity` and its persistence behavior across app launches specified? [Completeness, Spec §FR-002]

## Functional Requirements — Compact Toggles (FR-003 to FR-007)

- [ ] CHK020 — Are the 9 toggles specified to use `SwitchPreference` (from `core:ui`) rather than raw `Switch` composables? [Clarity, Spec §FR-003, Audit §1.1]
- [ ] CHK021 — Is the toggle display order specified to match the compact layout visual position? [Clarity, Spec §FR-003]
- [ ] CHK022 — Is per-toggle DataStore persistence via `NodeListLayoutPreferences` keys required? [Completeness, Spec §FR-004]
- [ ] CHK023 — Is the "Relative Last Heard Time" disabled state dependency on "Last Heard Time" toggle specified with `enabled = false`? [Clarity, Spec §FR-006]
- [ ] CHK024 — Is the descriptive text for Complete mode ("The Complete layout displays all available node data...") explicitly specified? [Completeness, Spec §FR-007]

## Functional Requirements — Live Preview (FR-008)

- [ ] CHK025 — Is the live preview data source specified (first node from Room KMP query sorted by `lastHeard` descending)? [Clarity, Spec §FR-008]
- [ ] CHK026 — Is the empty-state behavior specified when the database has zero nodes? [Edge Case, Gap]
- [ ] CHK027 — Is real-time toggle reflection in the preview specified via `collectAsState()`? [Completeness, Spec §FR-008]

## Functional Requirements — Compact Layout Structure (FR-009 to FR-014)

- [ ] CHK028 — Is the two-column layout structure (Column 1: fixed width chip+battery, Column 2: `weight(1f)` content) explicitly defined? [Completeness, Spec §FR-009]
- [ ] CHK029 — Is the `NodeChip` composable required to maintain consistent card styling at all sizes? [Clarity, Spec §FR-010]
- [ ] CHK030 — Is the adaptive chip sizing formula `max(36.dp, min(70.dp, 24.dp × lineCount))` specified with the `lineCount` derivation logic? [Clarity, Spec §FR-011]
- [ ] CHK031 — Is `Modifier.defaultMinSize()` (not hard `Modifier.size()`) required for font scaling support? [Clarity, Spec §FR-011, Audit §2.8]
- [ ] CHK032 — Is Row 1 (name) specified as non-toggleable with all components listed (`NodeKeyStatusIcon`, long name, favorite star)? [Completeness, Spec §FR-012]
- [ ] CHK033 — Is the last heard timestamp validity defined (non-zero, not more than 1 year in the future)? [Clarity, Spec §FR-013]
- [ ] CHK034 — Is Row 3 combined icons layout specified with `Row(horizontalArrangement = spacedBy(6.dp))` and `VerticalDivider` separators? [Completeness, Spec §FR-014]
- [ ] CHK035 — Is `VerticalDivider` specified to use `Modifier.fillMaxHeight()` inside `Row(Modifier.height(IntrinsicSize.Min))` rather than hardcoded height? [Clarity, Spec §FR-014, Audit §1.4]

## Functional Requirements — Conditional Field Rendering (FR-015 to FR-020)

- [ ] CHK036 — Are all data conditions for Distance and Bearing rendering documented (toggle on, has positions, not connected node, valid location data)? [Completeness, Spec §FR-015]
- [ ] CHK037 — Is the Hops Away condition specified (`hopsAway > 0`)? [Completeness, Spec §FR-016]
- [ ] CHK038 — Are all Signal rendering conditions specified (toggle on, `hopsAway == 0`, `snr != 0`, `viaMqtt == false`)? [Completeness, Spec §FR-017]
- [ ] CHK039 — Is the Signal icon `contentDescription` requirement specified for WCAG 1.4.1 compliance (e.g., "Signal: Good")? [Clarity, Spec §FR-017]
- [ ] CHK040 — Is the Channel condition specified (`channel > 0`, non-default channel)? [Completeness, Spec §FR-018]
- [ ] CHK041 — Are all Device Role sub-icons specified (role icon, unmessagable, store-and-forward, MQTT)? [Completeness, Spec §FR-019]
- [ ] CHK042 — Are all Log Icons components listed (device metrics, positions/mappin, environment, detection sensor, trace routes/signpost)? [Completeness, Spec §FR-020]
- [ ] CHK043 — Is the Log Icons data condition specified (at least one of: positions, environment metrics, detection sensor metrics, or trace routes)? [Completeness, Spec §FR-020]

## Functional Requirements — Complete Layout (FR-021 to FR-022)

- [ ] CHK044 — Is the Complete layout specified as unconditional (no user toggles, fields hidden only when data absent)? [Clarity, Spec §FR-021]
- [ ] CHK045 — Is the Complete layout signal display specified to use `LoraSignalIndicator` / `NodeSignalQuality` (quality icon + SNR/RSSI text), not a single colored icon? [Clarity, Spec §FR-022]
- [ ] CHK046 — Are the signal display differences between Complete and Compact layouts documented with rationale? [Completeness, Research §R-004]

## Functional Requirements — Help Sheet (FR-023 to FR-024)

- [ ] CHK047 — Are all four signal quality entries specified with quality levels, colors, and icon references (Good/green, Fair/yellow, Bad/orange, None/red)? [Completeness, Spec §FR-023]
- [ ] CHK048 — Is the `LoraSignalIndicator` documentation entry specified explaining how SNR and RSSI combine into a quality level? [Completeness, Spec §FR-024]
- [ ] CHK049 — Is the help sheet specified as a `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`? [Clarity, Audit §1.1]

## Functional Requirements — Accessibility (FR-025)

- [ ] CHK050 — Is `Modifier.semantics(mergeDescendants = true)` on the outer Card specified for BOTH layouts? [Completeness, Spec §FR-025, Audit §2.1]
- [ ] CHK051 — Is the composed `contentDescription` aggregation specified with all fields listed (name, connection status, favorite, last heard, online/offline, role, hops, battery, distance, heading, signal)? [Completeness, Spec §FR-025]
- [ ] CHK052 — Is `role = Role.Button` specified on clickable rows for TalkBack "double tap to activate"? [Completeness, Spec §FR-025, Audit §2.3]
- [ ] CHK053 — Is the `titleMediumEmphasized` typography requirement specified for compact mode node names to match the Complete layout? [Consistency, Spec §FR-025]
- [ ] CHK054 — Is the critical severity of the existing `NodeItem` TalkBack issue (8-12 separate focus stops per node) documented? [Completeness, Audit §2.1]

## Functional Requirements — Spacing & Formatting (FR-026 to FR-028)

- [ ] CHK055 — Is compact `spacedBy(2.dp)` inter-row spacing documented as an intentional M3 deviation with rationale? [Clarity, Spec §FR-026]
- [ ] CHK056 — Are padding values specified for both layouts (2.dp compact, 3.dp complete) as intentional M3 deviations? [Clarity, Spec §FR-027]
- [ ] CHK057 — Is `NumberFormatter.format()` required for all floating-point values before rendering? [Completeness, Spec §FR-028]

## Non-Functional Requirements (NFR-001 to NFR-004)

- [ ] CHK058 — Is the "within one recomposition cycle" requirement for toggle state changes measurable? [Measurability, Spec §NFR-001]
- [ ] CHK059 — Is the 60fps scrolling requirement specified with a concrete node count threshold (200+ nodes)? [Measurability, Spec §NFR-002]
- [ ] CHK060 — Is the `NodeListLayoutPreferences` enum value requirement for DataStore keys specified to prevent key string drift? [Clarity, Spec §NFR-003]
- [ ] CHK061 — Is the `LazyColumn` stable `key` parameter requirement specified for efficient rendering? [Completeness, Spec §NFR-004]

## Signal Quality Thresholds

- [ ] CHK062 — Are all four signal quality threshold conditions specified with exact SNR and RSSI values? [Completeness, Spec §Signal Quality Thresholds]
- [ ] CHK063 — Is the threshold evaluation order specified (GOOD first, then FAIR, BAD, NONE as fallback)? [Clarity, Spec §Signal Quality Thresholds]
- [ ] CHK064 — Is the signal quality function (`determineSignalQuality`) specified as using absolute thresholds (not modem-preset-relative)? [Clarity, Spec §Assumptions]

## Success Criteria (SC-001 to SC-006)

- [ ] CHK065 — Is SC-001 (density switch re-render within 1 second) measurable with a defined test method? [Measurability, Spec §SC-001]
- [ ] CHK066 — Is SC-002 (all 9 toggles persist across launches) testable with clear pass/fail criteria? [Measurability, Spec §SC-002]
- [ ] CHK067 — Is SC-003 (live preview accuracy) testable for both density modes? [Measurability, Spec §SC-003]
- [ ] CHK068 — Is SC-004 (help sheet content: 4 color-coded icons + LoraSignalIndicator entry) fully enumerated? [Completeness, Spec §SC-004]
- [ ] CHK069 — Is SC-005 (40% compact height reduction) measurable and is the comparison baseline defined (node with full data)? [Measurability, Spec §SC-005]
- [ ] CHK070 — Is SC-006 (TalkBack complete description) testable with defined content elements? [Measurability, Spec §SC-006]

## Edge Cases

- [ ] CHK071 — Is the all-toggles-disabled state specified (only name row + minimum 36.dp chip, battery hidden)? [Coverage, Spec §Edge Cases]
- [ ] CHK072 — Is the missing-data behavior specified (field absent, no placeholder or empty state)? [Clarity, Spec §Edge Cases]
- [ ] CHK073 — Is Signal/Hops mutual exclusivity documented (Signal when `hopsAway == 0`, Hops when `hopsAway > 0`)? [Consistency, Spec §Edge Cases]
- [ ] CHK074 — Is Channel 0 hiding specified regardless of toggle state? [Completeness, Spec §Edge Cases]
- [ ] CHK075 — Is the connected node exclusion from distance display specified? [Completeness, Spec §Edge Cases]
- [ ] CHK076 — Is MQTT node exclusion from signal display specified with rationale (SNR/RSSI not meaningful)? [Completeness, Spec §Edge Cases]
- [ ] CHK077 — Is the future date guard (> 1 year) specified for last heard timestamp? [Completeness, Spec §Edge Cases]
- [ ] CHK078 — Is the `lineCount` derivation specified as based on toggle state, not actual data presence? [Clarity, Research §R-003]

## Material 3 & Expressive

- [ ] CHK079 — Is `SwitchPreference` (from `core:ui`) specified for settings toggles instead of raw `Switch`? [Consistency, Audit §1.1]
- [ ] CHK080 — Is `titleMediumEmphasized` (M3 Expressive) specified for node names in both layouts? [Consistency, Spec §FR-025]
- [ ] CHK081 — Is `SegmentedButton` specified to follow the existing codebase pattern (`SingleChoiceSegmentedButtonRow`)? [Consistency, Audit §1.1]
- [ ] CHK082 — Is `ModalBottomSheet` specified with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`? [Clarity, Audit §1.1]
- [ ] CHK083 — Is `VerticalDivider` specified to use `fillMaxHeight()` inside `Row(Modifier.height(IntrinsicSize.Min))`? [Clarity, Audit §1.4]
- [ ] CHK084 — Are compact spacing deviations (2.dp inter-row, 2.dp padding) documented as intentional M3 deviations? [Clarity, Spec §FR-026, FR-027]
- [ ] CHK085 — Are typography requirements specified for settings section headers, toggle labels, and help sheet entries? [Gap, Audit §1.2]
- [ ] CHK086 — Is the help button specified to use `IconButton` (not raw `Icon` + `clickable`) for 48dp minimum touch target? [Clarity, Audit §2.5]

## Accessibility — Additional Audit Findings

- [ ] CHK087 — Is the TalkBack focus behavior specified when density changes (live region announcement, scroll position preservation)? [Gap, Audit §2.7]
- [ ] CHK088 — Is the decorative icon merge strategy specified for the row-level semantics context (which icons contribute vs use `null` contentDescription)? [Gap, Audit §2.2]
- [ ] CHK089 — Is the `NodeChip` contrast ratio concern documented for arbitrary node colors (WCAG AA 4.5:1)? [Gap, Audit §2.9]

## Resource Conventions

- [ ] CHK090 — Are string resources specified for `core/resources/.../values/strings.xml` with `stringResource(Res.string.key)` access pattern? [Completeness, Spec §Assumptions]
- [ ] CHK091 — Are all icon references specified to use `MeshtasticIcons` (from `core/ui/icon/`)? [Consistency, Spec §Architecture]
- [ ] CHK092 — Is `NumberFormatter.format()` specified for all float values before display (CMP formatting constraint)? [Completeness, Spec §FR-028]
- [ ] CHK093 — Is `determineSignalQuality(snr, rssi)` specified with absolute thresholds, not modem-preset-relative? [Clarity, Spec §Assumptions]
- [ ] CHK094 — Do all component references match actual codebase names (`NodeChip`, `LoraSignalIndicator`, `NodeSignalQuality`, `NodeKeyStatusIcon`, etc.)? [Consistency, Spec §Architecture]

## Data Model Completeness

- [ ] CHK095 — Are all 16 node data fields used by the layout documented with types and conditions? [Completeness, Data Model §Node Data Fields]
- [ ] CHK096 — Is the layout specified as read-only (never writes to the Node model)? [Clarity, Data Model §Overview]
- [ ] CHK097 — Is the `lineCount` computation logic fully specified with all three cases (1, 2, 3 rows)? [Completeness, Data Model §Adaptive Chip Sizing]
- [ ] CHK098 — Is the invalid density string fallback to `COMPLETE` documented? [Completeness, Data Model §Validation Rules]

## Testing and Delivery

- [ ] CHK099 — Are unit tests specified for preference defaults, `lineCount` calculation, and invalid density string fallback? [Coverage, Tasks §Phase 7]
- [ ] CHK100 — Are Compose UI tests specified for both layout variants? [Coverage, Tasks §Phase 7]
- [ ] CHK101 — Are unit tests specified for edge cases (future date filtering, channel 0, signal/hops exclusivity, connected node distance, MQTT signal)? [Coverage, Tasks §NL-T043]
- [ ] CHK102 — Is the live preview empty-state placeholder specified for when Room database has zero nodes? [Coverage, Tasks §NL-T030]
- [ ] CHK103 — Is the full verification command documented (`./gradlew spotlessApply detekt assembleDebug test allTests`)? [Completeness, Tasks §NL-T047]
- [ ] CHK104 — Is the Phase 1 design gate (NL-T001) specified as blocking all UI work? [Completeness, Tasks §Phase 1]

## Constitution Compliance

- [ ] CHK105 — Principle I (KMP Core): Is all code specified for `commonMain` with no `java.*`/`android.*` imports? [Consistency, Constitution §I]
- [ ] CHK106 — Principle II (Zero Lint Tolerance): Is `spotlessApply` + `detekt` passage required? [Consistency, Constitution §II]
- [ ] CHK107 — Principle III (CMP UI): Are CMP composables specified with `NumberFormatter.format()` for floats? [Consistency, Constitution §III]
- [ ] CHK108 — Principle IV (Privacy First): Is it confirmed no PII, location, or key logging/exposure is introduced? [Consistency, Constitution §IV]
- [ ] CHK109 — Principle V (Design Standards): Is NL-T000 UI-GATE review specified before UI work? [Consistency, Constitution §V]
- [ ] CHK110 — Principle VI (Verify Before Push): Is local verification specified before push? [Consistency, Constitution §VI]

## Assumptions & Dependencies

- [ ] CHK111 — Is the assumption that `Node` data model is fully populated by the packet pipeline validated? [Assumption, Spec §Assumptions]
- [ ] CHK112 — Are all pre-existing reusable components listed and confirmed to exist in `core:ui`? [Dependency, Spec §Assumptions]
- [ ] CHK113 — Is the `safeCatching {}` (not `runCatching {}`) error handling convention documented? [Clarity, Plan §Design Constraints]
- [ ] CHK114 — Is the `ioDispatcher` requirement from `org.meshtastic.core.common.util` documented? [Completeness, Plan §Design Constraints]
- [ ] CHK115 — Is the `python3 scripts/sort-strings.py` post-string-addition step documented? [Completeness, Plan §Design Constraints]

## Cross-Artifact Consistency

- [ ] CHK116 — Do the task IDs in `tasks.md` (47 tasks across 7 phases) align with the plan's phase descriptions? [Consistency, Plan §Phase Alignment]
- [ ] CHK117 — Does the data model's preference key list match the spec's Toggle Reference table exactly? [Consistency, Data Model vs Spec §Toggle Reference]
- [ ] CHK118 — Do research decisions (R-001 to R-005) align with the corresponding spec requirements? [Consistency, Research vs Spec]
- [ ] CHK119 — Are all M3/accessibility audit findings (6 required changes, 4 recommendations) reflected in the spec or tasks? [Completeness, Audit §Summary]
- [ ] CHK120 — Does the critical path in the plan (Phase 1→2→3→4→5→7) match the dependency graph in tasks? [Consistency, Plan §Critical Path vs Tasks §Dependency Graph]
