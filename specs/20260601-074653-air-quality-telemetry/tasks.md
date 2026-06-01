# Tasks: Air Quality Telemetry Display

**Input**: Design documents from `/specs/20260601-074653-air-quality-telemetry/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Not explicitly requested in spec. Test tasks omitted per convention.

**Verification**: Constitution-required validation (spotlessCheck, detekt, module tests) included in final phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: String resources and shared utilities needed by all subsequent phases

- [X] T001 Add air quality string resources (pm1_0, pm2_5, pm10, co2, air_quality_metrics_log, units) in `core/resources/src/commonMain/composeResources/values/strings.xml` then run `python3 scripts/sort-strings.py`
- [X] T002 [P] Create `Co2Severity` enum and `fromPpm()` color utility in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/Co2Severity.kt` mapping thresholds: Good 0–1000, Stuffy 1000–2000, Poor 2000–5000, Unsafe 5000–30000, Evacuate 30000+ to M3-compatible color tokens

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data layer changes that MUST be complete before ANY user story UI can function

**⚠️ CRITICAL**: No user story work can begin until this phase is complete — without these changes, air quality telemetry packets are silently dropped and no data persists.

- [X] T003 Add `airQualityMetrics: AirQualityMetrics = AirQualityMetrics()` field and `hasAirQualityMetrics` computed property to `Node` data class in `core/model/src/commonMain/kotlin/org/meshtastic/core/model/Node.kt`
- [X] T004 Add `air_quality_metrics` BLOB column (type `Telemetry`, default `Telemetry()`) to `NodeEntity` in `core/database/src/commonMain/kotlin/org/meshtastic/core/database/entity/NodeEntity.kt` with `airQualityMetrics` accessor property
- [X] T005 Bump database version 38→39 with auto-migration adding nullable `air_quality_metrics` column in `core/database/src/commonMain/kotlin/org/meshtastic/core/database/MeshtasticDatabase.kt`
- [X] T006 Handle `air_quality_metrics` oneof variant in `TelemetryPacketHandlerImpl` — add branch to `when` block that copies metrics to Node model via `nextNode.copy(airQualityMetrics = airQuality)` in `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/TelemetryPacketHandlerImpl.kt`

**Checkpoint**: Foundation ready — telemetry packets are now decoded, stored in-memory, and persisted to database. UI phases can begin.

---

## Phase 3: User Story 1 — View Current Air Quality Readings (Priority: P1) 🎯 MVP

**Goal**: Display latest PM1.0, PM2.5, PM10, and CO₂ values on the node detail info cards with CO₂ color-coded by severity thresholds.

**Independent Test**: Receive a single air quality telemetry packet and verify info cards render correct values with appropriate units and CO₂ coloring on the node detail screen.

### Implementation for User Story 1

- [X] T007 [US1] Create `AirQualityInfoCards` composable in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/AirQualityMetrics.kt` — build `VectorMetricInfo` list for PM1.0, PM2.5, PM10 (µg/m³) and CO₂ (ppm) from `Node.airQualityMetrics`, filtering zero values, using `MeshtasticIcons.AirQuality` icon
- [X] T008 [US1] Apply `Co2Severity.fromPpm()` color to the CO₂ info card value text in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/AirQualityMetrics.kt`
- [X] T009 [US1] Integrate `AirQualityInfoCards` into the node detail screen — render cards when `node.hasAirQualityMetrics` is true, positioned after existing Environment/Power info cards

**Checkpoint**: User Story 1 complete — users see at-a-glance air quality readings on the node detail screen with CO₂ severity coloring. Cards hide when no data is present and update live when new packets arrive.

---

## Phase 4: User Story 5 — Navigate to Air Quality Metrics Log (Priority: P1)

**Goal**: Provide discoverable navigation from the node detail screen to the Air Quality metrics log.

**Independent Test**: Verify "Air Quality" entry appears in the logs list with correct icon, and tapping it navigates to the Air Quality log screen.

### Implementation for User Story 5

- [X] T010 [P] [US5] Add `NodeDetailRoute.AirQualityMetrics(destNum: Int)` serializable data class to the `NodeDetailRoute` sealed interface in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- [X] T011 [P] [US5] Add `AIR_QUALITY` enum entry to `LogsType` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/model/LogsType.kt` — use `Res.string.air_quality_metrics_log`, `MeshtasticIcons.AirQuality` icon, and `NodeDetailRoute.AirQualityMetrics(it)` factory
- [X] T012 [US5] Register `NodeDetailRoute.AirQualityMetrics` route in `NodesNavigation.kt` via `addNodeDetailScreenComposable` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodesNavigation.kt`

**Checkpoint**: User Story 5 complete — Air Quality appears in the logs list and navigates to the metrics log screen (screen content implemented in next phases).

---

## Phase 5: User Story 2 — Browse Air Quality History (Priority: P2)

**Goal**: Display timestamped history cards on the Air Quality log screen with reverse-chronological readings and time frame filtering.

**Independent Test**: Populate telemetry history and verify the log screen shows timestamped cards in correct order with proper values and time frame filter works.

### Implementation for User Story 2

- [X] T013 [US2] Create `AirQualityMetricsScreen` composable in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt` — delegate to `BaseMetricScreen` with history content, time frame selector, and `onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.AIR_QUALITY) }` callback
- [X] T014 [US2] Implement air quality metrics state class (`AirQualityMetricsState`) providing timestamped history cards from `NodeEntity.air_quality_metrics` BLOB list, showing PM1.0, PM2.5, PM10, CO₂ values with CO₂ severity color in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt`
- [X] T015 [US2] Add air quality telemetry list query/accessor to `MetricsViewModel` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/MetricsViewModel.kt` — load historical telemetry entries for the node, support time frame filtering

**Checkpoint**: User Story 2 complete — users can browse timestamped air quality history with time frame filtering on the dedicated log screen.

---

## Phase 6: User Story 3 — Graph Air Quality Trends (Priority: P2)

**Goal**: Render thin-line Vico charts for selectable air quality metrics (PM1.0, PM2.5, PM10, CO₂) with dot marker only at the selected position.

**Independent Test**: Populate telemetry history and verify chart renders with correct data points, thin lines, legend entries, and tap-to-select behavior.

### Implementation for User Story 3

- [X] T016 [P] [US3] Create `AirQuality` chart metric enum (PM1_0, PM2_5, PM10, CO2) with label, unit, and proto field mapping in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt`
- [X] T017 [US3] Implement chart content section in `AirQualityMetricsScreen` using Vico thin-line chart with selectable metric series, dot marker only at cursor position, and graceful handling of zero/absent data points in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt`
- [X] T018 [US3] Wire chart selection to history list — when user taps a chart point, highlight/scroll to corresponding history card in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt`

**Checkpoint**: User Story 3 complete — users can visualize air quality trends with interactive thin-line charts and chart-to-list synchronization.

---

## Phase 7: User Story 4 — Export Air Quality Data (Priority: P3)

**Goal**: Enable CSV export of all air quality proto fields (27 columns) with time frame filtering applied to exported data.

**Independent Test**: Trigger CSV export and verify file contains correct headers (date, time, all proto fields) and values matching displayed history, with missing values as empty cells.

### Implementation for User Story 4

- [X] T019 [US4] Implement `saveAirQualityMetricsCSV()` in `MetricsViewModel` in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/MetricsViewModel.kt` — generate CSV with all 27 proto field columns per contracts/ui-contracts.md, respecting active time frame filter, empty cells for zero/missing values
- [X] T020 [US4] Wire export action into `AirQualityMetricsScreen`'s `BaseMetricScreen` `exportAction` parameter in `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/metrics/AirQualityMetrics.kt`

**Checkpoint**: User Story 4 complete — users can export filtered air quality data to CSV for external analysis.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verification, consistency checks, and constitution compliance

- [X] T021 [P] Review `AirQualityInfoCards` and `AirQualityMetricsScreen` against Meshtastic design standards — verify M3 component usage, typography (labelSmall/labelLarge), TalkBack semantics, touch targets, and units in content descriptions
- [X] T022 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto` — verify only raw sensor numerics are stored/displayed
- [ ] T023 [P] Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt :core:model:test :core:data:test :core:database:test :feature:node:test`
- [ ] T024 Validate end-to-end request→response→display loop works: tap request button on node detail and log screen, verify telemetry packet is handled, Node state updates, info cards refresh, log screen appends entry
- [X] T025 [P] Update `docs/en/user/telemetry-and-sensors.md` to document the Air Quality metrics log screen, info cards, CO₂ severity color-coding, chart usage, and CSV export. Update `last_updated` frontmatter. Verify DocBundleLoader registration if a new page is created.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on T001 (strings) for label references — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion (T003–T006 provide data flow)
- **User Story 5 (Phase 4)**: Depends on Phase 2 (needs Node model) — can run in parallel with Phase 3
- **User Story 2 (Phase 5)**: Depends on Phase 4 (needs route registered for navigation)
- **User Story 3 (Phase 6)**: Depends on Phase 5 (builds on log screen composable)
- **User Story 4 (Phase 7)**: Depends on Phase 5 (exports from same ViewModel data)
- **Polish (Phase 8)**: Depends on all user story phases complete

### User Story Dependencies

- **US1 (P1)**: Phase 2 only — fully independent of other stories
- **US5 (P1)**: Phase 2 only — fully independent of other stories, can parallel with US1
- **US2 (P2)**: Requires US5 (needs route/navigation to exist)
- **US3 (P2)**: Requires US2 (builds chart into log screen created in US2)
- **US4 (P3)**: Requires US2 (exports data from ViewModel state created in US2)

### Parallel Opportunities

- **Phase 1**: T001 and T002 can run in parallel (different files)
- **Phase 2**: T003 and T004 can run in parallel (different modules); T005 depends on T004; T006 depends on T003
- **Phase 3+4**: US1 (T007–T009) and US5 (T010–T012) can run in parallel after Phase 2
- **Phase 5+7**: US3 (T016) enum creation can parallel with US2 history implementation
- **Phase 8**: T021, T022, T023 all run in parallel (independent checks)

---

## Parallel Example: Phase 2 (Foundation)

```bash
# Launch independent model + entity changes together:
Task T003: "Add airQualityMetrics field to Node model"
Task T004: "Add air_quality_metrics BLOB column to NodeEntity"

# Then sequential (depends on T004):
Task T005: "Bump database version 38→39"

# Then sequential (depends on T003):
Task T006: "Handle air_quality_metrics in TelemetryPacketHandlerImpl"
```

## Parallel Example: MVP Stories (Phases 3 + 4)

```bash
# After Phase 2 completes, run both P1 stories in parallel:
# Developer A: User Story 1 (info cards)
Task T007: "Create AirQualityInfoCards composable"
Task T008: "Apply CO₂ severity color"
Task T009: "Integrate into node detail screen"

# Developer B: User Story 5 (navigation)
Task T010: "Add NodeDetailRoute.AirQualityMetrics"
Task T011: "Add AIR_QUALITY to LogsType enum"
Task T012: "Register route in NodesNavigation.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 5 Only)

1. Complete Phase 1: Setup (strings + CO₂ utility)
2. Complete Phase 2: Foundational (model + entity + migration + handler)
3. Complete Phase 3: User Story 1 (info cards on node detail)
4. Complete Phase 4: User Story 5 (navigation plumbing)
5. **STOP and VALIDATE**: Info cards show live data, navigation works
6. Run `./gradlew spotlessCheck detekt :core:model:test :core:data:test :core:database:test :feature:node:test`

### Incremental Delivery

1. Setup + Foundational → Data pipeline works end-to-end
2. Add US1 + US5 → MVP: View readings + navigate (deployable)
3. Add US2 → History browsing with time frame filter
4. Add US3 → Charts for trend analysis
5. Add US4 → CSV export for external tools
6. Polish → Design review, privacy check, full CI validation

---

## Notes

- All new code in `commonMain` source sets (Constitution I)
- Follow `EnvironmentMetrics` patterns exactly (info cards, log screen, CSV export)
- `Co2Severity` thresholds per design/issues/53: Good ≤1000, Stuffy 1000–2000, Poor 2000–5000, Unsafe 5000+, Evacuate 30000+
- Chart style: thin lines only, dot marker at selection point only (design/issues/53)
- Gas resistance intentionally excluded (already shown as IAQ in Environment metrics)
- Proto submodule is read-only — `AirQualityMetrics` message already exists upstream
- Request button infrastructure already exists (TelemetricActionsSection + CommandSenderImpl) — only the response handler is new
