# Data Model: TAK v2 Protocol Integration

## Core Entities

### CoTMessage (Central Domain Model)

The primary data model flowing between TAK clients, the TAK server, and the mesh network.

```kotlin
@Serializable
data class CoTMessage(
    val uid: String,                    // Unique event ID (e.g., "ANDROID-device-uuid")
    val type: String,                   // CoT type string (e.g., "a-f-G-U-C", "b-t-f")
    val time: Instant,                  // Event creation time
    val start: Instant,                 // Event validity start
    val stale: Instant,                 // Event expiry time
    val how: String,                    // How generated (e.g., "m-g", "h-e")
    val latitude: Double,              // WGS84 latitude
    val longitude: Double,             // WGS84 longitude
    val hae: Double,                   // Height above ellipsoid (meters)
    val ce: Double,                    // Circular error (meters)
    val le: Double,                    // Linear error (meters)
    val contact: CoTContact?,          // Contact info (callsign, endpoint)
    val group: CoTGroup?,              // Team/role info
    val status: CoTStatus?,            // Battery status
    val track: CoTTrack?,              // Speed/course
    val chat: CoTChat?,                // Chat routing info
    val remarks: String?,              // Free-text remarks
    val rawDetailXml: String?,         // Preserved inner <detail> XML for raw_detail
    val parsedDetailXml: CoTDetailXml?, // Parsed structured detail
    val sourceEventXml: String?,       // Full source XML for round-trip fidelity
)
```

### Supporting Detail Models

```kotlin
@Serializable data class CoTContact(val callsign: String?, val endpoint: String?)
@Serializable data class CoTGroup(val name: String?, val role: String?)
@Serializable data class CoTStatus(val battery: Int?)
@Serializable data class CoTTrack(val speed: Double?, val course: Double?)
@Serializable data class CoTChat(val chatroom: String?, val id: String?, val senderCallsign: String?)
```

### TAKClientInfo

Tracks connected TAK client state.

```kotlin
data class TAKClientInfo(
    val uid: String?,                  // Client's self-reported UID
    val callsign: String?,             // Client's callsign (from first PLI)
    val connectionTime: Instant,       // When client connected
)
```

### InboundCoTMessage

Wraps a CoT message with its source client info for routing decisions.

```kotlin
data class InboundCoTMessage(
    val cotMessage: CoTMessage,
    val clientInfo: TAKClientInfo?,    // null if from mesh (not local TAK client)
)
```

---

## Enum Mappings

### CotType (23 mapped values + fallback)

| Enum Value | CoT String | Category |
|-----------|------------|----------|
| CotType_PLI_Friendly | "a-f-G-U-C" | Position (friendly ground) |
| CotType_PLI_Hostile | "a-h-G" | Position (hostile) |
| CotType_PLI_Unknown | "a-u-G" | Position (unknown) |
| CotType_PLI_Neutral | "a-n-G" | Position (neutral) |
| CotType_Aircraft_Friendly | "a-f-A" | Aircraft |
| CotType_Aircraft_Military | "a-f-A-M" | Aircraft (military) |
| CotType_Aircraft_Helicopter | "a-f-A-M-H" | Aircraft (helicopter) |
| CotType_Aircraft_FixedWing | "a-f-A-C-F" | Aircraft (fixed wing) |
| CotType_GeoChat | "b-t-f" | Chat |
| CotType_DrawnShape | "u-d-f" | Drawing |
| CotType_Route | "b-m-r" | Route/Navigation |
| CotType_Marker | "b-m-p-s-p-i" | Marker (spot) |
| CotType_Waypoint | "b-m-p-s-p-loc" | Waypoint |
| CotType_Delete | "t-x-d-d" | Control |
| CotType_Casevac | "b-r-f-h-c" | Medical |
| CotType_Emergency | "b-a-o-pan" | Emergency |
| CotType_Alert | "b-a-o-opn" | Alert |
| CotType_Task | "b-i-v" | Tasking |
| ... (+ additional variants) | | |
| CotType_Other | *(any unrecognized)* | Fallback |

### CotHow (4 mapped values)

| Enum Value | String | Meaning |
|-----------|--------|---------|
| HOW_HUMAN_ENTERED | "h-e" | Human-entered |
| HOW_MACHINE_GENERATED | "m-g" | Machine-generated |
| HOW_HUMAN_OSINT | "h-g-i-g-o" | Human-generated OSINT |
| HOW_MACHINE_REPORTED | "m-r" | Machine-reported |

---

## State Machines

### TAK Server Lifecycle

```
     ┌──────────┐
     │  STOPPED │
     └────┬─────┘
          │ start()
          ▼
     ┌──────────┐
     │ STARTING │ (bind port 8089, load certs)
     └────┬─────┘
          │ success
          ▼
     ┌──────────┐       accept()      ┌────────────────┐
     │ RUNNING  │◄────────────────────│ CLIENT_CONNECTED│
     │(0 clients)│                     │(n clients)      │
     └────┬─────┘                     └───────┬─────────┘
          │ stop()                            │ stop() / all disconnect
          ▼                                   ▼
     ┌──────────┐
     │  STOPPED │
     └──────────┘
```

### TAK Client Connection Lifecycle

```
     ┌──────────────┐
     │  CONNECTED   │ (mTLS handshake complete)
     └──────┬───────┘
            │ first PLI received
            ▼
     ┌──────────────┐
     │  IDENTIFIED  │ (callsign + UID known)
     └──────┬───────┘
            │ socket error / timeout / client close
            ▼
     ┌──────────────┐
     │ DISCONNECTED │ (scope cancelled, removed from list)
     └──────────────┘
```

### Protocol Version Selection (per-send)

```
     ┌─────────────────────────────┐
     │ CoT message from TAK client │
     └──────────────┬──────────────┘
                    │
              ┌─────▼──────┐
              │ useTakV2()?│
              └──┬─────┬───┘
           yes   │     │  no
                 ▼     ▼
     ┌───────────┐   ┌───────────┐
     │ V2 Path   │   │ V1 Path   │
     │ (port 78) │   │ (port 72) │
     └─────┬─────┘   └─────┬─────┘
           │                │
     ┌─────▼─────┐   ┌─────▼─────┐
     │All CoT    │   │PLI + Chat │
     │types OK   │   │only; else │
     │           │   │drop+warn  │
     └─────┬─────┘   └─────┬─────┘
           │                │
     ┌─────▼─────┐   ┌─────▼─────┐
     │Compress   │   │Bare proto │
     │(zstd+dict)│   │(no compr.)│
     └─────┬─────┘   └─────┬─────┘
           │                │
     ┌─────▼─────┐   ┌─────▼─────┐
     │Check MTU  │   │Send on    │
     │≤225 bytes │   │port 72    │
     │else drop  │   └───────────┘
     └─────┬─────┘
           │
     ┌─────▼─────┐
     │Send on    │
     │port 78    │
     └───────────┘
```

---

## Validation Rules

| Field | Rule | Error Handling |
|-------|------|----------------|
| Compressed payload size | ≤ 225 bytes | Drop packet, log warning |
| Decompressed payload size | ≤ MAX_DECOMPRESSED_SIZE (4096) | Reject packet (memory exhaustion prevention) |
| CoT XML structure | Valid `<event>` root with `<point>` | Parse returns `Result.failure` |
| Latitude/Longitude | -90/90 and -180/180 respectively | Passed through (ATAK validates) |
| Stale time | Must be in future (or extended to MIN_MESH_STALE_TTL for static types) | Extended automatically for routes/shapes |
| Offline queue | ≤ 50 messages, ≤ 5 min TTL | Oldest evicted on overflow; expired purged on drain |
| Port 8089 binding | Must succeed | Server start returns `Result.failure` with user-visible error |

---

## Wire Format

### TAKPacketV2 (Port 78)

```
┌─────────┬────────────────────────────────┐
│ Flags   │ Compressed TAKPacketV2 Protobuf │
│ (1 byte)│ (variable, ≤224 bytes)          │
└─────────┴────────────────────────────────┘

Flags byte:
  Bits 0-5: Dictionary ID (0=non-aircraft, 1=aircraft)
  Bits 6-7: Reserved
  0xFF: Uncompressed (raw protobuf follows)
```

### TAKPacket (Port 72, Legacy)

```
┌──────────────────────────────┐
│ Raw TAKPacket Protobuf       │
│ (no compression, no header)  │
└──────────────────────────────┘
```

---

## Key Constants

```kotlin
const val DEFAULT_TAK_PORT = 8089
const val MAX_TAK_WIRE_PAYLOAD_BYTES = 225
const val MAX_DECOMPRESSED_SIZE = 4096
const val TAK_COORDINATE_SCALE = 1e7        // lat/lon → int scaling
const val DICT_ID_NON_AIRCRAFT = 0
const val DICT_ID_AIRCRAFT = 1
const val DICT_ID_UNCOMPRESSED = 0xFF
val OFFLINE_QUEUE_TTL = 5.minutes
const val OFFLINE_QUEUE_MAX_SIZE = 50
val KEEPALIVE_INTERVAL = 10.seconds
val MIN_MESH_STALE_TTL = 15.minutes
```
