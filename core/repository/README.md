# `:core:repository`

## Overview

The `:core:repository` module defines the **data and infrastructure contracts** for the Meshtastic KMP architecture. It is almost entirely interfaces — concrete implementations live in `:core:service` and platform modules. Consumers receive `:core:model` and `:core:proto` transitively because both are `api()`-exported.

**Targets:** Android · JVM · iOS (via `meshtastic.kmp.library` convention plugin)

## Key Responsibilities

- Define the reactive data contracts between the long-running mesh service and all feature/UI layers
- Declare the raw hardware I/O interface (`RadioTransport`)
- Provide the mesh node database interface (`NodeRepository`)
- Expose per-node remote-admin passkey management (`SessionManager`)
- Host all packet handlers (admin, telemetry, traceroute, store-and-forward, neighbour info)
- Manage the outbound message queue, MQTT bridge, and XModem firmware transfer
- Provide the `AppWidgetUpdater` contract so the mesh service can trigger widget refreshes without depending on Android widget APIs directly

## Source Structure

```
src/
├── commonMain/kotlin/org/meshtastic/core/repository/
│   ├── RadioTransport.kt              ← interface: raw hardware I/O
│   ├── ServiceRepository.kt           ← interface: service ↔ UI bridge
│   ├── NodeRepository.kt              ← interface: mesh node database
│   ├── SessionManager.kt              ← interface: per-node passkey store
│   ├── MeshConnectionManager.kt       ← interface: connection lifecycle callbacks
│   ├── AppWidgetUpdater.kt            ← interface: trigger widget refresh
│   ├── LocationRepository.kt
│   ├── LocationService.kt
│   ├── RadioController.kt             ← interface: composite radio command API
│   ├── AdminController.kt             ← config, channels, owner, device lifecycle, editSettings
│   ├── MessagingController.kt         ← send packets, reactions, contacts
│   ├── NodeController.kt              ← favorite, ignore, mute, remove nodes
│   ├── RequestController.kt           ← telemetry, traceroute, position queries
│   ├── CommandSender.kt
│   ├── AdminPacketHandler.kt
│   ├── FromRadioPacketHandler.kt
│   ├── MeshConfigFlowManager.kt
│   ├── MeshConfigHandler.kt
│   ├── MeshDataHandler.kt
│   ├── MeshLocationManager.kt
│   ├── MeshLogRepository.kt
│   ├── MeshMessageProcessor.kt
│   ├── MessageFilter.kt
│   ├── MessageQueue.kt
│   ├── MqttManager.kt
│   ├── NeighborInfoHandler.kt
│   ├── NodeManager.kt
│   ├── Notification.kt / NotificationManager.kt
│   ├── PacketHandler.kt / PacketRepository.kt
│   ├── QuickChatActionRepository.kt
│   ├── RadioConfigRepository.kt
│   ├── RadioInterfaceService.kt
│   ├── RadioTransportCallback.kt / RadioTransportFactory.kt
│   ├── StoreForwardPacketHandler.kt
│   ├── TelemetryPacketHandler.kt
│   ├── TracerouteHandler.kt / TracerouteSnapshotRepository.kt
│   ├── XModemFile.kt / XModemManager.kt
│   ├── usecase/
│   │   └── SendMessageUseCase.kt
│   └── di/
│       └── CoreRepositoryModule.kt
├── androidMain/kotlin/    ← Android LocationRepository actual
├── iosMain/kotlin/        ← iOS LocationRepository actual
└── jvmMain/kotlin/        ← Desktop LocationRepository actual
```

## Core Interfaces

### `RadioTransport`

Raw hardware I/O contract for all physical transports (BLE, USB, TCP, Mock).

```kotlin
interface RadioTransport {
    fun handleSendToRadio(p: ByteArray)
    fun start()
    fun keepAlive()
    suspend fun close()
}
```

### `ServiceRepository`

The primary reactive bridge between the long-running mesh service and all feature/UI layers.

```kotlin
interface ServiceRepository {
    val connectionState: StateFlow<ConnectionState>
    val clientNotification: StateFlow<ClientNotification?>
    val errorMessage: StateFlow<String?>
    val connectionProgress: StateFlow<String?>
    val meshPacketFlow: Flow<MeshPacket>
    val tracerouteResponse: StateFlow<TracerouteResponse?>
    val neighborInfoResponse: StateFlow<String?>

    fun setConnectionState(state: ConnectionState)
    suspend fun emitMeshPacket(packet: MeshPacket)
    fun setClientNotification(notification: ClientNotification?)
    fun setTracerouteResponse(value: TracerouteResponse?)
    // …setters/clearers for error, progress, neighbor-info
}

Radio commands are issued through `RadioController` (a composite of `AdminController`,
`MessagingController`, `NodeController`, `RequestController`) rather than an action/intent bus.
```

### `NodeRepository`

Reactive mesh node database. Backed by Room KMP in `:core:service`.

```kotlin
interface NodeRepository {
    val myNodeInfo: StateFlow<MyNodeInfo?>
    val ourNodeInfo: StateFlow<Node?>
    val myId: StateFlow<String?>
    val localStats: StateFlow<LocalStats>
    val nodeDBbyNum: StateFlow<Map<Int, Node>>
    val onlineNodeCount: Flow<Int>
    val totalNodeCount: Flow<Int>

    fun getNodes(sort, filter, includeUnknown, onlyOnline, onlyDirect): Flow<List<Node>>
    suspend fun upsert(node: Node)
    suspend fun clearNodeDB(preserveFavorites: Boolean = false)
    suspend fun deleteNode(num: Int)
    suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata)
    suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>)
}
```

### `SessionManager`

Per-node remote-admin passkey store, consumed by `:core:domain`'s `EnsureRemoteAdminSessionUseCase`.

```kotlin
interface SessionManager {
    fun recordSession(srcNodeNum: Int, passkey: ByteString)
    fun getPasskey(destNum: Int): ByteString
    fun clearAll()
    val sessionRefreshFlow: Flow<Int>
    fun observeSessionStatus(destNum: Int): Flow<SessionStatus>
}
```

## Dependency Graph

```
core:repository
  ├── api → core:model    (exported to consumers)
  ├── api → core:proto    (exported to consumers)
  ├── core:common
  ├── core:database
  └── kotlinx.coroutines, kermit, androidx.paging.common
```

## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:repository[repository]:::kmp-library
  :core:repository --> :core:model
  :core:repository --> :core:proto
  :core:repository -.-> :core:common
  :core:repository -.-> :core:database
  :core:repository -.-> :core:testing

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
