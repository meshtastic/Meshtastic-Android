# `:core:repository`

## Overview

The `:core:repository` module defines the **data and infrastructure contracts** for the Meshtastic KMP architecture. It is almost entirely interfaces — concrete implementations live in `:core:data`, `:core:service`, and platform modules. Consumers receive `:core:model` and the `org.meshtastic:protobufs` Maven artifact transitively because both are `api()`-exported.

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
│   ├── ServiceRepository.kt           ← interface: service ↔ UI bridge (extends all providers)
│   ├── ConnectionStateProvider.kt     ← interface: read-only connection state
│   ├── ResponseProviders.kt           ← interfaces: TracerouteResponseProvider, NeighborInfoResponseProvider
│   ├── ServiceStateWriter.kt          ← interface: write-side for handlers
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
│   ├── QueryController.kt           ← telemetry, traceroute, position queries
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
Decomposed into focused sub-interfaces via Interface Segregation Principle:

```kotlin
interface ConnectionStateProvider {
    val connectionState: StateFlow<ConnectionState>
}

interface TracerouteResponseProvider {
    val tracerouteResponse: StateFlow<TracerouteResponse?>
    fun clearTracerouteResponse()
}

interface NeighborInfoResponseProvider {
    val neighborInfoResponse: StateFlow<String?>
    fun clearNeighborInfoResponse()
}

interface ServiceStateWriter {
    fun setConnectionState(state: ConnectionState)
    suspend fun emitMeshPacket(packet: MeshPacket)
    fun setTracerouteResponse(value: TracerouteResponse?)
    fun setNeighborInfoResponse(value: String?)
    // …setters/clearers for error, progress, notification
}

interface ServiceRepository :
    ConnectionStateProvider,
    TracerouteResponseProvider,
    NeighborInfoResponseProvider,
    ServiceStateWriter {
    val clientNotification: StateFlow<ClientNotification?>
    val errorMessage: StateFlow<String?>
    val connectionProgress: StateFlow<String?>
    val meshPacketFlow: Flow<MeshPacket>
}
```

VMs inject the narrowest interface they need (e.g., `ConnectionStateProvider` for read-only
connection state). Handlers inject `ServiceStateWriter` for mutations. The full
`ServiceRepository` union is still available for backward compatibility.

Radio commands are issued through `RadioController` (a composite of `AdminController`,
`MessagingController`, `NodeController`, `QueryController`) rather than an action/intent bus.

### `NodeRepository`

Reactive mesh node database. Backed by Room KMP in `:core:data` (`NodeRepositoryImpl`).

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
  ├── api → core:model                   (exported to consumers)
  ├── api → org.meshtastic:protobufs     (Maven, exported to consumers)
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
