# Wire Protocol Contract: TAK v2 Protocol Integration

## Overview

The TAK v2 protocol defines two wire formats for transmitting Cursor-on-Target (CoT) events over the Meshtastic LoRa mesh network. This contract documents the encoding, port assignment, and interoperability guarantees.

---

## Port Assignments

| Port | Protobuf PortNum | Protocol | Firmware Requirement |
|------|-----------------|----------|---------------------|
| 72 | `ATAK_PLUGIN` | TAKPacket (v1) | Any version |
| 78 | `ATAK_PLUGIN_V2` | TAKPacketV2 (v2) | ≥ 2.8.0 |

---

## TAKPacketV2 Wire Format (Port 78)

### Byte Layout

```
Offset  Size    Field
0       1       Flags byte
1       N       Payload (compressed or raw TAKPacketV2 protobuf)

Total: 1 + N bytes, where (1 + N) ≤ 225 bytes
```

### Flags Byte Encoding

| Value | Meaning |
|-------|---------|
| 0x00 | Compressed with non-aircraft dictionary (ID 0) |
| 0x01 | Compressed with aircraft dictionary (ID 1) |
| 0x02-0xFE | Reserved for future dictionaries |
| 0xFF | Uncompressed (raw TAKPacketV2 protobuf, no zstd) |

### Compression

- **Algorithm**: Zstd (Zstandard) with pre-trained dictionaries
- **Dictionary selection**: Based on CoT type — aircraft types (`a-*-A-*`) use dict 1, all others use dict 0
- **Library**: `meshtastic/TAKPacket-SDK` v0.1.3 (wraps zstd-jni)
- **Max decompressed size**: 4096 bytes (reject larger to prevent memory exhaustion)

### TAKPacketV2 Protobuf Schema

The protobuf is defined upstream in `meshtastic/protobufs`. Key payload types:

| Payload Type | CoT Types Covered |
|-------------|-------------------|
| `pli` | All `a-*` position reports (friendly, hostile, unknown, neutral) |
| `chat` | `b-t-f` GeoChat messages |
| `raw_detail` | All other CoT types (shapes, markers, routes, etc.) |

#### PLI Payload Fields

| Field | Type | Scaling | Notes |
|-------|------|---------|-------|
| latitude_i | int32 | × 1e7 | WGS84 degrees |
| longitude_i | int32 | × 1e7 | WGS84 degrees |
| altitude | int32 | meters | HAE |
| speed | uint32 | × 100 | m/s → cm/s |
| course | uint32 | × 100 | degrees → centidegrees |
| cot_type_id | enum | — | Mapped CoT type |
| cot_type_str | string | — | Original type for round-trip (CotType_Other) |

#### GeoChat Payload Fields

| Field | Type | Notes |
|-------|------|-------|
| message | string | Chat message text |
| to | string | Recipient UID or chatroom name |
| device_callsign | string | `<senderUid>\|<messageId>` (UID smuggling) |

#### Raw Detail Payload

| Field | Type | Notes |
|-------|------|-------|
| raw_detail | bytes | Stripped inner `<detail>` XML (16 elements removed) |

---

## TAKPacket Wire Format (Port 72, Legacy)

### Byte Layout

```
Offset  Size    Field
0       N       Raw TAKPacket protobuf (no header, no compression)
```

### Supported Payload Types (v1)

| Type | Support |
|------|---------|
| PLI (position) | ✅ Full |
| GeoChat | ✅ Full |
| Markers/Shapes/Routes | ❌ Not representable in v1 schema |

---

## TAK Server Protocol (Port 8089)

### Transport

- **Protocol**: TCP with TLS 1.2+ (mTLS required)
- **Port**: 8089 (configurable via TAK server standard)
- **Binding**: `127.0.0.1` (loopback) + LAN interfaces
- **Authentication**: Mutual TLS — server presents `server.p12`, client presents `client.p12`, both trust `ca.pem`

### Message Framing

CoT events are sent as complete XML documents over the TLS stream:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<event version="2.0" uid="..." type="..." time="..." start="..." stale="..." how="...">
  <point lat="..." lon="..." hae="..." ce="..." le="..."/>
  <detail>
    <!-- Type-specific detail elements -->
  </detail>
</event>
```

Events are framed by detecting the closing `</event>` tag via `CoTXmlFrameBuffer`.

### Keepalive

- **Interval**: 10 seconds
- **Format**: Empty ping CoT event (`t-x-d-d` type)
- **Purpose**: Maintain connection below ATAK's 15-second stale threshold

---

## Data Package Format (.zip)

### Connection Data Package

Exported for ATAK/iTAK client configuration:

```
Meshtastic_TAK_Server.zip
├── meshtastic-server.pref    # ATAK connection preferences (XML)
├── truststore.p12            # CA certificate for server verification
├── client.p12                # Client identity for mTLS
└── manifest.xml              # MissionPackageManifest v2
```

### Route Data Package

Generated on receiving end for ATAK route import:

```
{route_uid}.zip
├── {route_uid}.kml           # KML LineString with waypoints
└── manifest.xml              # MissionPackageManifest v2
```

---

## Inbound Processing Rules

| Source Port | Local Firmware | Action |
|------------|---------------|--------|
| 78 (V2) | Any | Decompress → decode → broadcast to TAK clients |
| 72 (V1) | Any | Decode raw protobuf → broadcast to TAK clients |
| 78 (V2) + route type | Any | Decompress → decode → generate KML → broadcast + write file |

---

## Outbound Processing Rules

| CoT Type | Local Firmware ≥ 2.8.0 | Local Firmware < 2.8.0 |
|----------|----------------------|----------------------|
| PLI (`a-*`) | V2 (port 78, compressed) | V1 (port 72, raw proto) |
| GeoChat (`b-t-f`) | V2 (port 78, compressed) | V1 (port 72, raw proto) |
| All others | V2 (port 78, compressed) | **DROPPED** (log warning) |

### MTU Enforcement

- Max wire payload: 225 bytes (after protobuf framing within 237-byte LoRa MTU)
- If compressed payload exceeds 225 bytes: attempt remarks stripping, then drop with warning
- Never fragment; never queue oversized packets
