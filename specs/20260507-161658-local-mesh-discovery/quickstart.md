# Quickstart — Local Mesh Discovery

## Purpose

This guide helps a Meshtastic-Android contributor bootstrap, navigate, test, and debug the Local Mesh Discovery feature work.

## Prerequisites

- **JDK 21**
- **Android SDK** installed and `ANDROID_HOME` available to Gradle
- **Git submodule initialized** for `core/proto`
- A working `local.properties` file (copy from `secrets.defaults.properties` if needed)
- A Meshtastic radio for end-to-end testing of preset switching and reconnect behavior

## Workspace Bootstrap

Run these commands from the repository root:

```bash
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```

If `ANDROID_HOME` is not already set, use the standard workspace bootstrap logic documented in `.skills/project-overview/SKILL.md`.

## Feature Access Path

Once implemented, the feature entry point should be:

- **In-app**: `Settings > Advanced > Local Mesh Discovery`
- **Canonical deep link**: `meshtastic://meshtastic/settings/local-mesh-discovery`
- **Compatibility alias**: `meshtastic://meshtastic/settings/localMeshDiscovery`

## Recommended Development Commands

### KMP / feature-focused

```bash
./gradlew :feature:discovery:allTests
./gradlew :core:database:allTests
./gradlew kmpSmokeCompile
```

### Android host wiring

```bash
./gradlew :app:testFdroidDebugUnitTest
./gradlew :app:testGoogleDebugUnitTest
./gradlew lintFdroidDebug lintGoogleDebug
```

### Full verification

```bash
./gradlew spotlessApply detekt assembleDebug test allTests
```

> Both `test` and `allTests` are required in this repo. `allTests` covers KMP modules; `test` covers pure Android modules.

## Key Files

| Path | Why it matters |
|---|---|
| `specs/001-local-mesh-discovery/spec.md` | Primary feature specification |
| `specs/001-local-mesh-discovery/data-model.md` | Discovery Room KMP schema design |
| `feature/discovery/build.gradle.kts` | New feature module build definition |
| `feature/discovery/src/commonMain/kotlin/org/meshtastic/feature/discovery/scan/DiscoveryScanCoordinator.kt` | Shared scan orchestration state machine |
| `feature/discovery/src/commonMain/kotlin/org/meshtastic/feature/discovery/DiscoveryViewModel.kt` | Screen state + user actions |
| `feature/discovery/src/commonMain/kotlin/org/meshtastic/feature/discovery/navigation/DiscoveryNavigation.kt` | Navigation 3 entry registration |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` | Typed route definitions |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` | Deep-link path mapping |
| `core/database/src/commonMain/kotlin/org/meshtastic/core/database/MeshtasticDatabase.kt` | Room KMP schema registration |
| `core/database/src/commonMain/kotlin/org/meshtastic/core/database/entity/DiscoverySessionEntity.kt` | Discovery session persistence entity |
| `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/RadioConfig.kt` | Settings > Advanced entry point |
| `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/NeighborInfoHandlerImpl.kt` | Existing topology capture hook |
| `core/network/src/commonMain/kotlin/org/meshtastic/core/network/radio/BleReconnectPolicy.kt` | Existing reconnect behavior discovery depends on |

## Logging and Diagnostics

Use the existing logging stack rather than inventing a feature-local logger.

### Suggested tags/classes to instrument

- `DiscoveryScanCoordinator`
- `DiscoveryPacketCollector`
- `DiscoveryRepository`
- `DiscoveryRankingEngine`
- `AndroidDiscoveryRecommendationEngine`

### Where to inspect logs

- **Android**: `adb logcat` and the existing in-app Debug Panel (`Settings > Advanced > Debug Panel`) if mesh logging is enabled.
- **Desktop/JVM**: stdout / IDE console plus Kermit-backed logs.

### Suggested Android logcat filter

```bash
adb logcat | grep -E "Discovery|BleRadioTransport|BleReconnectPolicy|NeighborInfo"
```

## Manual End-to-End Test Loop

1. Connect to a radio over BLE.
2. Open `Settings > Advanced > Local Mesh Discovery`.
3. Select one supported preset and minimum dwell time.
4. Start scan and verify:
   - home preset snapshot succeeds
   - preset change is dispatched
   - reconnect wait state appears if the radio reboots
   - dwell countdown begins only after reconnect
5. Stop the scan early and verify partial session persistence + home preset restore.
6. Re-open history and confirm the session is visible without reconnecting.

## Common Pitfalls

- Forgetting `allTests` means KMP tests may not run.
- Forgetting to initialize the proto submodule breaks builds unrelated to discovery logic.
- Using direct Android map or AI APIs in `commonMain` will violate KMP boundaries.
- Treating 2.4 GHz capability as always known is unsafe; unknown must default to blocked.
- Letting dwell time run during reconnect will corrupt results.

## Done Definition for This Feature

Before calling the feature done locally:

1. Room schema and DAO tests pass.
2. Scan engine tests cover cancel/fail/restore behavior.
3. Discovery routes deep-link correctly.
4. Android host tests pass for changed wiring.
5. Full repo verification command passes.
