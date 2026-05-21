# Feature Specification: Car App Library Integration

**Feature Branch**: `feature/20260521-153452-car-app-library-integration`
**Created**: 2026-05-21
**Status**: Draft
**Input**: Integrate Android Car App Library 1.9.0-alpha01 as a fully-featured, first-class car app
**Cross-Platform Spec**: N/A — platform-specific only (Android Auto / AAOS exclusive; CAL has no cross-platform equivalent)

## Summary

Integrate the Android Car App Library 1.9.0-alpha01 into Meshtastic-Android to deliver a fully-featured, first-class automotive experience for Android Auto and Android Automotive OS. The integration creates a distraction-optimized, safety-first mesh radio interface for vehicles — enabling drivers to monitor mesh network status, read and reply to messages via voice, view node locations on maps, and receive emergency alerts with immediate prominence. A new `feature/car` module houses the Android-only CAL layer while reusing all shared business logic from existing core and feature modules.

## Clarifications

### Session 2026-05-21

- Q: How should voice commands be implemented — CAL built-in voice input, full Assistant App Actions, or both? → A: CAL built-in voice input only (tap reply → dictate → send). System-level "Hey Google" commands are handled separately by the AppFunctions feature (`specs/20260521-091500-app-functions/`), which exposes `sendMessage`, `getMeshStatus`, `listNodes`, `getRecentMessages`, and `getNodePosition` to Android system AI (Gemini) automatically — including on car displays.
- Q: Should the app declare NAVIGATION category for MapWithContentTemplate, or use PlaceListMapTemplate under POI? → A: **DECISION DEFERRED** — originally selected POI/PlaceListMapTemplate but reopened for further research. See US-5 deferral note for open questions on NAVIGATION vs POI implications.
- Q: Should the CarAppService maintain an independent BLE connection or share the phone app's existing connection? → A: Shared connection — single Application-scoped BleConnectionManager instance via Koin. CarAppService keeps the process alive via Android Auto host; BLE connection persists at the Service/Application level, not Activity level.
- Q: What observability approach should the car module use? → A: Reuse existing Crashlytics with `car_session` custom key tagging for car-specific filtering. No new observability infrastructure; tag existing analytics paths.
- Q: Should the car app unlock additional features when the vehicle is parked? → A: No parked-mode differentiation. Templated messaging apps provide a uniform experience regardless of driving state. Voice reply is built into ConversationItem. The Android Auto host enforces its own driving restrictions; the app just provides templates.

## Goals

1. **Complete automotive mesh experience** — Deliver all seven core screens (messaging, node dashboard, channel management, emergency alerts, map, quick actions, mesh status panel) as a single release
2. **Safety-first interaction model** — Every interaction completes in ≤ 2 taps or via voice, meeting automotive distraction guidelines
3. **Leverage 1.9.0-alpha01 components** — Showcase Spotlight Sections, Condensed Items, Chips, Minimized Control Panel, Banners, Section Headers, and Expanded Headers for a modern car UI
4. **Zero disruption to existing app** — The new `feature/car` module integrates via dependency injection without modifying existing module APIs or behavior
5. **Voice-first messaging** — Message composition defaults to voice input, with quick-reply templates as fallback for hands-free operation

## Non-Goals

- Firmware updates via the car interface (too complex and risky while driving)
- Full settings UI in-car (a minimal parked-only subset may be considered in future)
- Desktop or iOS car support (this is Android Auto / AAOS specific)
- Video playback or media/audio streaming features
- Compose UI interop (CAL uses its own template-based rendering system)
- Google Assistant App Actions / voice command routing (handled by separate AppFunctions feature)
- NAVIGATION category declaration / live map tracking (deferred to v2; v1 uses POI with PlaceListMapTemplate)
- Phone app UI changes (car UI is additive only)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read and Reply to Mesh Messages While Driving (Priority: P1)

A driver receives mesh messages from their group while on the road. They glance at the head unit to see new messages and use voice to compose a reply, keeping hands on the wheel and eyes on the road.

**Why this priority**: Messaging is the primary use case for Meshtastic. Enabling safe in-car messaging addresses the #1 reason users would want car integration.

**Independent Test**: Can be fully tested by sending a message from a second Meshtastic device, verifying it appears on the car display, and dictating a voice reply that arrives on the sender's device.

**Acceptance Scenarios**:

1. **Given** the car app is connected to a Meshtastic radio and a new message arrives, **When** the driver views the messaging screen, **Then** the new message appears within 3 seconds with sender name, timestamp, and message content visible at a glance
2. **Given** the driver is viewing a conversation, **When** they tap the reply action, **Then** the system presents voice input as the default composition method
3. **Given** the driver has initiated voice reply, **When** they speak their message and confirm, **Then** the message is sent to the correct channel/DM within 2 seconds
4. **Given** the driver prefers not to use voice, **When** they select quick-reply, **Then** a list of configurable template responses (e.g., "On my way", "Copy that", "10 minutes out") is presented for one-tap selection
5. **Given** the mesh radio is disconnected, **When** the driver opens messaging, **Then** a banner clearly indicates offline status and cached messages remain visible as read-only

---

### User Story 2 - Emergency Alert Reception (Priority: P1)

A driver receives an emergency alert broadcast from a mesh node (SOS, hazard warning, etc.). The alert demands immediate attention with distinct visual and audio treatment, regardless of which screen is currently active.

**Why this priority**: Emergency alerts are life-safety critical. Failure to surface them prominently could have real-world safety consequences.

**Independent Test**: Can be tested by triggering an emergency broadcast from a test device and verifying the car app interrupts current activity with a banner alert.

**Acceptance Scenarios**:

1. **Given** any screen is active, **When** an emergency message is received, **Then** a high-priority banner appears immediately (within 1 second) with emergency iconography and distinct color treatment
2. **Given** an emergency banner is displayed, **When** the driver taps it, **Then** full emergency details are shown including sender identity, location (if available), and timestamp
3. **Given** an emergency alert has been received, **When** the driver navigates to the messaging screen, **Then** the emergency message appears in a Spotlight Section at the top, visually distinguished from normal messages
4. **Given** emergency audio alerts are enabled, **When** an emergency message arrives, **Then** an audible notification tone plays through the car's audio system

---

### User Story 3 - Monitor Node Network Status (Priority: P2)

A driver glances at the head unit to check how many mesh nodes are in range, their signal strength, and battery levels — useful for caravan/convoy scenarios or checking if they're still in range of base camp.

**Why this priority**: Node awareness is the second-most-common Meshtastic use case and provides critical situational awareness for mobile users.

**Independent Test**: Can be tested by having 3+ nodes in range and verifying the dashboard displays each with correct signal/battery metrics.

**Acceptance Scenarios**:

1. **Given** the car app is connected with multiple nodes in range, **When** the driver opens the node dashboard, **Then** all known nodes are displayed as Condensed Items showing node name, signal quality indicator, and battery level
2. **Given** 6+ nodes are in range, **When** viewing the dashboard, **Then** at least 6 nodes are visible simultaneously without scrolling (leveraging Condensed Items)
3. **Given** a node goes offline, **When** the dashboard refreshes, **Then** the offline node is visually distinguished (dimmed or marked) and sorted to the bottom
4. **Given** the node list is displayed, **When** the driver taps a node, **Then** a detail view shows last heard time, distance (if location known), hardware model, and direct message option

---

### User Story 4 - Switch Between Channels (Priority: P2)

A driver participating in multiple mesh channels (e.g., "Convoy", "Emergency", "General") quickly switches between them to view messages from different groups.

**Why this priority**: Channel management is essential for users in organized groups and must be achievable without complex navigation.

**Independent Test**: Can be tested by configuring 3+ channels and verifying single-tap channel switching via chips.

**Acceptance Scenarios**:

1. **Given** the device has multiple channels configured, **When** the messaging screen loads, **Then** channel chips are displayed at the top allowing single-tap switching
2. **Given** channel chips are visible, **When** the driver taps a different channel chip, **Then** the message list updates to show that channel's messages within 1 second
3. **Given** a channel has unread messages, **When** viewing the chip bar, **Then** that channel's chip displays an unread indicator (badge or visual emphasis)

---

### User Story 5 - View Node Locations on Map (Priority: DEFERRED)

> **⚠️ DEFERRED:** Map implementation is deferred pending further research and discussion on whether to pursue POI category (PlaceListMapTemplate, limited but simpler) or NAVIGATION category (MapWithContentTemplate, full-featured but triggers stricter Play Store review and conflicts with active nav apps). This decision has significant architectural and distribution implications that warrant dedicated analysis.

A driver in a convoy scenario views the locations of all mesh nodes on a map to understand relative positions and navigate toward or away from group members.

**Why deferred**: The choice between POI (static pins, 6-item cap, no routing conflicts) and NAVIGATION (live tracking, full map control, but exclusive with Google Maps/Waze) fundamentally shapes the UX and distribution strategy. More research needed on:
- Google Maps SDK availability for AAOS (announced I/O 2026, timeline unclear)
- NAVIGATION category Play Store review requirements and timeline
- Whether Meshtastic's convoy use case justifies NAVIGATION exclusivity
- User expectations (passive awareness vs. active routing toward nodes)

**Acceptance Scenarios** (to be finalized after map strategy decision):

1. **Given** nodes are reporting GPS positions, **When** the driver opens the map screen, **Then** node locations are displayed with correct positions
2. **Given** the map is displayed, **When** the driver selects a node, **Then** a detail view shows node name, distance, last update time, and option to send a direct message
3. **Given** the driver's own position is available, **When** viewing the map, **Then** their position is shown distinctly from other nodes
4. **Given** a node's position updates, **When** the map is visible, **Then** the display updates within 5 seconds

---

### User Story 6 - Persistent Mesh Status at a Glance (Priority: P3)

While using any car app feature, the driver can glance at a persistent mini-panel showing mesh connectivity health — how many nodes are online, time since last message, and connection status to the radio.

**Why this priority**: Persistent status awareness reduces the need to navigate between screens, minimizing distraction.

**Independent Test**: Can be tested by verifying the minimized control panel remains visible across all screens and updates in real-time.

**Acceptance Scenarios**:

1. **Given** the car app is active on any screen, **When** the driver glances at the minimized control panel, **Then** they see: radio connection status, node count online, and time since last received message
2. **Given** the radio disconnects, **When** the status panel updates, **Then** it clearly indicates "Disconnected" with warning iconography
3. **Given** the minimized panel is visible, **When** the driver taps it, **Then** it expands to show additional detail (mesh name, own node battery, firmware version)

---

### User Story 7 - In-Context Voice Input for Actions (Priority: P3)

A driver uses CAL's built-in voice input to compose messages and perform actions without typing — tapping reply then dictating, or using TTS readback of messages. System-level voice commands ("Hey Google, send Meshtastic message to John") are handled separately by the AppFunctions feature and work automatically on car displays without car module code.

**Why this priority**: Voice is the safest interaction modality while driving and rounds out the hands-free experience.

**Independent Test**: Can be tested by tapping the reply action, dictating a message via CAL voice input, and verifying delivery. System-level "Hey Google" commands are tested via the AppFunctions spec.

**Acceptance Scenarios**:

1. **Given** the car app is on a conversation screen, **When** the driver taps the reply action and speaks a message, **Then** voice composition targets that node/channel using CAL's built-in voice input API
2. **Given** a message is displayed, **When** the driver taps a "read aloud" action, **Then** the message is read via TTS including sender name and content
3. **Given** the driver initiates a direct message from the node dashboard, **When** they tap a node and select "message", **Then** voice input is presented as the default composition method with `FuzzyNameResolver` used for node name matching

---

### Edge Cases

- What happens when the Bluetooth connection to the Meshtastic radio drops mid-conversation? → Banner notification + graceful degradation to cached data, auto-reconnect in background
- What happens when the message list exceeds CAL template item limits? → Cap at 10 conversations with 5 messages each per Android Auto best practices; most recent first
- How does the system handle very long messages that exceed car display constraints? → Truncation with "..." and full message available on tap or read-aloud
- What happens when outgoing messages exceed 237 bytes (Meshtastic protocol limit)? → Reject with user feedback ("Message too long"); do not attempt to send
- What happens when the car's system restricts interaction (e.g., car moving at speed)? → No parked-mode differentiation; the templated messaging UI is uniform regardless of driving state. Voice reply is built into ConversationItem automatically. The Android Auto host enforces its own driving restrictions — the app provides templates only.
- What happens when multiple emergency alerts arrive simultaneously? → Stack as multiple banners; Spotlight Section shows all active emergencies chronologically
- How does the app handle no configured channels? → Show onboarding prompt directing user to configure channels on their phone first
- What happens with emoji-only or admin messages? → Filtered from car display entirely (not shown in conversation list or read aloud)
- What happens on initial session connect with existing unread messages? → Batch-load up to 50 unread messages across conversations; also post MessagingStyle notifications for read-back support
- How are favorites vs recent contacts distinguished? → Favorites (node.favorite == true) grouped at top of DM list with Section Header; remaining contacts sorted by last-heard, capped at 24

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST register as a Car App Service discoverable by Android Auto and AAOS hosts
- **FR-002**: System MUST display incoming mesh messages in a scrollable list grouped by channel using Section Headers
- **FR-003**: System MUST support voice-based message composition as the primary reply method
- **FR-004**: System MUST provide quick-reply templates selectable with a single tap
- **FR-005**: System MUST display emergency messages as high-priority Banners that overlay any active screen within 1 second of receipt
- **FR-006**: System MUST present emergency messages in a Spotlight Section when viewing the messaging screen
- **FR-007**: System MUST display all known mesh nodes as Condensed Items showing name, signal quality, and battery level
- **FR-008**: System MUST support channel switching via Chips displayed at the top of the messaging screen
- **FR-009**: ~~DEFERRED~~ — Map implementation deferred pending NAVIGATION vs POI category decision. See User Story 5.
- **FR-010**: System MUST maintain a persistent Minimized Control Panel showing radio status, online node count, and last message time
- **FR-011**: System MUST display a Banner when the Bluetooth connection to the radio is lost
- **FR-012**: System MUST support expanding node details on tap (last heard, distance, hardware model)
- **FR-013**: System MUST use Expanded Header Layout for the node dashboard showing mesh topology summary
- **FR-014**: System MUST declare MESSAGING as the primary category. POI or NAVIGATION as secondary category is deferred pending map strategy decision.
- **FR-015**: System MUST gracefully degrade to cached/read-only data when the mesh radio is disconnected
- **FR-016**: System MUST support unread message indicators on channel Chips
- **FR-017**: System MUST filter emoji-only and admin messages from the car display (only text messages shown)
- **FR-018**: System MUST reject outgoing messages exceeding 237 bytes (Meshtastic packet limit) with user-visible feedback
- **FR-019**: System MUST display at most 10 conversations and at most 5 messages per ConversationItem, per Android Auto best practices
- **FR-020**: System MUST group direct message contacts into "Favorites" (nodes marked favorite) and "Recent" sections using Section Headers
- **FR-021**: System MUST load up to 50 unread messages across conversations on session start, most recent first
- **FR-022**: System MUST also implement notification-based messaging (NotificationCompat.MessagingStyle with reply and mark-as-read Actions) as required by templated messaging apps

### Non-Functional Requirements

- **NFR-001**: All interactive elements MUST be reachable within 2 taps from any screen
- **NFR-002**: New message display latency MUST be ≤ 3 seconds from radio receipt to screen render
- **NFR-003**: Car app battery overhead MUST be < 10% additional drain compared to the phone app running alone
- **NFR-004**: Car App minimum API level MUST be Car API Level 8 (required for 1.9.0 components)
- **NFR-005**: The car module MUST NOT introduce dependencies that affect the phone app's build time by more than 5%
- **NFR-006**: All text elements MUST meet automotive readability guidelines (minimum font sizes per OEM requirements)
- **NFR-007**: The app MUST support both Android Auto (projection) and AAOS (embedded) deployment modes
- **NFR-008**: Emergency alert audio MUST play through the car's notification channel, not media channel
- **NFR-009**: Car module MUST tag all Crashlytics events with a `car_session` custom key (value: session ID) to enable car-specific crash/ANR filtering and diagnosis
- **NFR-010**: Screen invalidation MUST be debounced (≥300ms) and MUST NOT recreate Screen objects; use `invalidate()` to trigger `onGetTemplate()` re-evaluation, matching CarPlay's proven refresh pattern
- **NFR-011**: Template data refresh latency MUST be ≤500ms from invalidation trigger to rendered update

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| MeshtasticCarAppService | `feature/car/service/` | CAL Session host, entry point for Android Auto/AAOS |
| MessagingScreen | `feature/car/screens/` | Message list with channel chips, voice reply, quick-reply |
| NodeDashboardScreen | `feature/car/screens/` | Condensed Items grid of all mesh nodes |
| ~~MapScreen~~ | ~~`feature/car/screens/`~~ | ~~PlaceListMapTemplate showing node positions~~ — **DEFERRED** |
| EmergencyHandler | `feature/car/alerts/` | Banner management for emergency messages |
| MeshStatusPanel | `feature/car/panels/` | Minimized Control Panel with mesh health |
| CarMessageRepository | `core/data/` | Existing message repository (reused) |
| CarNodeRepository | `core/data/` | Existing node repository (reused) |
| ChannelManager | `core/domain/` | Existing channel logic (reused) |
| BleConnectionManager | `core/ble/` | Existing BLE connection (reused; Application-scoped singleton shared with phone app — CarAppService keeps process alive via host) |

### Component Interaction

```
┌─────────────────────────────────────────────────┐
│              Android Auto / AAOS Host            │
└────────────────────┬────────────────────────────┘
                     │ CAL Session
┌────────────────────▼────────────────────────────┐
│           MeshtasticCarAppService                │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐│
│  │Messaging │ │  Nodes   │ │   Map Screen     ││
│  │ Screen   │ │Dashboard │ │                  ││
│  └────┬─────┘ └────┬─────┘ └───────┬──────────┘│
│       │             │               │            │
│  ┌────▼─────────────▼───────────────▼──────────┐│
│  │         MeshStatusPanel (persistent)        ││
│  └─────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────┐│
│  │         EmergencyHandler (banners)          ││
│  └─────────────────────────────────────────────┘│
└────────────────────┬────────────────────────────┘
                     │ Koin DI
┌────────────────────▼────────────────────────────┐
│         Shared Business Logic (core/)            │
│  ┌─────────┐ ┌─────────┐ ┌────────┐ ┌───────┐ │
│  │Messages │ │  Nodes  │ │Channels│ │  BLE  │ │
│  │  Repo   │ │  Repo   │ │Manager │ │Connect│ │
│  └─────────┘ └─────────┘ └────────┘ └───────┘ │
└─────────────────────────────────────────────────┘
```

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | No changes | All shared business logic already exists in core modules |
| `androidMain` | New `feature/car` module | CAL is Android-only; entire car UI layer is platform-specific |

## Design Standards Compliance

- [ ] New screens reviewed against automotive HMI distraction guidelines (NHTSA Phase 2)
- [ ] CAL template system used exclusively (no custom rendering that bypasses automotive safety checks)
- [ ] Accessibility: Voice readback of all visual information, high-contrast automotive color schemes
- [ ] Typography: Uses CAL's built-in automotive-safe text sizing (enforced by host)
- [ ] Emergency alerts use distinct visual language (color, iconography) distinguishable from informational banners

## Privacy Assessment

- [ ] No PII, location data, or cryptographic keys logged or exposed beyond what existing modules already handle
- [ ] Car app reuses existing data layer — no new network calls or data collection
- [ ] Node location data displayed on map uses existing privacy controls (user opt-in for position sharing)
- [ ] No data sent to third-party automotive services
- [ ] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can read a new message and send a voice reply in under 15 seconds total interaction time
- **SC-002**: Emergency alerts are visible to the driver within 1 second of receipt by the radio
- **SC-003**: Node dashboard displays 6+ nodes simultaneously without scrolling (Condensed Items density)
- **SC-004**: All primary actions (read message, reply, check nodes, view map) reachable within 2 taps from home
- **SC-005**: Car app adds < 10% battery drain overhead compared to phone-only operation over a 1-hour driving session
- **SC-006**: Channel switching completes (chip tap to new message list rendered) within 1 second
- **SC-007**: App passes Android Auto App Quality review criteria for the MESSAGING category
- **SC-008**: 95% of voice-initiated replies complete successfully without fallback to touch input
- **SC-009**: ~~DEFERRED~~ — Map latency criterion deferred with map implementation
- **SC-010**: Zero crashes or ANRs attributed to the car module during a 2-hour continuous driving session

## Assumptions

- Car App Library 1.9.0-alpha01 APIs are sufficiently stable for production use (alpha risk accepted per user directive)
- The existing `core/data` repositories provide all necessary data access; no new data sources required
- Meshtastic radio remains paired and connected via BLE during driving (standard operating mode)
- BLE connection is Application-scoped (not Activity-scoped); CarAppService keeps the host process alive so the connection naturally persists regardless of phone app Activity state
- Users have already configured channels and node settings via the phone app before driving
- Android Auto host enforces its own distraction-optimization rules (template item limits, interaction restrictions); the app respects these constraints
- The `google` build flavor is the distribution target; F-Droid/GitHub flavors do not include car support
- Quick-reply templates are configurable via the phone app's settings; the car app consumes them read-only
- Voice input quality depends on the car's microphone hardware; the app delegates to Android's speech recognition system
- Map template strategy (POI vs NAVIGATION category) is deferred; no map screen in initial implementation
- Minimum Car API Level 8 is required; older Android Auto hosts will not show the app (graceful absence, not crash)
- Koin dependency injection is used consistently with Koin Annotations for the new module
- TTS (text-to-speech) for reading messages aloud uses Android's built-in TTS engine

## External References & Research

### Official Documentation

| Resource | URL | Relevance |
|----------|-----|-----------|
| Car App Library Release Notes | https://developer.android.com/jetpack/androidx/releases/car-app | 1.8.0-beta01 & 1.9.0-alpha01 component APIs |
| Building Car Apps (Training) | https://developer.android.com/training/cars/apps | CarAppService setup, templates, lifecycle |
| Templated Messaging Guide | https://developer.android.com/training/cars/communication/templated-messaging | ConversationItem, voice reply, notification integration |
| Notification-based Messaging | https://developer.android.com/training/cars/messaging | MessagingStyle, reply/mark-as-read Actions |
| Android Auto Add Support | https://developer.android.com/training/cars/apps/auto | Manifest, automotive_app_desc.xml, projection |
| Component Design Guidance | https://developer.android.com/design/ui/cars/guides/components/overview | Automotive HMI patterns |
| Car App Quality Guidelines | https://developer.android.com/docs/quality-guidelines/car-app-quality | Review criteria for MESSAGING category |
| Testing with DHU | https://developer.android.com/training/cars/testing | Desktop Head Unit setup and usage |

### Google I/O 2026 Announcements

| Resource | URL | Key Takeaways |
|----------|-----|---------------|
| Android for Cars: Unifying Platforms | https://android-developers.googleblog.com/2026/05/android-for-cars-unifying-platforms-premium-experiences.html | CAL 1.8.0 media templates, CAL 1.9.0 components, Material 3 Expressive, video support |

### Key API Patterns from Official Docs

#### Templated Messaging (from official guidance)

- **ConversationItem** auto-provides voice reply + mark-as-read actions
- Max **5–10 conversations**, each with ≤ **5 messages**
- Refresh cadence: ≤ **500ms** per invalidation
- Must also implement **notification-based messaging** (MessagingStyle) as fallback
- Distribution: Currently **internal + closed testing** tracks only (production opening later)

#### Manifest Requirements

```xml
<!-- automotive_app_desc.xml for templated messaging -->
<automotiveApp>
    <uses name="notification" />
    <uses name="template" />
</automotiveApp>

<!-- CarAppService intent filter -->
<service android:name=".MeshtasticCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.MESSAGING" />
    </intent-filter>
</service>

<!-- Minimum Car API Level -->
<meta-data android:name="androidx.car.app.minCarApiLevel" android:value="8" />
```

#### ConversationItem Pattern (from official sample)

```kotlin
ConversationItem.Builder()
    .setConversationCallback(callback)
    .setId(conversation.id)
    .setTitle(conversation.title)
    .setIcon(conversation.icon)
    .setMessages(carMessages)
    .setSelf(selfPerson)
    .setGroupConversation(conversation.isGroup)
    .build()
```

### Related In-Flight Features

| Feature | Branch | Spec | Relationship |
|---------|--------|------|-------------|
| App Functions | `jamesarich/crispy-barnacle` | `specs/20260521-091500-app-functions/` | Provides "Hey Google" system AI integration for sendMessage, getMeshStatus, listNodes, getRecentMessages, getNodePosition — complementary to CAL voice input |

#### Shared Infrastructure from AppFunctions

- **`AiFunctionProvider`** interface in `core/data/commonMain` — platform-agnostic contract for AI-driven operations
- **`FuzzyNameResolver`** in `core/data/commonMain` — LCS-based node/channel name matching (50% threshold)
- **`RateLimiter`** in `core/data/commonMain` — sliding window rate limiter (5 calls/60s) for mesh airtime protection
- **Architecture pattern:** Thin Android wrappers (`androidApp/src/google/`) calling shared business logic

#### Integration Points

- Car module reuses `FuzzyNameResolver` for voice reply targeting (e.g., "reply to James" → resolve to node)
- `RateLimiter` can protect car-originated sends from exceeding mesh airtime
- AppFunctions "Hey Google" commands work on car displays automatically (system-level, no car module code needed)
- Both features share: `NodeRepository`, `CommandSender`, `RadioConfigRepository`, `PacketRepository`

### CAL 1.9.0-alpha01 Component Reference

| Component | API Class | Min Car API | Use in Meshtastic |
|-----------|-----------|-------------|-------------------|
| Spotlight Section | `SpotlightSection.Builder()` | 8 | Emergency messages pinned at top |
| Condensed Items | `CondensedItem.Builder()` | 8 | Dense node list (6+ visible) |
| Chips | `Chip.Builder()` | 8 | Channel switching + unread badges |
| Minimized Control Panel | `SectionedItemTemplate` | 8 | Persistent mesh status strip |
| Banners | `Banner.Builder()` | 8 | Emergency overlay + disconnection alerts |
| Section Headers | `SectionHeader.Builder()` | 8 | Message grouping by channel |
| Expanded Header Layout | `Header.Builder()` | 8 | Mesh topology summary (node dashboard) |

### Distribution Constraints (as of May 2026)

- **Templated messaging apps:** Internal + closed testing tracks only on Play Store
- **Production track:** Not yet open for templated messaging category
- **AAOS:** Separate distribution channel (OEM app stores or Play for Automotive)
- **F-Droid:** Excluded (CAL requires Google Play Services)
- **Timeline:** Production track expected to open "later" per Google (no firm date)

### Cross-Platform Parity: Meshtastic-Apple CarPlay

**Source:** `Meshtastic-Apple/Meshtastic/CarPlay/` (main branch, May 21, 2026)

**Apple CarPlay features (shipped):**
- Two-tab UI: Channels + Direct Messages (with Favorites/Recent sections)
- SiriKit voice compose/read-back via `INSendMessageIntent`
- Unread badges per channel and per DM
- "Not Connected" graceful degradation
- Live Activity (Dynamic Island) with node telemetry stats
- Batch donation of 50 unread messages on session start
- 300ms debounced refresh (updateSections, not rebuild)
- Message search via `INSearchForMessagesIntent`
- Message filtering: no emoji-only, no admin messages
- 200-byte message limit enforcement

**Parity decisions incorporated into this spec:**
- FR-017: Message filtering (emoji/admin exclusion) — matches Apple
- FR-018: Message size limit enforcement — matches Apple (237 bytes for Meshtastic)
- FR-019: Conversation caps (10 convos, 5 msgs each) — per Android guidance
- FR-020: Favorites section grouping — matches Apple's Favorites/Recent pattern
- FR-021: Session start unread batch load — matches Apple's 50-message donation
- FR-022: Notification-based messaging fallback — required per Android templated messaging docs
- NFR-010: Refresh debouncing (≥300ms) — matches Apple's proven 300ms debounce
- NFR-011: Refresh latency (≤500ms) — matches Apple's observed performance

**Android-exclusive features (exceeding Apple):**
- Node dashboard with Condensed Items (Apple has no node visibility)
- Emergency Banner overlays with audio alerts (Apple shows emergencies as regular messages)
- ~~Map integration~~ (DEFERRED pending NAVIGATION vs POI decision)
- Channel Chips for instant switching (Apple requires tab navigation)
- Quick-reply templates (Apple only offers Siri voice)
- Visual hierarchy via Spotlight/Section Headers/Expanded Headers
- Persistent Minimized Control Panel (Apple uses separate Live Activity)

**Deferred to v2 (Apple has, we don't yet):**
- Message search (SearchTemplate or via AppFunctions)
- Live Activity equivalent (Android ongoing notification with mesh telemetry)
