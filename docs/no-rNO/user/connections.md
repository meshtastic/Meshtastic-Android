---
title: Connections
parent: User Guide
nav_order: 2
last_updated: 2026-05-20
description: Connect your phone or desktop to a Meshtastic radio via Bluetooth, USB, or TCP/IP.
aliases:
  - bluetooth
  - usb
  - tcp
  - pairing
---

# Connections

Meshtastic supports multiple transport methods to communicate between your phone/desktop and a radio node.

## Bluetooth (BLE)

Bluetooth Low Energy is the default and most common connection method on Android.

### Pairing a Device

1. Ensure your Meshtastic radio is powered on and in pairing mode.
2. Open the app and navigate to the **Connect** tab.
3. Tap **Scan for Devices** — nearby Meshtastic radios will appear.
4. Select your device from the list.
5. Accept the Bluetooth pairing prompt if shown.

![Device list item](../../assets/screenshots/connections_bluetooth_scan.png)

You can filter devices by transport type using the filter chips at the top:

![Transport filter chips](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Tip:** If your device doesn't appear, check that Bluetooth and Location permissions are granted, and that the radio is not already connected to another device.

### Connection Status

| Icon | State          | Beskrivelse                   |
| ---- | -------------- | ----------------------------- |
| 🟢   | Connected      | Active radio link established |
| 🟡   | Connecting     | Handshake in progress         |
| 🔴   | Frakoblet      | No active connection          |
| ⚪    | Not configured | No device selected            |

When connecting, a status indicator shows the current connection state:

![Connecting status](../../assets/screenshots/connections_connecting.png)

If no devices are found, the app shows an empty state with instructions:

![No devices found](../../assets/screenshots/connections_empty_state.png)

### Troubleshooting Bluetooth

- **Device not found:** Toggle Bluetooth off/on, ensure location is enabled.
- **Connection drops:** Move closer to the radio; check for interference.
- **Pairing rejected:** Forget the device in Android Bluetooth settings and retry.

## USB Serial

USB connections provide a wired alternative, useful for desktop or when Bluetooth is unavailable.

### Setup

1. Connect your radio via USB cable to your device.
2. The app will prompt for USB permission — tap **Allow**.
3. The connection is established automatically.

> ⚠️ **Note:** USB connections require OTG support on Android devices.

## TCP/IP (WiFi)

Some Meshtastic radios support WiFi connectivity, allowing TCP-based connections.

### Configuration

1. Connect your radio to a WiFi network via the radio's web interface or settings.
2. In the app, go to **Connect → TCP**.
3. Enter the radio's IP address and port (default: 4403).
4. Tap **Connect**.

![WiFi scanning for devices](../../assets/screenshots/connections_wifi_scanning.png)

When a device is found, it appears in the connection list:

![WiFi device found](../../assets/screenshots/connections_wifi_device_found.png)

A successful connection is confirmed with a status indicator:

![WiFi connection success](../../assets/screenshots/connections_wifi_success.png)

### When to Use TCP

- Radio is on the same local network
- Testing with a simulated radio
- Environments where Bluetooth has interference issues

## Reconnection Behavior

The app reconnects to the **last selected device** on startup. You can switch transports from the Connect screen at any time.

To disconnect, tap the disconnect button on the Connect screen:

![Disconnect from radio](../../assets/screenshots/connections_disconnect.png)

## Desktop Connections

On Desktop (Linux/macOS/Windows), the app supports:

- **Bluetooth (BLE)** — via the Kable library; works on macOS, Linux, and Windows
- **USB Serial** — primary wired connection method
- **TCP/IP** — for network-connected radios

See [Desktop App](desktop) for platform-specific details and keyboard shortcuts.

## Related Topics

- [Getting Started](onboarding) — first-launch setup and permissions
- [Settings — Radio & User](settings-radio-user) — Bluetooth and network configuration
- [Desktop App](desktop) — desktop-specific connection details
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — full list of compatible radios on meshtastic.org

---

