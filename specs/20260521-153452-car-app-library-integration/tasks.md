# Tasks: Car App Library Integration

**Input**: Design documents from `/specs/20260521-153452-car-app-library-integration/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Not explicitly requested in spec. Test tasks omitted per template rules.

**Verification**: Constitution-required validation (spotlessCheck, detekt, compile/test) included in final phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Create the `feature/car` module structure, Gradle configuration, and version catalog entries

- [x] T001 Add Car App Library version catalog entries in gradle/libs.versions.toml (car-app version, 4 library entries)
- [x] T002 Add `include(":feature:car")` to settings.gradle.kts
- [x] T003 Create module build file at feature/car/build.gradle.kts with android-library, flavors, koin plugins, and all dependencies per contracts/manifest-declarations.md
- [x] T004 [P] Add `"googleImplementation"(projects.feature.car)` dependency in androidApp/build.gradle.kts
- [x] T005 [P] Create AndroidManifest.xml at feature/car/src/main/AndroidManifest.xml with CarAppService, MESSAGING category, and minCarApiLevel 8 meta-data
- [x] T006 [P] Create AAOS descriptor at feature/car/src/main/res/xml/automotive_app_desc.xml
- [x] T007 [P] Create car-specific strings file at feature/car/src/main/res/values/strings.xml with initial string resources

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that ALL user stories depend on — service entry point, session lifecycle, DI, utilities

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T008 Create Koin DI module at feature/car/src/main/kotlin/org/meshtastic/feature/car/di/FeatureCarModule.kt declaring MeshtasticCarSession (factory), EmergencyHandler (singleton), CrashlyticsCarTagger (singleton)
- [x] T009 Register FeatureCarModule in androidApp google flavor Koin configuration (androidApp/src/google/ Koin app module graph)
- [x] T010 [P] Create CrashlyticsCarTagger utility at feature/car/src/main/kotlin/org/meshtastic/feature/car/util/CrashlyticsCarTagger.kt implementing car_session custom key set/clear
- [x] T011 [P] Create TemplateBuilders helper extensions at feature/car/src/main/kotlin/org/meshtastic/feature/car/util/TemplateBuilders.kt with reusable CAL template construction helpers
- [x] T012 Create MeshtasticCarAppService at feature/car/src/main/kotlin/org/meshtastic/feature/car/service/MeshtasticCarAppService.kt extending CarAppService, creating sessions via Koin
- [x] T013 Create MeshtasticCarSession at feature/car/src/main/kotlin/org/meshtastic/feature/car/service/MeshtasticCarSession.kt with onCreateScreen (returns HomeScreen), onNewIntent, onCarConfigurationChanged, Crashlytics tagging, 300ms invalidation debouncing
- [x] T014 Create presentation state models (CarSessionState, ConnectionStatus, MessagingUiState, ChannelUi, ConversationUi, NodeDashboardUiState, NodeUi, SignalQuality, TopologyHeader, EmergencyAlert) at feature/car/src/main/kotlin/org/meshtastic/feature/car/model/CarUiModels.kt
- [x] T015 Create HomeScreen (TabTemplate with Messages/Nodes tabs; Map tab placeholder deferred) at feature/car/src/main/kotlin/org/meshtastic/feature/car/screens/HomeScreen.kt

**Checkpoint**: Foundation ready — CarAppService binds, session creates, HomeScreen renders tabs. User story implementation can now begin in parallel.

---

## Phase 3: User Story 1 — Read and Reply to Mesh Messages While Driving (Priority: P1) 🎯 MVP

**Goal**: Drivers can view incoming mesh messages grouped by channel and reply via voice or quick-reply templates

**Independent Test**: Send a message from a second Meshtastic device → appears on car display within 3s → dictate voice reply → arrives on sender's device

### Implementation for User Story 1

- [x] T016 [P] [US1] Create MessagingScreen at feature/car/src/main/kotlin/org/meshtastic/feature/car/screens/MessagingScreen.kt with ListTemplate, channel Chips header, Section Headers grouping conversations, ConversationItem list (max 10), 300ms debounced invalidation, favorites/recent DM grouping
- [x] T017 [P] [US1] Create ConversationScreen at feature/car/src/main/kotlin/org/meshtastic/feature/car/screens/ConversationScreen.kt with MessageTemplate showing messages (max 5 per conversation), voice reply action via CAL built-in ConversationItem voice input, quick-reply action list from QuickChatActionRepository, read-aloud TTS action
- [x] T018 [US1] Reuse `FuzzyNameResolver` from `core/data/commonMain` (shared with AppFunctions feature) for voice-initiated DM node name matching — inject via Koin from existing `core/data` module. If AppFunctions branch not yet merged, temporarily duplicate LCS algorithm in feature/car/src/main/kotlin/org/meshtastic/feature/car/util/FuzzyNodeNameResolver.kt with TODO to consolidate post-merge
- [x] T019 [US1] Implement message filtering logic in MessagingScreen — exclude emoji-only and admin messages from display (FR-017), enforce 237-byte outgoing limit with user feedback (FR-018)
- [x] T020 [US1] Implement session-start batch loading of up to 50 unread messages in MeshtasticCarSession (FR-021) and post MessagingStyle notifications for read-back support
- [x] T021 [US1] Implement notification-based messaging (NotificationCompat.MessagingStyle with reply and mark-as-read Actions) at feature/car/src/main/kotlin/org/meshtastic/feature/car/service/CarNotificationManager.kt (FR-022)

**Checkpoint**: Messaging fully functional — driver can see messages, switch channels, voice reply, use quick-reply templates, and receive MessagingStyle notifications

---

## Phase 4: User Story 2 — Emergency Alert Reception (Priority: P1)

**Goal**: Emergency broadcasts immediately surface as prominent banners with audio alerts regardless of active screen

**Independent Test**: Trigger emergency broadcast from test device → banner appears within 1s → audio alert plays → tap shows full details in Spotlight Section

### Implementation for User Story 2

- [x] T022 [P] [US2] Create EmergencyHandler at feature/car/src/main/kotlin/org/meshtastic/feature/car/alerts/EmergencyHandler.kt observing PacketRepository flow for emergency-priority packets, triggering Banner via AppManager.showAlert(), managing active emergency list, stacking multiple banners chronologically
- [x] T023 [US2] Implement emergency audio alert playback in EmergencyHandler using NotificationManager on USAGE_NOTIFICATION audio channel (NFR-008), not media channel
- [x] T024 [US2] Integrate Spotlight Section in MessagingScreen for active emergencies — display EmergencyAlert items at top of messaging list when activeEmergencies is non-empty (FR-006). **Depends on T016 (MessagingScreen must exist first)**
- [x] T025 [US2] Wire EmergencyHandler into MeshtasticCarSession lifecycle — start collecting on onCreateScreen, stop on session destroy

**Checkpoint**: Emergency alerts fully operational — banners overlay any screen within 1s, audio plays, Spotlight Section shows in messaging view

---

## Phase 5: User Story 3 — Monitor Node Network Status (Priority: P2)

**Goal**: Driver views all mesh nodes as a dense Condensed Items grid with signal/battery metrics and topology header

**Independent Test**: Have 3+ nodes in range → open node dashboard → all nodes displayed with correct signal/battery → tap node → detail view shows full info

### Implementation for User Story 3

- [x] T026 [P] [US3] Create NodeDashboardScreen at feature/car/src/main/kotlin/org/meshtastic/feature/car/screens/NodeDashboardScreen.kt with ListTemplate, Expanded Header Layout (mesh topology summary: online/total), Condensed Items for each node (name, signal quality, battery), online-first sorting with offline dimmed at bottom
- [x] T027 [P] [US3] Create NodeDetailScreen at feature/car/src/main/kotlin/org/meshtastic/feature/car/screens/NodeDetailScreen.kt with PaneTemplate showing last heard, distance, hardware model, battery, SNR, and "Message" action to push ConversationScreen for DM

**Checkpoint**: Node dashboard shows 6+ nodes without scrolling via Condensed Items, detail drill-down works, DM action connects to messaging

---

## Phase 6: User Story 4 — Switch Between Channels (Priority: P2)

**Goal**: Single-tap channel switching via Chips with unread badges at the top of the messaging screen

**Independent Test**: Configure 3+ channels → messaging screen shows channel chips → tap chip → message list updates within 1s → unread badge visible on channels with new messages

### Implementation for User Story 4

- [x] T028 [US4] Implement channel Chip actions with unread badge indicators in MessagingScreen header — single-tap switches selectedChannelIndex, triggers message list re-filter within 1s (FR-008, FR-016)

**Checkpoint**: Channel chips render with unread counts, tapping switches view to that channel's conversations immediately

---

## Phase 7: User Story 5 — View Node Locations on Map (DEFERRED)

> **⚠️ DEFERRED:** Map implementation deferred pending NAVIGATION vs POI category decision. The choice between PlaceListMapTemplate (POI, 6-item cap, no nav conflicts) and MapWithContentTemplate (NAVIGATION, full-featured, exclusive with Google Maps) requires further research and discussion. See spec User Story 5 for open questions.

~~- [ ] T029 [US5] Create MapScreen~~

**Checkpoint**: SKIPPED — revisit after map strategy decision

---

## Phase 8: User Story 6 — Persistent Mesh Status at a Glance (Priority: P3)

**Goal**: Minimized Control Panel visible across all screens showing radio status, node count, last message time

**Independent Test**: Navigate between all screens → mini-panel always visible → shows correct node count → disconnect radio → panel shows "Disconnected"

### Implementation for User Story 6

- [x] T030 [US6] Create MeshStatusPanel at feature/car/src/main/kotlin/org/meshtastic/feature/car/panels/MeshStatusPanel.kt implementing Minimized Control Panel — connectionStatusIcon, "{N} nodes online" title, "Last msg: {timeAgo}" subtitle, onClickListener expanding to full detail (mesh name, own battery, firmware version)
- [x] T031 [US6] Register MeshStatusPanel in MeshtasticCarSession lifecycle — attach to session on creation, observe BleConnectionState + NodeRepository for live updates, show "Disconnected" with warning icon on radio disconnect (FR-010, FR-011)

**Checkpoint**: Persistent mini-panel visible across all screens, updates in real-time, expands on tap

---

## Phase 9: User Story 7 — In-Context Voice Input for Actions (Priority: P3)

**Goal**: Voice reply is the default composition method, TTS reads messages aloud, FuzzyNodeNameResolver handles voice-initiated DMs

**Independent Test**: Tap reply → dictate → message sent → tap "read aloud" → TTS reads message with sender name

### Implementation for User Story 7

- [x] T032 [US7] Implement TTS read-aloud action in ConversationScreen using Android built-in TTS engine — reads sender name + message content on tap of "Read Aloud" action
- [x] T033 [US7] Wire FuzzyNodeNameResolver into node detail "Message" action flow — when initiating DM from NodeDashboard, voice input is default composition method with resolved node context

**Checkpoint**: Voice reply works end-to-end, TTS reads messages clearly, node-initiated DMs use voice by default

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, degraded states, compliance, and verification

- [x] T034 [P] Implement BLE disconnection Banner + graceful degradation to cached read-only data across all screens (FR-011, FR-015)
- [x] T035 [P] Implement empty/error states: no channels configured → onboarding PaneTemplate, no nodes → "No nodes heard", no positions → "No positions reported" (per error contracts)
- [x] T036 [P] Add ProGuard/R8 keep rule for MeshtasticCarAppService in feature/car/proguard-rules.pro
- [x] T037 [P] Confirm no logs, telemetry, or config changes expose PII, location data, secrets, or modify `core/proto`
- [x] T038 [P] Review all screens against automotive HMI distraction guidelines — verify ≤ 2 taps for all primary actions (NFR-001)
- [x] T039 Run constitution-required verification: `./gradlew spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:testGoogleDebugUnitTest`
- [x] T040 Validate quickstart.md developer workflow documentation is accurate for the implemented module

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories
- **Phase 3 (US1 - Messaging)**: Depends on Phase 2 — MVP target
- **Phase 4 (US2 - Emergency)**: Depends on Phase 2; integrates with MessagingScreen (Phase 3 T016)
- **Phase 5 (US3 - Nodes)**: Depends on Phase 2 — independent of messaging
- **Phase 6 (US4 - Channels)**: Depends on Phase 3 (modifies MessagingScreen)
- **Phase 7 (US5 - Map)**: **DEFERRED** — pending NAVIGATION vs POI category decision
- **Phase 8 (US6 - Status Panel)**: Depends on Phase 2 — independent
- **Phase 9 (US7 - Voice)**: Depends on Phase 3 (ConversationScreen T017, FuzzyNodeNameResolver T018)
- **Phase 10 (Polish)**: Depends on all user story phases

### User Story Dependencies

- **US1 (Messaging, P1)**: Can start after Phase 2 — no other story dependencies
- **US2 (Emergency, P1)**: Can start after Phase 2 — integrates with US1's MessagingScreen (T016) for Spotlight Section (T024)
- **US3 (Nodes, P2)**: Can start after Phase 2 — fully independent
- **US4 (Channels, P2)**: Depends on US1 (extends MessagingScreen)
- **US5 (Map, DEFERRED)**: Pending NAVIGATION vs POI category decision — requires further research
- **US6 (Status Panel, P3)**: Can start after Phase 2 — fully independent
- **US7 (Voice, P3)**: Depends on US1 (extends ConversationScreen)

### Within Each User Story

- State models → Screen implementation → Integration logic
- Screens before cross-screen wiring
- Core implementation before refinement

### Parallel Opportunities

- **Phase 1**: T004, T005, T006, T007 can all run in parallel
- **Phase 2**: T010, T011 in parallel; T014 parallel with T010/T011
- **After Phase 2**: US1, US3, and US6 can start simultaneously (independent)
- **Within US1**: T016 and T017 in parallel (different files)
- **Within US2**: T022 independent of other stories
- **Within US3**: T026 and T027 in parallel (different files)
- **Phase 10**: T034, T035, T036, T037, T038 all in parallel

---

## Parallel Example: After Foundational Phase

```bash
# Three stories can start simultaneously:
# Developer A: US1 (Messaging)
Task: T016 "Create MessagingScreen"
Task: T017 "Create ConversationScreen"

# Developer B: US3 (Nodes)
Task: T026 "Create NodeDashboardScreen"
Task: T027 "Create NodeDetailScreen"

# Developer C: US6 (Status Panel)
Task: T030 "Create MeshStatusPanel"
Task: T031 "Register panel in session"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T007)
2. Complete Phase 2: Foundational (T008–T015)
3. Complete Phase 3: User Story 1 — Messaging (T016–T021)
4. **STOP and VALIDATE**: Test messaging end-to-end with DHU
5. Deploy to internal testing track if ready

### Incremental Delivery

1. Setup + Foundational → Module compiles and binds to Android Auto
2. Add US1 (Messaging) → Core value delivered (MVP!)
3. Add US2 (Emergency) → Safety-critical alerts operational
4. Add US3 (Nodes) → Node awareness complete
5. Add US4 (Channels) → Multi-channel workflows enabled
6. Add US6 + US7 (Panel + Voice) → Polish and hands-free refinement
7. Each increment is independently testable with the Desktop Head Unit (DHU)

### Parallel Team Strategy

With multiple developers after Phase 2:
- Developer A: US1 (Messaging) → US4 (Channels) → US7 (Voice)
- Developer B: US3 (Nodes) + US6 (Status Panel)
- Developer C: US2 (Emergency)

---

## Notes

- All screens use `invalidate()` for refresh (never recreate Screen objects) per NFR-010
- 300ms debounce on all invalidation triggers per NFR-010
- CAL host enforces distraction guidelines — app provides templates only
- Existing `core/` modules consumed read-only via Koin DI — no API changes
- Google flavor only — F-Droid builds unaffected
- Car API Level 8 minimum — older hosts gracefully hide the app
