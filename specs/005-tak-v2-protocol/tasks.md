# Tasks: TAK v2 Protocol Integration

**Input**: Design documents from `/specs/005-tak-v2-protocol/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅
**Status**: Retroactive — documents work completed in PR #5434 (99 files, +4698 lines). Use as verification checklist.

**Tests**: Included — the implementation ships with 12 test classes and 89+ test methods covering all CoT types.

**Verification**: Constitution-required validation tasks included (spotlessCheck, detekt, compile, allTests).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Module & Dependencies)

**Purpose**: Create the `core:takserver` KMP module, configure dependencies, and establish project structure.

- [X] T001 Create `core/takserver/build.gradle.kts` with TAKPacket-SDK v0.1.3, xmlutil, zstd-jni 1.5.7-7, Ktor Network, Okio, Koin, and Kermit dependencies
- [X] T002 Register `:core:takserver` module in `settings.gradle.kts`
- [X] T003 [P] Create source set directory structure: `commonMain`, `commonTest`, `jvmAndroidMain`, `androidMain`, `jvmMain`, `iosMain` under `core/takserver/src/`
- [X] T004 [P] Add bundled mTLS certificates to `core/takserver/src/jvmAndroidMain/resources/tak_certs/`
- [X] T005 [P] Add 40 CoT XML test fixture files to `core/takserver/src/jvmAndroidMain/resources/tak_test_fixtures/`
- [X] T006 [P] Create Koin DI module in `core/takserver/src/commonMain/kotlin/org/meshtastic/core/takserver/di/CoreTakServerModule.kt`

---

## Phase 2: Foundational (Shared Models, Constants & Utilities)

**Purpose**: Core domain models, constants, and utilities that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T007 Define domain models (CoTMessage, CoTContact, CoTGroup, CoTStatus, CoTTrack, CoTChat, TAKClientInfo, InboundCoTMessage) in `core/takserver/src/commonMain/kotlin/.../TAKModels.kt`
- [X] T008 [P] Define constants (DEFAULT_TAK_PORT, MAX_TAK_WIRE_PAYLOAD_BYTES, MAX_DECOMPRESSED_SIZE, TAK_COORDINATE_SCALE, DICT_IDs, OFFLINE_QUEUE_TTL, KEEPALIVE_INTERVAL, MIN_MESH_STALE_TTL) in `core/takserver/src/commonMain/kotlin/.../TAKDefaults.kt`
- [X] T009 [P] Implement XML escaping utilities (5 special characters: &, <, >, ", ') in `core/takserver/src/commonMain/kotlin/.../XmlUtils.kt`
- [X] T010 [P] Implement CoT XML data classes for serialization in `core/takserver/src/commonMain/kotlin/.../CoTXmlDataClasses.kt`
- [X] T011 [P] Implement ATAK preference XML schema classes in `core/takserver/src/commonMain/kotlin/.../TAKPrefXmlDataClasses.kt`
- [X] T012 [P] Implement shared coordinate scaling and conversion helpers in `core/takserver/src/commonMain/kotlin/.../TakConversionHelpers.kt`
- [X] T013 [P] Implement `TakV2TypeMapper` with 23 CoT types + 4 HOW types + CotType_Other fallback in `core/takserver/src/commonMain/kotlin/.../TakV2TypeMapper.kt`
- [X] T014 [P] Implement `CoTDetailStripper` to strip 16 bloat XML elements (regex-based) in `core/takserver/src/commonMain/kotlin/.../CoTDetailStripper.kt`
- [X] T015 [P] Implement streaming `CoTXmlParser` (XML → CoTMessage) in `core/takserver/src/commonMain/kotlin/.../CoTXmlParser.kt`
- [X] T016 [P] Implement `CoTXml` (CoTMessage → XML serialization) in `core/takserver/src/commonMain/kotlin/.../CoTXml.kt`
- [X] T017 [P] Implement TCP stream framing in `core/takserver/src/commonMain/kotlin/.../CoTXmlFrameBuffer.kt`
- [X] T018 [P] Implement shared conversion helpers in `core/takserver/src/commonMain/kotlin/.../CoTConversion.kt`
- [X] T019 [P] Add `supportsTakV2` capability flag (firmware >= 2.8.0 check) in `core/model/src/commonMain/kotlin/.../Capabilities.kt`
- [X] T020 [P] Define `AtakFileWriter` expect interface in `core/takserver/src/commonMain/kotlin/.../AtakFileWriter.kt`
- [X] T021 [P] Define `ZipArchiver` expect interface in `core/takserver/src/commonMain/kotlin/.../ZipArchiver.kt`
- [X] T022 [P] Define `TakFixtureLoader` expect interface in `core/takserver/src/commonMain/kotlin/.../TakFixtureLoader.kt`
- [X] T023 [P] Define `TakV2Compressor` expect interface in `core/takserver/src/commonMain/kotlin/.../TakV2Compressor.kt`
- [X] T024 [P] Define `TAKServer` expect interface in `core/takserver/src/commonMain/kotlin/.../TAKServer.kt`

**Checkpoint**: Foundation ready — all shared types, constants, and interfaces established.

---

## Phase 3: User Story 1 — Send Rich Tactical Data Over Mesh (Priority: P1) 🎯 MVP

**Goal**: Convert all CoT types from ATAK clients into compressed TAKPacketV2 and transmit over port 78. Receiving nodes decompress and forward to connected TAK clients.

**Independent Test**: Drop a hostile marker on one ATAK instance → appears on remote ATAK with correct type, icon, and position.

### Tests for User Story 1

- [X] T025 [P] [US1] Implement `CoTXmlParserTest` (XML parsing for all 23 CoT types) in `core/takserver/src/commonTest/kotlin/.../CoTXmlParserTest.kt`
- [X] T026 [P] [US1] Implement `CoTXmlTest` (CoTMessage → XML round-trip) in `core/takserver/src/commonTest/kotlin/.../CoTXmlTest.kt`
- [X] T027 [P] [US1] Implement `CoTConversionTest` (conversion helper correctness) in `core/takserver/src/commonTest/kotlin/.../CoTConversionTest.kt`
- [X] T028 [P] [US1] Implement `CoTDetailStripperTest` (16-element stripping) in `core/takserver/src/commonTest/kotlin/.../CoTDetailStripperTest.kt`
- [X] T029 [P] [US1] Implement `TAKPacketV2RawDetailTest` (raw_detail round-trip for shapes/markers/routes) in `core/takserver/src/commonTest/kotlin/.../TAKPacketV2RawDetailTest.kt`
- [X] T030 [P] [US1] Implement `XmlUtilsTest` (escaping correctness for 5 special chars) in `core/takserver/src/commonTest/kotlin/.../XmlUtilsTest.kt`
- [X] T031 [P] [US1] Implement `TAKDefaultsTest` (constants validation) in `core/takserver/src/commonTest/kotlin/.../TAKDefaultsTest.kt`

### Implementation for User Story 1

- [X] T032 [US1] Implement `TAKPacketV2Conversion` (CoTMessage ↔ TAKPacketV2 for all CoT types including pli, chat, raw_detail payloads) in `core/takserver/src/commonMain/kotlin/.../TAKPacketV2Conversion.kt`
- [X] T033 [P] [US1] Implement `TakV2Compressor` actual (zstd dictionary compression via TAKPacket-SDK) in `core/takserver/src/jvmAndroidMain/kotlin/.../TakV2Compressor.kt`
- [X] T034 [P] [US1] Implement `TakV2Compressor` iOS stub (uncompressed, flags=0xFF) in `core/takserver/src/iosMain/kotlin/.../TakV2Compressor.kt`
- [X] T035 [US1] Implement `RouteDataPackageGenerator` (Route CoT → KML data package with MissionPackageManifest v2) in `core/takserver/src/commonMain/kotlin/.../RouteDataPackageGenerator.kt`
- [X] T036 [US1] Implement `TAKMeshIntegration` outbound path (TAK client → CoT parse → detail strip → compress → MTU check → port 78 send) in `core/takserver/src/commonMain/kotlin/.../TAKMeshIntegration.kt`
- [X] T037 [US1] Implement `TAKMeshIntegration` inbound path (port 78 receive → decompress → decode → broadcast to TAK clients) in `core/takserver/src/commonMain/kotlin/.../TAKMeshIntegration.kt`
- [X] T038 [P] [US1] Implement `TakFixtureLoader` actual (JVM resource loading for test fixtures) in `core/takserver/src/jvmAndroidMain/kotlin/.../TakFixtureLoader.kt`
- [X] T039 [P] [US1] Implement `TakFixtureLoader` iOS stub in `core/takserver/src/iosMain/kotlin/.../TakFixtureLoader.kt`
- [X] T040 [P] [US1] Implement `ZipArchiver` actual (java.util.zip) in `core/takserver/src/jvmAndroidMain/kotlin/.../ZipArchiver.kt`
- [X] T041 [P] [US1] Implement `ZipArchiver` iOS stub in `core/takserver/src/iosMain/kotlin/.../ZipArchiver.kt`
- [X] T042 [P] [US1] Implement `AtakFileWriter` actual (SAF/private directory) in `core/takserver/src/androidMain/kotlin/.../AtakFileWriter.kt`
- [X] T043 [P] [US1] Implement `AtakFileWriter` actual (desktop filesystem) in `core/takserver/src/jvmMain/kotlin/.../AtakFileWriter.kt`
- [X] T044 [P] [US1] Implement `AtakFileWriter` iOS stub in `core/takserver/src/iosMain/kotlin/.../AtakFileWriter.kt`
- [X] T045 [US1] Implement `TakMeshTestRunner` (in-app diagnostic runner for per-CoT-type byte size and round-trip verification) in `core/takserver/src/commonMain/kotlin/.../TakMeshTestRunner.kt`

**Checkpoint**: All 28 CoT type mappings encode/decode correctly. Compressed PLI < 100 bytes. Shapes/markers/routes fit within 225-byte MTU after stripping + compression.

---

## Phase 4: User Story 2 — Legacy Fallback for Mixed Firmware (Priority: P1)

**Goal**: Auto-detect firmware version and use TAKPacket v1 (port 72) for radios < 2.8.0. Drop unsupported CoT types on legacy with a warning. Always accept inbound on both ports.

**Independent Test**: Connect a node with firmware 2.7.x and verify PLI/GeoChat work on port 72 while markers are dropped with warning.

### Tests for User Story 2

- [X] T046 [P] [US2] Implement `TAKPacketConversionTest` (v1 PLI + GeoChat encode/decode) in `core/takserver/src/commonTest/kotlin/.../TAKPacketConversionTest.kt`

### Implementation for User Story 2

- [X] T047 [US2] Implement `TAKPacketConversion` (CoTMessage ↔ TAKPacket v1 for PLI and GeoChat only) in `core/takserver/src/commonMain/kotlin/.../TAKPacketConversion.kt`
- [X] T048 [US2] Implement version-gating logic in `TAKMeshIntegration` — check `Capabilities.supportsTakV2` per-send to select port 72 vs port 78 path in `core/takserver/src/commonMain/kotlin/.../TAKMeshIntegration.kt`
- [X] T049 [US2] Implement dual-port inbound listener in `TAKMeshIntegration` — accept and decode packets from both port 72 and port 78 regardless of local firmware in `core/takserver/src/commonMain/kotlin/.../TAKMeshIntegration.kt`
- [X] T050 [US2] Implement CoT type filtering for legacy path — drop non-PLI/non-GeoChat types with logged warning on firmware < 2.8.0 in `core/takserver/src/commonMain/kotlin/.../TAKMeshIntegration.kt`

**Checkpoint**: Mixed-firmware mesh maintains PLI/GeoChat for legacy nodes. Advanced CoT types dropped cleanly with user-visible warning.

---

## Phase 5: User Story 3 — TAK Server Lifecycle and Reliability (Priority: P2)

**Goal**: Run a reliable local TLS/mTLS server on port 8089, maintain ATAK/iTAK connections through screen-off and Doze mode, support data package export.

**Independent Test**: Enable TAK server, connect ATAK via exported data package, verify connection persists for 10+ minutes with screen off.

### Tests for User Story 3

- [X] T051 [P] [US3] Implement `CoTXmlFrameBufferTest` (TCP stream framing correctness) in `core/takserver/src/commonTest/kotlin/.../CoTXmlFrameBufferTest.kt`

### Implementation for User Story 3

- [X] T052 [US3] Implement `TAKServerJvm` actual (JSSE SSLServerSocket with mTLS on port 8089) in `core/takserver/src/jvmAndroidMain/kotlin/.../TAKServerJvm.kt`
- [X] T053 [P] [US3] Implement `TAKServerIos` actual (no-op stub) in `core/takserver/src/iosMain/kotlin/.../TAKServerIos.kt`
- [X] T054 [US3] Implement `TAKClientConnection` (per-client coroutine scope, SupervisorJob, BufferedOutputStream + writeMutex, XML stream framing) in `core/takserver/src/jvmAndroidMain/kotlin/.../TAKClientConnection.kt`
- [X] T055 [US3] Implement `TakCertLoader` (load bundled .p12/.pem certificates for mTLS) in `core/takserver/src/jvmAndroidMain/kotlin/.../TakCertLoader.kt`
- [X] T056 [US3] Implement `TAKServerManager` lifecycle (start/stop, client list, broadcast, 10-second keepalive interval, offline message queue: 50-msg cap, 5-min TTL) in `core/takserver/src/commonMain/kotlin/.../TAKServerManager.kt`
- [X] T057 [US3] Implement `TAKDataPackageGenerator` (export .zip with server certificates and connection config for ATAK/iTAK import) in `core/takserver/src/commonMain/kotlin/.../TAKDataPackageGenerator.kt`
- [X] T058 [US3] Add `PARTIAL_WAKE_LOCK` acquisition in `MeshService` when TAK server is active in `core/service/src/androidMain/kotlin/.../MeshService.kt`
- [X] T059 [US3] Wire `TAKMeshIntegration.start(scope)` call from `MeshServiceOrchestrator` in `core/service/src/commonMain/kotlin/.../MeshServiceOrchestrator.kt`

**Checkpoint**: TAK server starts on port 8089, accepts mTLS connections, maintains keepalive, survives screen-off. Data package exportable.

---

## Phase 6: User Story 4 — TAK Configuration UI (Priority: P2)

**Goal**: Compose Multiplatform settings UI for TAK module — server toggle, team/role selection, data package export, diagnostic test runner.

**Independent Test**: Navigate to TAK config, toggle server, change team/role, verify settings persist across app restart.

### Implementation for User Story 4

- [X] T060 [US4] Implement `TAKConfigItemList` Compose UI (SwitchPreference for server toggle, DropDownPreference for team/role, TitledCard for status, export button, test runner button) in `feature/settings/src/commonMain/kotlin/.../radio/component/TAKConfigItemList.kt`
- [X] T061 [P] [US4] Implement `TakPermissionUtil` expect interface in `feature/settings/src/commonMain/kotlin/.../tak/TakPermissionUtil.kt`
- [X] T062 [P] [US4] Implement `TakPermissionUtil` Android actual (ACCESS_LOCAL_NETWORK for API 37+) in `feature/settings/src/androidMain/kotlin/.../tak/TakPermissionUtil.kt`
- [X] T063 [P] [US4] Implement `TakPermissionUtil` JVM actual (no-op) in `feature/settings/src/jvmMain/kotlin/.../tak/TakPermissionUtil.kt`
- [X] T064 [P] [US4] Implement `TakPermissionUtil` iOS actual (no-op) in `feature/settings/src/iosMain/kotlin/.../tak/TakPermissionUtil.kt`

**Checkpoint**: TAK config screen functional — server toggle works, team/role persists, test runner shows per-CoT-type byte sizes.

---

## Phase 7: User Story 5 — Inbound Dual-Path Tolerance (Priority: P3)

**Goal**: V2-capable nodes decode and forward ALL inbound mesh traffic (both port 72 and port 78) to connected TAK clients.

**Independent Test**: Send a legacy TAKPacket (port 72) to a firmware 2.8.0+ node → connected ATAK client receives the PLI as valid CoT XML.

### Implementation for User Story 5

- [X] T065 [US5] Verify inbound handler in `TAKMeshIntegration` correctly decodes port 72 legacy packets and broadcasts to TAK clients (implemented as part of T049, verify independently)
- [X] T066 [US5] Verify inbound handler in `TAKMeshIntegration` correctly decompresses port 78 packets (including 0xFF uncompressed from TAK_TRACKER) and broadcasts to TAK clients

**Checkpoint**: No tactical data lost in mixed deployments. Both v1 and v2 inbound traffic decoded and forwarded correctly.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verification, compliance, and documentation tasks that span all user stories.

- [X] T067 [P] Add TAK-related string resources to `core/resources/src/commonMain/composeResources/values/strings.xml`
- [X] T068 [P] Review `TAKConfigItemList.kt` against [Meshtastic design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md) — verify M3 components, accessibility (TalkBack), touch targets
- [X] T069 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto` submodule
- [X] T070 [P] Run constitution-required verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug :core:takserver:allTests :feature:settings:allTests`
- [X] T071 [P] Verify all 12 test classes pass (89+ methods): `./gradlew :core:takserver:allTests`
- [X] T072 [P] Validate quickstart.md instructions produce successful build from clean checkout
- [X] T073 Run `gh pr checks 5434` to confirm CI passes all checks
- [X] T074 [P] [US3] Add test for offline message queue: verify FIFO eviction at 50-message cap, per-message TTL expiry after 5 minutes, and replay of queued messages on client reconnect
- [X] T075 [P] [US4] Add test for ACCESS_LOCAL_NETWORK permission denied on Android 17+: verify TAK server displays user-visible error and does not crash
- [X] T076 [P] [US3] Add test for TAKServerJvm port-conflict error: verify graceful failure with user-visible error when port 8089 is already in use
- [X] T077 [P] [US1] Add test for MAX_DECOMPRESSED_SIZE boundary: verify `TakV2Compressor` rejects payloads exceeding the decompression size limit
- [X] T078 [P] [US1] Create `TAKMeshIntegrationTest.kt` in `core/takserver/src/commonTest/` with lifecycle, inbound mesh, firmware gating, and GeoChat enrichment tests (10 tests)
- [X] T079 [P] [US3] Add `broadcastRawXml` tests to `TAKServerManagerTest.kt`: verify forward-to-TAKServer when running and no-op when not running (2 tests)
- [X] T080 [P] Restrict `TAKServerManagerImpl` visibility to `internal` in `TAKServerManager.kt` — consumers use the `TAKServerManager` interface via Koin

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 — the MVP
- **User Story 2 (Phase 4)**: Depends on Phase 2 + shares `TAKMeshIntegration` with US1
- **User Story 3 (Phase 5)**: Depends on Phase 2 — independent of US1/US2
- **User Story 4 (Phase 6)**: Depends on Phase 5 (needs TAKServerManager for toggle state)
- **User Story 5 (Phase 7)**: Depends on Phase 3 + Phase 4 (verification of dual-path handling)
- **Polish (Phase 8)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: After Phase 2 — no dependencies on other stories
- **US2 (P1)**: After Phase 2 — shares `TAKMeshIntegration.kt` with US1 (co-implemented)
- **US3 (P2)**: After Phase 2 — independent (server infrastructure)
- **US4 (P2)**: After US3 (needs server lifecycle for toggle state)
- **US5 (P3)**: After US1 + US2 (verification of combined behavior)

### Within Each User Story

- Tests written alongside implementation (retroactive — both exist in PR)
- Models/interfaces → actual implementations → integration → verification
- Platform actuals can be implemented in parallel (jvmAndroidMain, androidMain, jvmMain, iosMain)

### Parallel Opportunities

- All Phase 1 tasks T003-T006 can run in parallel
- All Phase 2 tasks T008-T024 can run in parallel (independent files)
- All US1 test tasks T025-T031 can run in parallel
- Platform actuals within US1 (T033/T034, T040/T041, T042/T043/T044) can run in parallel
- US3 server tasks T052/T053 can run in parallel (JVM vs iOS)
- All US4 permission actuals T062/T063/T064 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all tests for US1 together:
Task: "CoTXmlParserTest in commonTest"
Task: "CoTXmlTest in commonTest"
Task: "CoTConversionTest in commonTest"
Task: "CoTDetailStripperTest in commonTest"
Task: "TAKPacketV2RawDetailTest in commonTest"
Task: "XmlUtilsTest in commonTest"
Task: "TAKDefaultsTest in commonTest"

# Launch all platform actuals together:
Task: "TakV2Compressor jvmAndroidMain actual"
Task: "TakV2Compressor iosMain stub"
Task: "ZipArchiver jvmAndroidMain actual"
Task: "ZipArchiver iosMain stub"
Task: "AtakFileWriter androidMain actual"
Task: "AtakFileWriter jvmMain actual"
Task: "AtakFileWriter iosMain stub"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Module setup + dependencies
2. Complete Phase 2: Shared models, constants, utilities
3. Complete Phase 3: US1 — Rich tactical data over mesh (v2 path)
4. Complete Phase 4: US2 — Legacy fallback (v1 path)
5. **STOP and VALIDATE**: Run all tests, verify PLI/marker round-trip on actual hardware

### Incremental Delivery

1. Setup + Foundational → Module compilable
2. US1 + US2 → Full bidirectional mesh ↔ TAK bridge (MVP!)
3. US3 → Reliable TAK server lifecycle
4. US4 → User configuration UI
5. US5 → Dual-path tolerance verification
6. Polish → CI green, design compliance confirmed

### Retroactive Verification (PR #5434)

Since this implementation already exists, use this task list as:
1. **Verification checklist** — confirm each task's output exists in the PR diff
2. **Code review guide** — trace requirements → implementation → tests
3. **Regression detection** — if future changes break a task's output, identify which user story is affected

---

## Notes

- [P] tasks = different files, no dependencies between them
- [Story] label maps task to specific user story for traceability
- This is a **retroactive** task list — PR #5434 implements all tasks
- Verification: `./gradlew spotlessApply spotlessCheck detekt assembleDebug :core:takserver:allTests :feature:settings:allTests`
- Total implementation: 99 files changed, +4698 lines, 1 new KMP module (`core:takserver`)
