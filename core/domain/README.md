# `:core:domain`

## Overview

The `:core:domain` module is the **business-logic layer** of the KMP architecture. It contains exclusively use-case classes, with no UI and no platform code. Each use case is a thin orchestrator that coordinates one or more repository/model dependencies to fulfill a single application action.

**Targets:** Android · JVM · iOS (via `meshtastic.kmp.library` convention plugin)

## Key Responsibilities

- Orchestrate node configuration reads/writes (config, module config, channels, owner, position)
- Manage remote-admin session lifecycle (per-node passkey negotiation)
- Process admin responses from the node and manage mesh log settings
- Data export (CSV mesh log, JSON node database, `DeviceProfile` protobuf)
- Profile and security-config import/install
- Node database maintenance (clean, reset, selective purge) and OTA capability checks

## Source Structure

```text
src/commonMain/kotlin/org/meshtastic/core/domain/
├── di/
│   └── CoreDomainModule.kt          ← Koin @Module + component scan
└── usecase/
    ├── session/
    │   ├── EnsureRemoteAdminSessionUseCase.kt
    │   ├── EnsureSessionResult.kt
    │   └── ObserveRemoteAdminSessionStatusUseCase.kt
    └── settings/
        ├── AdminActionsUseCase.kt
        ├── CleanNodeDatabaseUseCase.kt
        ├── ExportDataUseCase.kt
        ├── ExportNodeDatabaseUseCase.kt  ← plus NodeDatabaseExport.kt, its JSON model
        ├── ExportProfileUseCase.kt
        ├── ImportProfileUseCase.kt
        ├── ImportSecurityConfigUseCase.kt
        ├── InstallProfileUseCase.kt
        ├── IsOtaCapableUseCase.kt
        ├── ProcessRadioResponseUseCase.kt
        ├── RadioConfigUseCase.kt
        └── SetMeshLogSettingsUseCase.kt
```

## Notable APIs

### `EnsureRemoteAdminSessionUseCase`

Ensures a per-node remote-admin passkey session exists before entering the remote admin UI. Uses a `Mutex`-guarded `inFlight` map so that double-taps coalesce onto a single `Deferred`.

```kotlin
sealed interface EnsureSessionResult {
    data object AlreadyActive   : EnsureSessionResult  // passkey already fresh
    data object Refreshed       : EnsureSessionResult  // metadata response arrived
    data object Timeout         : EnsureSessionResult  // no response within 30 s
    data object Disconnected    : EnsureSessionResult  // radio not connected
}
```

### `RadioConfigUseCase`

Node configuration read/write operations. Each admin request returns its `packetId` for async tracking, except `setFixedPosition`, `removeFixedPosition`, `setRingtone` and `setCannedMessages`, which return nothing:

| Method | Description |
|---|---|
| `setOwner` / `getOwner` | Node owner info |
| `setHamMode` | Licensed (ham) mode parameters |
| `setConfig` / `getConfig` | `Config` proto (device, position, power, …) |
| `setModuleConfig` / `getModuleConfig` | `ModuleConfig` proto |
| `getChannel` / `setRemoteChannel` | Channel configuration |
| `setFixedPosition` / `removeFixedPosition` | Fixed GPS position |
| `setRingtone` / `getRingtone` | External notification ringtone |
| `setCannedMessages` / `getCannedMessages` | Canned message slots |
| `getDeviceConnectionStatus` | The node's `DeviceConnectionStatus` |

### `AdminActionsUseCase`

Each call also takes an optional `onRequestId` callback and returns the request's packet id.

```kotlin
reboot(destNum)
rebootToDfu(destNum)
shutdown(destNum)
setTime(destNum)                 // sync the node's clock to the phone's
factoryReset(destNum, isLocal)   // also clears local NodeDB when isLocal = true
nodedbReset(destNum, preserveFavorites, isLocal)
```

### `ExportDataUseCase`

Streams the mesh log's received packets that carry an SNR reading to a CSV `BufferedSink`, optionally filtered to one port. Columns: date, time, from, sender name/location, received location/elevation, SNR, distance, hop limit, hop start, relay node, payload.

### `ExportNodeDatabaseUseCase`

Writes the node database as JSON (`NodeDatabaseExport`), omitting absent fields rather than writing zero.

## Dependency Graph

```text
core:domain
  ├── core:repository              (repository and controller contracts)
  ├── core:model                   (domain models)
  ├── org.meshtastic:protobufs     (Meshtastic protobuf types, Maven)
  ├── core:common
  └── core:resources
```

The generated Mermaid graph below renders project-module edges only, so external Maven artifacts such as `org.meshtastic:protobufs` are not shown. Solid edges are `api` dependencies and dashed edges are `implementation` ones, test source sets included (`:core:testing` comes from `commonTest`).

## DI

All use cases are registered via Koin component scan on `org.meshtastic.core.domain`. No manual binding is needed — annotate a new use case with `@Single` and it is picked up automatically.

## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:domain[domain]:::kmp-library
  :core:domain -.-> :core:repository
  :core:domain -.-> :core:model
  :core:domain -.-> :core:common
  :core:domain -.-> :core:resources
  :core:domain -.-> :core:testing

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library-compose fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
