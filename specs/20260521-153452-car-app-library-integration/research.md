# Research: Car App Library Integration

**Feature**: Car App Library Integration
**Date**: 2026-05-21

## R1: Car App Library 1.9.0-alpha01 New Components

**Decision**: Use all 7 new CAL 1.9.0-alpha01 components as specified

**Rationale**: The alpha release provides modern automotive UI components that directly map to Meshtastic use cases. The user explicitly accepted alpha risk.

**Components and their application**:

| CAL Component | Meshtastic Screen | Purpose |
|---------------|-------------------|---------|
| Spotlight Section | Messaging (emergency) | Emergency messages pinned at top of message list |
| Condensed Items | Node Dashboard | Dense node list showing 6+ nodes without scroll |
| Chips | Messaging (channels) | Channel switching with unread badges |
| Minimized Control Panel | All screens (persistent) | Mesh status: radio connection, node count, last message time |
| Banners | Emergency alerts | Full-screen overlay for emergency broadcasts |
| Section Headers | Messaging | Group messages by channel within conversation list |
| Expanded Header Layout | Node Dashboard | Mesh topology summary at top of node grid |

**Alternatives considered**:
- Wait for stable 1.9.0 release → Rejected: Timeline unknown; alpha APIs are functionally complete
- Use legacy ListTemplate/MessageTemplate → Rejected: Misses density benefits (Condensed Items) and visual hierarchy (Spotlight/Headers)

**API Level requirement**: Car API Level 8 (maps to `minCarApiLevel 8` in manifest). Older hosts gracefully hide the app.

## R2: Module Architecture — Android-Only vs KMP

**Decision**: Create `feature/car` as an Android-only library module (not KMP)

**Rationale**: CAL SDK is exclusively Android. Creating a KMP module with only `androidMain` source sets would add unnecessary complexity (empty `commonMain`, unused KMP plugin overhead). The project already has Android-only modules (`core/api`, `core/barcode`, `androidApp`) as precedent.

**Build plugin**: `AndroidLibraryFlavorsConventionPlugin` (not `KmpLibraryConventionPlugin`) — ensures proper flavor-aware configuration consistent with existing Android-only modules.

**Alternatives considered**:
- KMP module with `androidMain` only → Rejected: No cross-platform value; KMP plugin adds 2-3s build overhead with zero benefit
- Inline within `androidApp` module → Rejected: Violates separation of concerns; feature modules should be independent

## R3: BLE Connection Sharing Strategy

**Decision**: Shared Application-scoped `BleConnection` singleton via Koin, no new connection management

**Rationale**: The existing `BleConnection` in `core/ble` is already scoped to the Application lifecycle via Koin's singleton scope. When Android Auto starts the `CarAppService`, it runs in the same process as the phone app (projection mode) — the Koin graph is shared naturally. The `CarAppService` keeps the process alive via the Android Auto host binding, ensuring the BLE connection persists.

**Key implementation detail**: `KableBleConnection` is instantiated by `KableBleConnectionFactory` and held as a Koin singleton. The car module simply injects the same instance — no reconnection logic needed.

**AAOS (embedded) consideration**: On AAOS, the app runs as a standalone process. The same Koin graph initializes in `Application.onCreate()`. BLE connection management is identical because it's Application-scoped regardless of entry point.

**Alternatives considered**:
- Dedicated car BLE connection → Rejected: Would conflict with phone app's connection; BLE to Meshtastic radio is single-link
- Service binding to phone app → Rejected: Unnecessary IPC; same process in projection mode; AAOS doesn't have the phone app

## R4: Crashlytics car_session Tagging

**Decision**: Tag all Crashlytics events with `car_session` custom key during car session lifecycle

**Rationale**: Enables filtering car-specific crashes/ANRs in Firebase console without new infrastructure. The `MeshtasticCarSession` sets the key on `onCreateScreen()` and clears on `onDestroy()`.

**Implementation**:
```kotlin
// In MeshtasticCarSession.onCreateScreen():
FirebaseCrashlytics.getInstance().setCustomKey("car_session", sessionInfo.sessionId.toString())

// In MeshtasticCarSession lifecycle end:
FirebaseCrashlytics.getInstance().setCustomKey("car_session", "")
```

**Alternatives considered**:
- Separate Crashlytics instance → Not possible; Firebase is process-wide singleton
- DataDog APM → Rejected: Project uses Crashlytics; DataDog not in dependency graph

## R5: Messaging via ConversationItem + Voice Reply

**Decision**: Use `ConversationItem` API with CAL's built-in voice input for reply

**Rationale**: CAL's `ConversationItem` is purpose-built for messaging apps on Android Auto. It handles:
- Message display with sender avatar, name, timestamp
- Unread indicators
- Voice reply flow (tap → record → send) with no custom speech recognition needed
- Quick-reply suggestions

The existing `SendMessageUseCase` in `core/repository` accepts `(text, contactKey, replyId)` — the car module calls this directly after voice transcription completes.

**Data flow**: `ConversationItem.onReply { text -> sendMessageUseCase(text, contactKey) }`

**Alternatives considered**:
- Custom speech recognition → Rejected: CAL handles this automatically; would duplicate system capabilities
- Google Assistant App Actions → Rejected: Separate concern handled by AppFunctions feature

## R6: PlaceListMapTemplate for Node Map (POI Category)

**Decision**: Use `PlaceListMapTemplate` under POI category for static node position display

**Rationale**: POI category avoids NAVIGATION category requirements (turn-by-turn guidance, active routing), which would trigger additional Play Store review burden and potential conflicts with navigation apps. `PlaceListMapTemplate` renders a map with place items (pins) + a scrollable list — perfect for showing node positions.

**Implementation approach**:
- Each node with known GPS position becomes a `Place` item with `LatLng`
- List items show node name + distance + last update time
- Map auto-zooms to fit all visible pins
- Tap a list item → NodeDetailScreen with message option
- Refresh interval: 5 seconds (matches NFR map update latency requirement)

**Limitation**: No live tracking line or animated position updates (NAVIGATION category feature, deferred to v2)

**Alternatives considered**:
- MapWithContentTemplate + NAVIGATION category → Rejected by spec decision; deferred to v2
- No map at all → Rejected: Location awareness is core Meshtastic differentiator

## R7: Koin DI Integration for Car Module

**Decision**: New `FeatureCarModule` using Koin Annotations, registered in app's module graph

**Rationale**: Consistent with project's DI pattern. All feature modules declare a Koin module that is included by the `androidApp` module graph. The car module's DI graph is simple — it only needs to declare car-specific Screen factories and the EmergencyHandler; all business logic comes from existing core modules.

**Registration**: `androidApp/src/googleMain/` includes `FeatureCarModule` in the Koin application configuration (google flavor only).

**Key bindings**:
- `MeshtasticCarSession` → factory (new per session)
- `EmergencyHandler` → singleton (one per process)
- `CrashlyticsCarTagger` → singleton
- All repositories, use cases → inherited from existing core modules (already in graph)

## R8: AppFunctions Interop — Shared Interface Reuse

**Decision**: Reuse `FuzzyNameResolver` pattern from AppFunctions for node name matching in voice replies

**Rationale**: When a driver sends a direct message via voice, they may say a node name imprecisely. The AppFunctions feature (in-flight) implements fuzzy node name resolution. While the `AiFunctionProvider` interface is not yet merged, the car module can implement the same fuzzy matching logic directly using `NodeRepository.nodeDBbyNum` and Levenshtein distance or substring matching.

**Implementation**: Standalone `FuzzyNodeNameResolver` utility class in `feature/car/util/` that queries `NodeRepository` and performs case-insensitive substring + edit-distance matching. If/when AppFunctions lands and exposes a shared resolver in `core/data/commonMain`, the car module can delegate to it.

**Alternatives considered**:
- Wait for AppFunctions to land first → Rejected: Unclear timeline; car module should not block on it
- Exact match only → Rejected: Poor voice UX ("node exclamation one two three four" vs "James")

## R9: Emergency Alert Banner Strategy

**Decision**: Observe emergency messages via `PacketRepository` Flow, trigger CAL Banner API

**Rationale**: Emergency messages are already classified in the packet data layer (message type/priority). The `EmergencyHandler` subscribes to the message flow, filters for emergency-priority packets, and immediately invokes `CarToast` + `AppManager.showAlert()` to display a Banner. The Banner overlays any active screen within CAL's rendering pipeline.

**Audio**: Use `NotificationManager` to play a notification sound on the car's notification audio channel (`AudioAttributes.USAGE_NOTIFICATION`), not media channel (per NFR-008).

**Alternatives considered**:
- Poll for emergencies on timer → Rejected: Violates 1-second latency requirement
- Use Android notifications only → Rejected: Would not overlay within CAL UI; needs in-app Banner

## R10: Build Configuration — Google Flavor Only

**Decision**: `feature/car` module included only in the `google` product flavor

**Rationale**: CAL apps require Google Play Services for Android Auto projection. The F-Droid flavor explicitly excludes Google dependencies. The module is conditionally included via flavor-based dependency in `androidApp/build.gradle.kts`:

```kotlin
"googleImplementation"(projects.feature.car)
```

This mirrors existing patterns like Firebase/Maps dependencies being google-flavor-only.

**Alternatives considered**:
- Include in all flavors → Rejected: CAL requires Google Play Services; F-Droid builds would fail
- Separate app module for car → Rejected: Adds unnecessary complexity; flavor separation is simpler
