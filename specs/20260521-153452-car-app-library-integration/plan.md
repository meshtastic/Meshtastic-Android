# Implementation Plan: Car App Library Integration

**Branch**: `feature/20260521-153452-car-app-library-integration` | **Date**: 2026-05-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/20260521-153452-car-app-library-integration/spec.md`

## Summary

Integrate Android Car App Library 1.9.0-alpha01 into Meshtastic-Android as a new `feature/car` module, delivering a complete automotive mesh radio interface with 7 screens (messaging, node dashboard, channel management, emergency alerts, map, quick actions, mesh status panel). The module is Android-only, reuses all existing `core/` business logic via Koin DI, and leverages CAL's template-based rendering (no Compose). Voice reply uses CAL's built-in ConversationItem voice input; system-level "Hey Google" commands are handled separately by the AppFunctions feature.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21, Car API Level 8+

**Primary Dependencies**: `androidx.car.app:app:1.9.0-alpha01`, `androidx.car.app:app-projected:1.9.0-alpha01`, `androidx.car.app:app-automotive:1.9.0-alpha01`, Koin 4.2.1 (Koin Annotations + K2 Plugin), Firebase Crashlytics (BOM 34.13.0)

**Storage**: Room KMP (existing), DataStore KMP (existing) — no new storage

**Testing**: `./gradlew :feature:car:testGoogleDebugUnitTest` (Android-only module), `androidx.car.app:app-testing:1.9.0-alpha01` for host simulation, Robolectric for unit tests

**Target Platform**: Android Auto (projection, API 23+) and AAOS (embedded), Car API Level 8 minimum

**Project Type**: Mobile app — new Android-only feature module within KMP project

**Performance Goals**: Message display latency ≤ 3s, emergency banner ≤ 1s, channel switch ≤ 1s, map pin update ≤ 5s

**Constraints**: ≤ 2 taps for all primary actions, < 10% battery overhead, zero crashes/ANRs in 2-hour sessions, `google` flavor only

**Scale/Scope**: 7 car screens, ~15-20 new source files, 1 new Gradle module, 0 changes to existing modules' APIs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ PASS — No `commonMain` changes. All new code resides in `feature/car/src/main/` (Android-only module). Business logic is consumed from existing `core/repository`, `core/data`, `core/domain`, `core/ble` KMP modules via their public interfaces. No new business logic is introduced in the car module — it is purely a presentation layer adapting existing repositories to CAL templates.

- **II. Zero Lint Tolerance**: ✅ PASS — Will run:
  - `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck`
  - `./gradlew :feature:car:detekt`
  - Module is Android-only so uses standard detekt tasks (not KMP variants)

- **III. Compose Multiplatform UI**: ✅ N/A — Car App Library uses its own template-based rendering system, not Compose. No `@Composable` functions are introduced. `MeshtasticNavDisplay` and `NavigationBackHandler` do not apply to CAL's `ScreenManager` navigation. No floats displayed (all text pre-formatted by existing `MetricFormatter`/`NumberFormatter` in core modules).

- **IV. Privacy First**: ✅ PASS — No new data collection or network calls. Reuses existing repositories with their privacy controls. Location data on map uses existing user-opt-in position sharing. No PII/keys in logs. Crashlytics tagging uses session ID only (no PII). `core/proto` submodule not modified.

- **V. Design Standards Compliance**: ✅ N/A (justified) — CAL apps use automotive-specific template design language enforced by the Android Auto host, not the Meshtastic Client Design Standards which target phone/desktop Compose UI. The host enforces readability (font sizes, item limits, distraction guidelines). Cross-Platform Spec field is N/A because CAL is Android-only with no cross-platform equivalent. Emergency alert visual treatment follows NHTSA Phase 2 automotive HMI guidelines via CAL Banner APIs.

- **VI. Verify Before Push**: ✅ Commands recorded:
  ```bash
  # Local verification
  ./gradlew spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:testGoogleDebugUnitTest

  # Post-push CI check
  gh pr checks <PR> || gh run list --branch feature/20260521-153452-car-app-library-integration --limit 5
  ```

## Project Structure

### Documentation (this feature)

```text
specs/20260521-153452-car-app-library-integration/
├── plan.md              # This file
├── research.md          # Phase 0: CAL API research, architecture decisions
├── data-model.md        # Phase 1: Entities and state models
├── quickstart.md        # Phase 1: Developer onboarding guide
├── contracts/           # Phase 1: CAL service contracts and manifest declarations
│   ├── car-app-service.md
│   └── manifest-declarations.md
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
feature/car/
├── build.gradle.kts                    # Android-only library, google flavor only
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml         # CarAppService declaration, categories
│   │   ├── kotlin/org/meshtastic/feature/car/
│   │   │   ├── di/
│   │   │   │   └── FeatureCarModule.kt           # Koin module for car DI
│   │   │   ├── service/
│   │   │   │   ├── MeshtasticCarAppService.kt    # CarAppService entry point
│   │   │   │   └── MeshtasticCarSession.kt       # Session lifecycle, screen manager
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt                 # Tab-based entry (messaging, nodes, map)
│   │   │   │   ├── MessagingScreen.kt            # ConversationItem list, channel chips
│   │   │   │   ├── ConversationScreen.kt         # Single conversation with voice reply
│   │   │   │   ├── NodeDashboardScreen.kt        # Condensed Items node grid
│   │   │   │   ├── NodeDetailScreen.kt           # Expanded node info
│   │   │   │   ├── MapScreen.kt                  # PlaceListMapTemplate
│   │   │   │   └── ChannelManagementScreen.kt    # Channel selection/switching
│   │   │   ├── alerts/
│   │   │   │   └── EmergencyHandler.kt           # Banner management for emergencies
│   │   │   ├── panels/
│   │   │   │   └── MeshStatusPanel.kt            # Minimized Control Panel
│   │   │   └── util/
│   │   │       ├── CrashlyticsCarTagger.kt       # car_session key tagging
│   │   │       └── TemplateBuilders.kt           # Helper extensions for CAL templates
│   │   └── res/
│   │       ├── values/
│   │       │   └── strings.xml                   # Car-specific strings
│   │       └── xml/
│   │           └── automotive_app_desc.xml        # AAOS app description
│   └── test/
│       └── kotlin/org/meshtastic/feature/car/
│           ├── service/
│           │   └── MeshtasticCarSessionTest.kt
│           ├── screens/
│           │   ├── MessagingScreenTest.kt
│           │   ├── NodeDashboardScreenTest.kt
│           │   └── MapScreenTest.kt
│           └── alerts/
│               └── EmergencyHandlerTest.kt

# Existing modules (consumed, NOT modified):
core/repository/    # PacketRepository, NodeRepository, QuickChatActionRepository, SendMessageUseCase
core/data/          # NodeRepositoryImpl, PacketRepositoryImpl
core/ble/           # BleConnection (Application-scoped singleton)
core/model/         # Node, DataPacket, MyNodeInfo, etc.
core/domain/        # Use cases (SendMessageUseCase, etc.)
```

**Structure Decision**: New `feature/car` module as an Android-only library (not KMP). Follows existing feature module pattern but uses `AndroidLibraryFlavorsConventionPlugin` instead of KMP plugin since CAL has no multiplatform support. Only the `google` flavor includes this module (mirrors Maps/Crashlytics flavor split).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| III. Compose Multiplatform UI — N/A | CAL uses proprietary template system, not Compose | Cannot render Compose inside automotive templates; CAL enforces distraction-safe UI via templates exclusively |
| V. Design Standards — N/A | Automotive design is governed by NHTSA + host-enforced constraints | Meshtastic Design Standards target phone/desktop Compose; applying them to CAL templates would conflict with automotive safety requirements |
| Android-only module in KMP project | CAL SDK is Android-exclusive | No KMP equivalent exists; all business logic remains in `commonMain` — only the thin presentation adapter is platform-specific |
