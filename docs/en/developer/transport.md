---
title: Transport
parent: Developer Guide
nav_order: 5
last_updated: 2026-11-23
description: The BLE, USB serial, TCP and WebSocket transports behind the RadioLinks abstraction — which module owns each, and how to add a new one.
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

The app's platform `RadioLinks` build one SDK `RadioClient` for the selected radio and handle host
permissions. The SDK client owns connecting, the wire handshake, heartbeat, backoff and reconnecting.
The session pipeline in `core:data` writes each SDK session's snapshot and packets into Room. Demo and
replay simulate the radio side of that same protocol.

Every transport address starts with a one-character `InterfaceId`: `x` BLE (`!` is accepted too), `s`
serial, `t` TCP, `w` WebSocket (a `ws://` or `wss://` URL), `m` mock, `r` replay, and `n` for no device selected. `RadioLinks.isAddressValid` checks that prefix
before a connection is attempted.

## Bluetooth Low Energy (BLE)

**Module:** `meshtastic-sdk:transport-ble`

Available on Android, desktop and Web Bluetooth. The app’s `core:ble` supplies discovery and host
permission state. On the web it passes the browser’s existing Kable grant to the SDK. On Android
the SDK handles pairing and GATT traffic and can refresh the cached service table after a firmware
update.

## USB Serial

**Module:** `meshtastic-sdk:transport-serial`  
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

**Platforms:** Android, Desktop (a browser opens no raw sockets, so the web uses WebSocket instead)

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

Android, desktop and Web share one WebSocket link. The SDK keeps the quick-drop history that detects a contested slot, and stops with `ConnectionState.Failed` on a close that only the user's next connect should undo (4001 replaced by another client, 1009 message too big, a contested slot).

## Radio Links

The `RadioLinks` interface (`core:repository`) admits addresses and builds the selected radio's client:

```kotlin
interface RadioLinks {
    val supportedDeviceTypes: List<DeviceType>

    /** Whether the virtual demo radios (`m` mock / `r` replay) may be offered and opened right now. */
    val mockTransportEnabled: StateFlow<Boolean>

    /** Whether this build ships a packet capture to replay, rather than degrading to plain mock. */
    val isReplayTransportAvailable: Boolean

    fun isAddressValid(address: String?): Boolean
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String
    fun open(address: String, scope: CoroutineScope): RadioLink?
}
```

`mockTransportEnabled` is a flow, not a one-shot check: the Android Demo Mode gesture (five taps
on the Settings app-version row) unlocks the demo radios mid-session, and the device list has
to notice. Every consumer must read that one flow — the visibility path and the `isAddressValid`
admission path have to agree, or the demo entry appears and then refuses to connect.

`BaseRadioLinks` (`core:data`, `commonMain`) admits each address type and dispatches on the prefix.
Each link builds its client with `RadioClient.Builder.transport(identity, provider)` and the reconnect
schedule in `radioReconnectConfig`: a first retry after 1 s (3 s on BLE, which waits for firmware to
free its GATT session), backoff capped at 30 s, a link counted stable after 30 s (5 s on BLE), and three
handshake restarts in a row before the client stops. The provider the SDK calls before each attempt
decides what that attempt opens:

- BLE returns one `BleTransport`, built with `BleResilience` (bond first, auto-connect fallback, stale-cache refresh and a refresh after six failures in a row), and waits for Bluetooth to be on
- TCP and WebSocket return one transport; a TCP retry waits for a network
- Android USB serial opens the port afresh each attempt, since a rebooting radio re-enumerates, and waits for the device and for Android's permission, asking for it once
- Desktop serial waits for the port to exist, then opens it afresh
- Web Bluetooth and Web Serial open the device the user already granted
- Mock and replay build a fresh virtual radio each attempt

An `n` or unknown prefix opens nothing. On hardware without Bluetooth LE BLE addresses are refused, and
Android refuses serial addresses on hardware with no USB host, so a saved address restored onto such a
device is kept but never armed.

Platform implementations, all in `core:data`:
- **Android:** `AndroidRadioLinks` (`androidMain`) supports BLE + USB + TCP + WebSocket; Demo Mode is on in debug builds and Firebase Test Lab, and in release builds once hidden features are unlocked
- **Desktop:** `DesktopRadioLinks` (`jvmMain`) supports BLE + USB + TCP + WebSocket; Demo Mode is a debug-build feature, and an `r` address opens the plain mock
- **Web:** `WebRadioLinks` (`wasmJsMain`) supports BLE (Kable over Web Bluetooth) + USB (Web Serial) + WebSocket. It refuses raw TCP addresses (`isRawTcpSupported = false`), since a browser opens no sockets, so its Network pane takes WebSocket addresses only
- **iOS:** The app has no iOS radio links, and no iOS app ships

## Connection Lifecycle

`SharedRadioInterfaceService` (`core:service`) holds the selected radio's link. It connects and
disconnects the client, and calls `retryNow()` to end a backoff early when Bluetooth, the network or the
selected USB device comes back. A firmware-maintenance `RadioOperation` closes the link and its end
reopens it. A post-update GATT refresh goes to the next BLE link through `refreshGattCacheOnNextConnect()`.

Every SDK session becomes an app session, with a generation that keys the session leases database writes
hold; teardown drains those leases before the next session is admitted.

UI code observes `ConnectionStateProvider.connectionState`, which is the SDK's own `ConnectionState`:
`Connecting` and `Configuring` while a session comes up, `Connected`, `Reconnecting` with its cause and
the time of the next attempt, `DeviceSleeping` when a power-saving node drops its BLE link by design,
`Failed` once the client stops trying, and `Disconnecting` then `Disconnected` after the user disconnects.
The service publishes the client's states, except `Connected`, which `MeshConfigFlowManager` publishes once
the session's config and nodes are installed in Room.

## Adding a New Transport

1. Add the link to the SDK as a transport module; the SDK owns framing and the PhoneAPI session
2. Give it an `InterfaceId` prefix, admit it in `isAddressValid`, and open a link over it in `BaseRadioLinks.open` with a provider for each attempt
3. Add connection UI in `feature:connections`
4. Update DI bindings for the platform
