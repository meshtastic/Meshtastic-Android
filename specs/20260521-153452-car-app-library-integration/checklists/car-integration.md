# Car App Library Integration Checklist: Car App Library Integration

**Purpose**: Validate requirements quality, completeness, and clarity for the Car App Library 1.9.0-alpha01 integration — covering automotive safety, component usage, connectivity, distribution, and testability
**Created**: 2026-05-21
**Feature**: [spec.md](../spec.md)

## Requirement Completeness

- [ ] CHK001 — Are CarAppService lifecycle requirements specified (onCreateSession, onDestroy, multi-session behavior)? [Completeness, Gap]
- [ ] CHK002 — Are requirements defined for all 7 new 1.9.0-alpha01 components (Spotlight, Condensed Items, Chips, Minimized Control Panel, Banners, Section Headers, Expanded Headers)? [Completeness, Spec §FR-002–FR-013]
- [ ] CHK003 — Are Screen navigation graph requirements documented (which screens link to which, back-stack behavior)? [Completeness, Gap]
- [ ] CHK004 — Are ConversationItem requirements specified (message grouping, read/unread state, sender avatar rendering)? [Completeness, Spec §FR-002]
- [ ] CHK005 — Are TTS readback requirements defined with language, speed, and fallback behavior? [Completeness, Spec §US-7]
- [ ] CHK006 — Are quick-reply template storage and configuration requirements specified? [Completeness, Spec §FR-004]
- [ ] CHK007 — Are Koin module registration requirements documented for the `feature/car` DI graph? [Completeness, Gap]
- [ ] CHK008 — Are requirements defined for the google-flavor-only build gate (how other flavors exclude the car module)? [Completeness, Gap]
- [ ] CHK009 — Are requirements specified for CarAppService `onNewIntent` handling and deep-link entry points? [Completeness, Gap]
- [ ] CHK010 — Are node detail view content requirements exhaustively enumerated (last heard, distance, hardware model, firmware version, hops)? [Completeness, Spec §FR-012]

## Requirement Clarity

- [ ] CHK011 — Is "within 3 seconds" latency (FR-002/NFR-002) measured from radio receipt, BLE delivery, or repository emission? [Clarity, Spec §NFR-002]
- [ ] CHK012 — Is "high-priority banner" (FR-005) defined with specific CAL Banner priority level and duration? [Clarity, Spec §FR-005]
- [ ] CHK013 — Is "signal quality indicator" quantified — specific icon set, numeric dBm ranges, or named levels (excellent/good/fair/poor)? [Clarity, Spec §FR-007]
- [ ] CHK014 — Is "< 10% battery drain" measured under defined conditions (screen brightness, BLE activity, message frequency)? [Clarity, Spec §NFR-003]
- [ ] CHK015 — Is "visually distinguished" for offline nodes defined with specific styling (opacity, icon, sort order)? [Clarity, Spec §US-3 Scenario 3]
- [ ] CHK016 — Is "distinct color treatment" for emergency banners specified with concrete color values or semantic tokens? [Clarity, Spec §US-2 Scenario 1]
- [ ] CHK017 — Is "6+ nodes visible simultaneously without scrolling" dependent on a specific screen density or display size? [Clarity, Spec §SC-003]
- [ ] CHK018 — Is "configurable template responses" clear on who configures them, where they're stored, and defaults? [Clarity, Spec §FR-004]
- [ ] CHK019 — Is Car API Level 8 minimum clearly justified — which specific 1.9.0 APIs require it? [Clarity, Spec §NFR-004]

## Requirement Consistency

- [ ] CHK020 — Do FR-009 (PlaceListMapTemplate under POI) and Non-Goals (NAVIGATION deferred to v2) consistently align with map update latency requirement SC-009 (< 5s)? [Consistency, Spec §FR-009, §SC-009]
- [ ] CHK021 — Are voice input requirements consistent between US-1 (reply), US-7 (in-context), and FR-003 (primary method)? [Consistency]
- [ ] CHK022 — Does "no parked-mode differentiation" (Clarifications) conflict with any acceptance scenario implying driving-only behavior? [Consistency, Spec §Edge Cases]
- [ ] CHK023 — Are emergency handling requirements consistent between FR-005 (banner), FR-006 (spotlight), and US-2 (all scenarios)? [Consistency]
- [ ] CHK024 — Is the shared BLE connection assumption consistent with CarAppService lifecycle — what happens when phone app is force-stopped? [Consistency, Spec §Assumptions]
- [ ] CHK025 — Are "within 1 second" requirements (FR-005 emergency, SC-006 channel switch) measured consistently with NFR-002's 3-second messaging latency? [Consistency]

## Acceptance Criteria Quality

- [ ] CHK026 — Is SC-008 ("95% voice replies succeed") measurable without defining what "success" means (sent vs. accurately transcribed vs. delivered)? [Measurability, Spec §SC-008]
- [ ] CHK027 — Is SC-010 ("zero crashes in 2-hour session") sufficient as a release gate — what about ANRs, OOM, or session drops? [Measurability, Spec §SC-010]
- [ ] CHK028 — Is SC-007 ("passes Android Auto App Quality review") measurable before actual store submission? [Measurability, Spec §SC-007]
- [ ] CHK029 — Is SC-001 ("15 seconds total interaction time") measured from notification appearance or screen wake? [Measurability, Spec §SC-001]
- [ ] CHK030 — Are acceptance scenarios for US-5 (map) testable on DHU given DHU's limited map rendering capabilities? [Measurability, Spec §US-5]

## Scenario Coverage

- [ ] CHK031 — Are requirements defined for initial onboarding flow when no radio is paired? [Coverage, Gap]
- [ ] CHK032 — Are requirements specified for behavior when Android Auto host disconnects mid-session (cable pull, Bluetooth drop)? [Coverage, Gap]
- [ ] CHK033 — Are requirements defined for multi-device scenario (phone switches between two radios)? [Coverage, Gap]
- [ ] CHK034 — Are requirements specified for app behavior during phone call interruption on the head unit? [Coverage, Gap]
- [ ] CHK035 — Are requirements defined for Screen refresh/invalidation cadence (how often templates re-render)? [Coverage, Gap]
- [ ] CHK036 — Are data freshness requirements defined for cached messages shown during disconnection? [Coverage, Spec §FR-015]
- [ ] CHK037 — Are requirements specified for ConversationItem threading — flat list or grouped by conversation? [Coverage, Spec §FR-002]

## Edge Case Coverage

- [ ] CHK038 — Is behavior defined when PlaceListMapTemplate's item limit is reached (max 6 items per CAL docs)? [Edge Case, Spec §FR-009]
- [ ] CHK039 — Is behavior defined when a channel has zero messages (empty state for messaging screen per channel)? [Edge Case, Gap]
- [ ] CHK040 — Are requirements defined for handling very long node names that exceed Condensed Item text bounds? [Edge Case, Spec §FR-007]
- [ ] CHK041 — Is behavior defined when voice recognition returns empty/null result or times out? [Edge Case, Spec §FR-003]
- [ ] CHK042 — Is behavior defined for rapid consecutive emergency alerts from multiple nodes? [Edge Case, Spec §Edge Cases]
- [ ] CHK043 — Are requirements defined for handling GPS-less nodes on the map screen (nodes without position data)? [Edge Case, Spec §FR-009]
- [ ] CHK044 — Is behavior defined when the message being composed via voice exceeds mesh packet size limit (228 bytes)? [Edge Case, Gap]
- [ ] CHK045 — Is behavior defined when Minimized Control Panel data sources become stale (BLE connected but no mesh traffic)? [Edge Case, Spec §FR-010]

## Non-Functional Requirements

- [ ] CHK046 — Are memory usage requirements specified for the car module (AAOS devices may have constrained RAM)? [NFR, Gap]
- [ ] CHK047 — Are cold-start performance requirements defined for CarAppService (time from launch to first screen rendered)? [NFR, Gap]
- [ ] CHK048 — Are requirements specified for Crashlytics `car_session` key format, lifecycle (set/clear), and what constitutes a "session"? [NFR, Spec §NFR-009]
- [ ] CHK049 — Are ProGuard/R8 keep rules requirements documented for the car module (CAL uses reflection for template inflation)? [NFR, Gap]
- [ ] CHK050 — Are requirements defined for handling Android Auto's 10-second ANR threshold on the main thread? [NFR, Gap]
- [ ] CHK051 — Is backward-compatibility behavior specified for hosts below Car API Level 8 (graceful absence vs. crash vs. fallback)? [NFR, Spec §NFR-004, §Assumptions]
- [ ] CHK052 — Are requirements specified for process priority / foreground service behavior to keep BLE alive when phone app is backgrounded? [NFR, Gap]

## Dependencies & Assumptions

- [ ] CHK053 — Is the alpha stability risk (1.9.0-alpha01) quantified with a fallback plan if APIs change before stable release? [Assumption, Spec §Assumptions]
- [ ] CHK054 — Is the assumption "users configure channels on phone first" validated — what if a user only has AAOS with no phone? [Assumption, Spec §Assumptions]
- [ ] CHK055 — Are Play Store review requirements for MESSAGING category documented (conversation API compliance, notification delegation)? [Dependency, Gap]
- [ ] CHK056 — Is the relationship with AppFunctions feature clearly bounded — are there shared components or only independent parallel features? [Dependency, Spec §Clarifications]
- [ ] CHK057 — Are DHU and Automotive Emulator API 35-ext15 testing environment requirements documented as a verification prerequisite? [Dependency, Gap]
- [ ] CHK058 — Is the Koin Application-scoped BleConnectionManager's threading model documented (which dispatcher, coroutine scope)? [Assumption, Spec §Architecture]

## Distribution & Build Integration

- [ ] CHK059 — Are Play Store listing requirements specified for the car app (screenshots, description, category metadata)? [Completeness, Gap]
- [ ] CHK060 — Are internal/closed testing track progression criteria defined (when to promote from internal → closed → open → production)? [Completeness, Gap]
- [ ] CHK061 — Is the manifest merger strategy documented for adding `<meta-data>` and `<service>` entries only in the google flavor? [Completeness, Gap]
- [ ] CHK062 — Are automotive-specific permission requirements documented (e.g., `androidx.car.app.ACCESS_SURFACE`)? [Completeness, Gap]

## Cross-Artifact Consistency

- [ ] CHK063 — Do architecture component names in spec match planned module/package structure in plan.md? [Consistency]
- [ ] CHK064 — Are all 7 user stories reflected as distinct implementation tasks in tasks.md? [Consistency]
- [ ] CHK065 — Do NFR metrics (latency, battery, build time) have corresponding verification methods defined? [Traceability]

## Notes

- Check items off as they are resolved (requirement clarified, gap filled, or explicitly marked N/A)
- Items marked [Gap] indicate missing requirements that should be added to spec.md
- Items marked [Assumption] should be validated or converted to explicit requirements
- 80%+ items include traceability references to spec sections or gap markers
