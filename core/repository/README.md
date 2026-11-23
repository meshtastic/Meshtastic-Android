# `:core:repository`

## Overview

The `:core:repository` module defines the **data and infrastructure contracts** for the Meshtastic KMP architecture. It is almost entirely interfaces — concrete implementations live in `:core:data`, `:core:service`, and platform modules. Consumers receive `:core:model` and the `org.meshtastic:protobufs` Maven artifact transitively because both are `api()`-exported.

**Targets:** Android · JVM · iOS (via `meshtastic.kmp.library` convention plugin)

## Key Responsibilities

- Define the reactive data contracts between the long-running mesh service and all feature/UI layers
- Declare the per-link transport contract (`RadioTransport`) over the SDK PhoneAPI session
- Provide the mesh node database interface (`NodeRepository`)
- Expose the active radio session's SDK admin API and per-node admin session status (`RadioInterfaceService.adminForSession`, `adminSessionStatus`)
- Declare the packet handler contracts (admin, telemetry, traceroute, store-and-forward, neighbor info, remote shell)
- Declare the outbound message queue and MQTT proxy contracts, and expose the active session's SDK client (`RadioInterfaceService.clientForSession`)
- Declare the app preference interfaces (`AppPreferences.kt`) that `:core:prefs` implements
- Provide the `AppWidgetUpdater` contract so the mesh service can trigger widget refreshes without depending on Android widget APIs directly

## Source Structure

```text
src/
├── commonMain/kotlin/org/meshtastic/core/repository/
│   ├── RadioTransport.kt              ← interface: raw hardware I/O
│   ├── ServiceRepository.kt           ← interface: service ↔ UI bridge (extends all providers)
│   ├── ConnectionStateProvider.kt     ← interface: read-only connection state
│   ├── ResponseProviders.kt           ← interfaces: TracerouteResponseProvider, NeighborInfoResponseProvider
│   ├── ServiceStateWriter.kt          ← interface: write-side for handlers
│   ├── NodeRepository.kt              ← interface: mesh node database
│   ├── MeshConnectionManager.kt       ← interface: connection lifecycle callbacks
│   ├── AppWidgetUpdater.kt            ← interface: trigger widget refresh
│   ├── LocationRepository.kt
│   ├── LocationService.kt
│   ├── RadioController.kt             ← interface: composite radio command API
│   ├── AdminController.kt             ← config, channels, owner, device lifecycle, editSettings
│   ├── MessagingController.kt         ← send packets, reactions, contacts
│   ├── NodeController.kt              ← favorite, ignore, mute, remove nodes
│   ├── QueryController.kt             ← telemetry, traceroute, position, neighbor info queries
│   ├── AppPreferences.kt              ← UiPrefs, MapPrefs, RadioPrefs, TakPrefs and the other prefs interfaces
│   ├── *Repository.kt                 ← firmware, hardware, device links, discovery, mesh beacon, …
│   ├── MeshConfigFlowManager.kt
│   ├── MeshConfigHandler.kt
│   ├── MeshDataHandler.kt
│   ├── MeshLocationManager.kt
│   ├── MeshLogRepository.kt
│   ├── MessageFilter.kt
│   ├── MessageQueue.kt
│   ├── MqttManager.kt
│   ├── NeighborInfoHandler.kt
│   ├── NodeManager.kt
│   ├── NodeRequest.kt                 ← a sent node request and the routing error that failed it
│   ├── Notification.kt / NotificationManager.kt
│   ├── PacketHandler.kt / PacketRepository.kt
│   ├── QuickChatActionRepository.kt
│   ├── RadioConfigRepository.kt
│   ├── RadioInterfaceService.kt
│   ├── RadioSessionContext.kt
│   ├── RadioTransportCallback.kt / RadioTransportFactory.kt
│   ├── StoreForwardPacketHandler.kt
│   ├── TelemetryPacketHandler.kt
│   ├── TracerouteHandler.kt / TracerouteSnapshotRepository.kt
│   ├── usecase/
│   │   └── SendMessageUseCase.kt
│   └── di/
│       └── CoreRepositoryModule.kt
└── commonTest/kotlin/
```

The module has no platform source sets. Platform implementations such as the Android `LocationRepositoryImpl` live in `:core:data` and the app hosts.

## Core Interfaces

### `RadioTransport`

One radio link (BLE, USB, TCP, WebSocket, demo) and the SDK PhoneAPI session that runs over it. Every member but
`close` has a default, so the inert transports (`NopRadioTransport`, the USB-unavailable placeholder) override nothing
else.

```kotlin
interface RadioTransport {
    val admin: AdminApi?            // the session's SDK admin API
    val client: RadioClient?        // the session's SDK client
    fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus>
    fun sendPaced(packet: MeshPacket): PacedMessageHandle?
    fun tryLockdown(auth: LockdownAuth): Boolean
    fun requestNodeDbRefresh(): Boolean
    suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean
    fun start() {}
    suspend fun close()
}
```

`RadioInterfaceService` admits work to the active transport under a per-session gate: `sendPaced`, `tryLockdown` and
`deliverMqttProxy` run only while their session still owns the link, and teardown drains an admitted handoff before it
closes the transport.

### `ServiceRepository`

The primary reactive bridge between the long-running mesh service and all feature/UI layers.
Decomposed into focused sub-interfaces via Interface Segregation Principle:

```kotlin
interface ConnectionStateProvider {
    val connectionLifecycle: StateFlow<ConnectionLifecycle>
    val connectionState: StateFlow<ConnectionState>
    val connectionEpochs: StateFlow<ConnectionEpochs>
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
    fun setConnectionState(connectionState: ConnectionState)
    suspend fun emitMeshPacket(packet: MeshPacket)
    fun setTracerouteResponse(value: TracerouteResponse?)
    fun setNeighborInfoResponse(value: String?)
    // …setters/clearers for error and notification
}

interface ServiceRepository :
    ConnectionStateProvider,
    TracerouteResponseProvider,
    NeighborInfoResponseProvider,
    ServiceStateWriter {
    val clientNotification: StateFlow<ClientNotification?>
    val errorMessage: StateFlow<String?>
    val meshPacketFlow: Flow<MeshPacket>
    // …plus lockdown state, lockdown token info and sessionAuthorized, with their setters
}
```

VMs inject the narrowest interface they need (e.g., `ConnectionStateProvider` for read-only
connection state). Handlers inject `ServiceStateWriter` for mutations. The full
`ServiceRepository` union serves code that needs both sides.

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
    suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>): List<Int>
}
```

## Dependency Graph

```text
core:repository
  ├── api → core:model                   (exported to consumers)
  ├── api → org.meshtastic:protobufs     (Maven, exported to consumers)
  ├── core:common
  ├── core:database
  └── kotlinx.coroutines, atomicfu, kermit, androidx.paging.common
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
