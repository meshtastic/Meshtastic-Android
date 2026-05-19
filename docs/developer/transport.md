---
title: Transport
nav_order: 5
last_updated: 2026-05-13
aliases:
  - ble
  - serial
  - tcp
  - radio-transport
---

# Transport

Meshtastic communicates between the app and radio hardware through multiple transport mechanisms.

## Transport Abstraction

The transport layer is abstracted through interfaces in `core/network` and `core/ble`, allowing the app to work identically regardless of the underlying connection type.

```
App ← RadioController → Transport (BLE | Serial | TCP)
```

## Bluetooth Low Energy (BLE)

**Module:** `core:ble`  
**Platforms:** Android, (planned: iOS)

The primary transport for Android mobile devices:
- Service discovery for Meshtastic GATT services
- Characteristic-based read/write for protobuf packets
- Connection state management and automatic reconnection
- MTU negotiation for optimal packet sizes

### Key Classes

- `core/ble/` — BLE scanning, connection, and GATT operations
- Platform-specific implementations in `androidMain`

## USB Serial

**Module:** `core:network`  
**Platforms:** Android (OTG), Desktop

Serial communication over USB:
- Uses `usb-serial-for-android` library on Android
- Direct serial port access on Desktop (JVM)
- Probe table for supported USB vendor/product IDs
- Automatic detection when USB device is connected

### Key Classes

- Serial prober and transport factory in `core/network`
- Desktop-specific serial in `desktop/src/main/kotlin/.../radio/`

## TCP/IP

**Module:** `core:network`  
**Platforms:** Android, Desktop, iOS

Network-based transport for WiFi-enabled radios:
- TCP socket connection to radio's IP address
- Default port: 4403
- Used for development with simulated radios
- Available when BLE/USB is impractical

## Transport Factory

The `RadioTransportFactory` interface abstracts transport creation:

```kotlin
interface RadioTransportFactory {
    fun createTransport(config: TransportConfig): RadioTransport
}
```

Platform-specific implementations:
- **Android:** Supports BLE + USB + TCP
- **Desktop:** Supports USB + TCP (no BLE)
- **iOS:** Planned BLE + TCP

## Connection Lifecycle

1. **Discovery** — Scan for available radios (BLE scan / USB detect / manual TCP)
2. **Connection** — Establish link to selected radio
3. **Handshake** — Exchange node info and configuration
4. **Active** — Normal message exchange
5. **Disconnection** — Clean teardown or error recovery

## Adding a New Transport

1. Implement `RadioTransport` interface
2. Register in platform-specific `RadioTransportFactory`
3. Add connection UI in `feature:connections`
4. Update DI bindings for the platform

---

