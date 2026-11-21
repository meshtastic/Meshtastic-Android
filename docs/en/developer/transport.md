---
title: Transport
parent: Developer Guide
nav_order: 5
last_updated: 2026-11-20
description: The BLE, USB serial, TCP and WebSocket transports behind the RadioTransport abstraction — which module owns each, and how to add a new one.
aliases:
  - ble
  - serial
  - tcp
  - websocket
  - radio-transport
---

# Transport

The app talks to the node over four transports (BLE, USB serial, TCP and WebSocket) behind one abstraction.

## Transport Abstraction

The app’s platform factories select links and handle host permissions. The shared session in
`core:data` runs one SDK client for the admitted generation and installs the radio snapshot in
Room. The SDK owns the wire handshake, heartbeat and goodbye. Demo and replay simulate the radio
side of that same protocol.

Every transport address starts with a one-character `InterfaceId`: `x` BLE (`!` is accepted too), `s`
serial, `t` TCP, `w` WebSocket (a `ws://` or `wss://` URL), `m` mock, `r` replay, and `n` for no device selected. `RadioTransportFactory.isAddressValid` checks that prefix
before a connection is attempted.

## Bluetooth Low Energy (BLE)

**Module:** `meshtastic-sdk:transport-ble`

Available on Android, desktop and Web Bluetooth. The app’s `core:ble` supplies discovery and host
permission state. On the web it passes the browser’s existing Kable grant to the SDK. On Android
the SDK handles pairing and GATT traffic and can refresh the cached service table after a firmware
update.

## USB Serial

**Module:** `core:network`  
**Platforms:** Android (OTG), Desktop, Web (Web Serial)

Serial communication over USB:
- Uses `usb-serial-for-android` library on Android
- Direct serial port access on Desktop (JVM)
- Probe table for supported USB vendor/product IDs
- Automatic detection when USB device is connected; on Web only for a port the user has already granted through the browser's port picker

Android and desktop use the SDK serial transports. On the web, `WebSerialRadioLink` opens
the granted browser streams and feeds the shared SDK frame assembler.

## TCP/IP

**Module:** `meshtastic-sdk:transport-tcp`

**Platforms:** Android, Desktop (iOS: code compiles, but there's no iOS app target or `RadioTransportFactory` — see [Transport Factory](#transport-factory))

Network-based transport for Wi-Fi-enabled nodes:
- TCP socket connection to the node's IP address
- Default port: 4403
- Nodes on the local network found through mDNS (`_meshtastic._tcp`): `NsdManager` on Android, JmDNS on Desktop
- Used for development with meshtasticd and simulated nodes
- Available when BLE/USB is impractical

## WebSocket

**Module:** `meshtastic-sdk:transport-websocket`

**Platforms:** Android, Desktop, Web

Firmware's WebSocket phone API carries one binary `ToRadio` or `FromRadio` per message at `/api/v1/ws`, for one client at a time. Addresses are `ws://` or `wss://` URLs, and an empty path becomes `/api/v1/ws` (`WebSocketUrl.kt`). Released firmware does not serve it yet. In a browser it is the only network transport.

The shared SDK session uses the same WebSocket factory on Android, desktop and Web. The app’s session service handles reselecting the link after a recoverable disconnect.

## Transport Factory

The `RadioTransportFactory` interface abstracts transport creation:

```kotlin
interface RadioTransportFactory {
    val supportedDeviceTypes: List<DeviceType>

    /** Whether the virtual demo transports (`m` mock / `r` replay) may be offered and bound right now. */
    val mockTransportEnabled: StateFlow<Boolean>

    /** Whether this build ships a packet capture to replay, rather than degrading to plain mock. */
    val isReplayTransportAvailable: Boolean

    fun createTransport(address: String, service: RadioInterfaceService): RadioTransport
    fun isAddressValid(address: String?): Boolean
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String
}
```

`mockTransportEnabled` is a flow, not a one-shot check: the Android Demo Mode gesture (five taps
on the Settings app-version row) unlocks the demo transports mid-session, and the device list has
to notice. Every consumer must read that one flow — the visibility path and the `isAddressValid`
admission path have to agree, or the demo entry appears and then refuses to connect.

`BaseRadioTransportFactory` (`core:network`, `commonMain`) holds the shared half: it creates
`BleRadioTransport` for BLE addresses and `WebSocketRadioTransport` for WebSocket ones, admits each address type in `isAddressValid`, and dispatches every other
address in `createPlatformTransport`: mock and no-device itself, and replay, TCP and serial through hooks a platform overrides. `NonWebRadioTransportFactory` (`nonWebMain`) supplies raw TCP for Android and Desktop. On hardware without Bluetooth LE it refuses BLE
addresses, and the Android factory refuses serial addresses on hardware with no USB host, so a saved
address restored onto such a device is kept but never armed.

Platform-specific implementations:
- **Android:** `AndroidRadioTransportFactory` (`core:network`, `androidMain`) supports BLE + USB + TCP; Demo Mode is on in debug builds and Firebase Test Lab, and in release builds once hidden features are unlocked
- **Desktop:** `DesktopRadioTransportFactory` (`desktopApp`) supports BLE (Kable) + USB + TCP; Demo Mode is a debug-build feature, and an `r` address opens the plain mock, and an `n` or unknown prefix opens the no-op transport like every other platform
- **Web:** `WasmJsRadioTransportFactory` (`core:network`, `wasmJsMain`) supports BLE (Kable over Web Bluetooth) + USB (Web Serial) + WebSocket. It refuses raw TCP addresses (`isRawTcpSupported = false`), since a browser opens no sockets, so its Network pane takes WebSocket addresses only
- **iOS:** Code compiles for BLE and TCP, but no `RadioTransportFactory` implementation exists and no iOS app ships


## Connection Lifecycle

1. **Discovery** — Find available nodes (BLE scan / USB detect / mDNS or a manually entered TCP address or WebSocket URL)
2. **Connection** — Establish link to selected node
3. **Handshake** — Exchange node info and configuration
4. **Active** — Normal message exchange
5. **Disconnection** — Clean teardown or error recovery

The app-level state is `ConnectionState` (`Disconnected`, `Connecting`, `Connected`, `DeviceSleep`), read
through `ConnectionStateProvider`. `MeshConnectionManager` writes `Connecting`, `DeviceSleep` and
`Disconnected` as it reconciles transport events with the node's sleep policy, and `MeshConfigFlowManager`
writes `Connected` once the handshake completes. UI code observes this state, never
`RadioInterfaceService.connectionState`.

## Adding a New Transport

1. Implement `RadioTransport` interface
2. Give it an `InterfaceId` prefix, admit it in `isAddressValid`, and create it in the platform's `createPlatformTransport`
3. Add connection UI in `feature:connections`
4. Update DI bindings for the platform
