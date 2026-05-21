# Data Model: Car App Library Integration

**Feature**: Car App Library Integration
**Date**: 2026-05-21

## Overview

The car module introduces **no new persistent entities**. All data is consumed from existing `core/` repositories. This document defines the **presentation state models** and **UI state containers** used within the car module to bridge repository data to CAL templates.

## Existing Entities (consumed, not modified)

### Node (core/model)
| Field | Type | Car Usage |
|-------|------|-----------|
| `num` | `Int` | Unique identifier, key for node DB |
| `user.id` | `String` | User ID (e.g., "!1234abcd") |
| `user.longName` | `String` | Display name in Condensed Items |
| `user.shortName` | `String` | Abbreviated name for compact views |
| `user.hwModel` | `HardwareModel` | Shown in node detail |
| `position.latitude` | `Double` | Map pin latitude |
| `position.longitude` | `Double` | Map pin longitude |
| `position.time` | `Int` | Last position update epoch |
| `lastHeard` | `Int` | Last communication epoch |
| `snr` | `Float` | Signal-to-noise ratio display |
| `deviceMetrics.batteryLevel` | `Int?` | Battery indicator |
| `isFavorite` | `Boolean` | Priority in node list |

### DataPacket (core/model)
| Field | Type | Car Usage |
|-------|------|-----------|
| `from` | `String` | Sender identifier |
| `to` | `String` | Destination identifier |
| `channel` | `Int` | Channel index for grouping |
| `bytes` | `ByteArray?` | Message content |
| `dataType` | `Int` | Message type classification |
| `time` | `Long` | Timestamp for display |
| `id` | `Int` | Unique packet ID |
| `status` | `MessageStatus` | Delivery status indicator |

### QuickChatAction (core/database)
| Field | Type | Car Usage |
|-------|------|-----------|
| `uuid` | `Long` | Unique ID |
| `name` | `String` | Display label for quick-reply button |
| `message` | `String` | Text to send when tapped |
| `mode` | `Int` | Instant vs append mode |
| `position` | `Int` | Sort order |

### MyNodeInfo (core/model)
| Field | Type | Car Usage |
|-------|------|-----------|
| `myNodeNum` | `Int` | Our node number |
| `firmwareVersion` | `String?` | Display in expanded status panel |
| `model` | `String?` | Hardware model display |

## Presentation State Models (new, car module only)

### CarSessionState

Top-level state for a car session lifecycle.

```kotlin
data class CarSessionState(
    val connectionStatus: ConnectionStatus,
    val onlineNodeCount: Int,
    val lastMessageTime: Long?,   // epoch millis, null if no messages
    val activeEmergencies: List<EmergencyAlert>,
    val meshName: String?,
)

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
}
```

**Source**: Derived from `BleConnectionState`, `NodeRepository.onlineNodeCount`, `PacketRepository`

### MessagingUiState

State for the messaging screen template builder.

```kotlin
data class MessagingUiState(
    val channels: List<ChannelUi>,
    val selectedChannelIndex: Int,
    val conversations: List<ConversationUi>,
    val emergencySpotlight: List<EmergencyAlert>?,
)

data class ChannelUi(
    val index: Int,
    val name: String,
    val unreadCount: Int,
)

data class ConversationUi(
    val contactKey: String,
    val displayName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isEmergency: Boolean,
)
```

**Source**: `PacketRepository.getContacts()`, `PacketRepository.getUnreadCountFlow()`, channel config from radio

### NodeDashboardUiState

State for the node dashboard condensed items grid.

```kotlin
data class NodeDashboardUiState(
    val nodes: List<NodeUi>,
    val topologyHeader: TopologyHeader,
)

data class NodeUi(
    val nodeNum: Int,
    val longName: String,
    val shortName: String,
    val signalQuality: SignalQuality,
    val batteryPercent: Int?,
    val isOnline: Boolean,
    val lastHeard: Long,
    val hasPosition: Boolean,
)

enum class SignalQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }

data class TopologyHeader(
    val totalNodes: Int,
    val onlineNodes: Int,
    val meshName: String?,
)
```

**Source**: `NodeRepository.nodeDBbyNum`, `NodeRepository.onlineNodeCount`

### MapUiState

State for the PlaceListMapTemplate.

```kotlin
data class MapUiState(
    val places: List<NodePlace>,
    val ownPosition: LatLngWrapper?,
)

data class NodePlace(
    val nodeNum: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdateTime: Long,
    val distanceMeters: Float?,  // from own position, null if own position unknown
)

data class LatLngWrapper(
    val latitude: Double,
    val longitude: Double,
)
```

**Source**: `NodeRepository.nodeDBbyNum` filtered to nodes with valid positions

### EmergencyAlert

Model for emergency messages requiring banner treatment.

```kotlin
data class EmergencyAlert(
    val packetId: Int,
    val senderName: String,
    val senderNodeNum: Int,
    val message: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val acknowledged: Boolean,
)
```

**Source**: `PacketRepository` flow filtered by emergency message type/priority

## State Transitions

### Car Session Lifecycle

```
[App Not Visible] → onCreateScreen() → [Active Session]
    ↓                                        ↓
    ↓                                    Screens pushed/popped via ScreenManager
    ↓                                        ↓
[App Not Visible] ← onDestroy() ← [Active Session]
```

### Connection Status

```
DISCONNECTED → (BLE scan + connect) → CONNECTING → (handshake complete) → CONNECTED
     ↑                                                                          |
     └──────────────────── (link lost / timeout) ──────────────────────────────┘
```

### Emergency Alert Flow

```
[Message received] → (priority == EMERGENCY?) → YES → Add to activeEmergencies
                                                         → Show Banner
                                                         → Play notification sound
                                               → NO  → Normal message flow
```

## Validation Rules

| Rule | Enforcement |
|------|-------------|
| Node name display ≤ 30 chars | Truncated by CAL host automatically |
| Message content ≤ 300 chars in list | Truncate with "…"; full on tap/TTS |
| Channel name ≤ 12 chars for Chip | Truncated with "…" |
| Max 6 conversations visible | CAL template item limit; paginate |
| Map pins require valid lat/lng | Filter nodes without position |
| Emergency banner requires non-empty message | Skip silent emergency packets |
