# `:core:data`

## Overview
The `:core:data` module holds the concrete implementations of the `:core:repository` contracts, serving as the primary data source for ViewModels in feature modules. It orchestrates data flow between the local database (`core:database`), remote services, and network repositories.

## Key Components

### 1. Repository implementations (`repository/`)
- **`NodeRepositoryImpl`**: High-level access to node information and mesh state (Room KMP backed).
- **`PacketRepositoryImpl`** / **`MeshLogRepositoryImpl`**: Stored messages and packets, and historical logs and diagnostics.
- **`RadioConfigRepositoryImpl`**: The connected device's config, module config and channel set.
- **`SignalSurveyRepositoryImpl`**: Signal survey sessions and their readings, stored through `SignalSurveyLocalDataSource`.
- **`FirmwareReleaseRepositoryImpl`** / **`DeviceHardwareRepositoryImpl`**: Firmware releases and the hardware list, served by `staleWhileRevalidateFlow` from the local data sources and bundled assets in `datasource/`, refreshed from the `:core:network` remote data sources.

### 2. Manager implementations (`manager/`)
- **`MeshConnectionManagerImpl`** / **`MeshConfigFlowManagerImpl`**: Connection lifecycle and the config handshake.
- **`CommandSenderImpl`**: The traceroute, neighbor-info and remote local-stats requests the SDK cannot yet send as firmware expects; every other command goes through the session's SDK client.
- **`SessionManagerImpl`**: Per-node remote-admin passkey store.
- **`RadioSessionProjection`** / **`RadioSessionEventHandler`**: Project the SDK's typed session events into Room and the repositories: the Ready snapshot, packets, node, config and channel changes, and events such as client notifications and the MQTT proxy. Packets reach `MeshDataHandlerImpl` and its per-port handlers (`TelemetryPacketHandlerImpl`, `TracerouteHandlerImpl`, and others).
- **`PacketHandlerImpl`**: Data packets (messages, reactions, waypoints, alerts, TAK) and app-built requests through the SDK's paced send under their persisted IDs, with the Room delivery projection; it also generates packet IDs.
- **`NodeManagerImpl`**, **`MqttManagerImpl`**: The in-memory node database and the MQTT proxy.

### 3. `ai/`
`AiFunctionProvider` is the contract for operations a system assistant can invoke (on Android, through AppFunctions), with `FuzzyNameResolver` resolving node and channel names.


## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:data[data]:::kmp-library
  :core:data --> :core:repository
  :core:data -.-> :core:common
  :core:data -.-> :core:database
  :core:data -.-> :core:datastore
  :core:data -.-> :core:di
  :core:data -.-> :core:model
  :core:data -.-> :core:network
  :core:data -.-> :core:testing

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
