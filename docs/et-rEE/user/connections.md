---
title: Ühendus
parent: User Guide
nav_order: 2
last_updated: 2026-05-20
description: Ühenda oma telefon või lauaarvuti Meshtastic raadioga Bluetoothi, USB või TCP/IP kaudu.
aliases:
  - sinihammas
  - usb
  - tcp
  - seon
---

# Ühendus

Meshtastic toetab mitut transpordimeetodit telefoni/töölaua ja raadiosõlme vaheliseks suhtluseks.

## Sinihammas (BLE)

Bluetooth Low Energy is the default and most common connection method on Android.

### Seadme sidumine

1. Veendu, et Meshtastic seade on sisse lülitatud ja sidumisrežiimis.
2. Open the app and navigate to the **Connect** tab.
3. Puuduta **Skanni seadmeid** – kuvatakse lähedalasuvad Meshtastic raadiod.
4. Select your device from the list.
5. Nõustu Bluetoothi ​​sidumise taotlusega, kui see kuvatakse.

![Device list item](../../assets/screenshots/connections_bluetooth_scan.png)

You can filter devices by transport type using the filter chips at the top:

![Transport filter chips](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Vihje:** Kui sinu seadet ei kuvata, kontrolli, kas sinihamba ​​ja asukoha load on antud ning et raadio poleks juba teise seadmega ühendatud.

### Connection Status

| Icon | State            | Kirjeldus                     |
| ---- | ---------------- | ----------------------------- |
| 🟢   | Ühendatud        | Active radio link established |
| 🟡   | Ühendan          | Handshake in progress         |
| 🔴   | Ühendus katkenud | No active connection          |
| ⚪    | Not configured   | Seadet pole valitud           |

When connecting, a status indicator shows the current connection state:

![Connecting status](../../assets/screenshots/connections_connecting.png)

If no devices are found, the app shows an empty state with instructions:

![No devices found](../../assets/screenshots/connections_empty_state.png)

### Troubleshooting Bluetooth

- **Device not found:** Toggle Bluetooth off/on, ensure location is enabled.
- **Connection drops:** Move closer to the radio; check for interference.
- **Sidumine tagasi lükatud:** Unusta seade Androidi sinihamba ​​seadetes ja proovi uuesti.

## USB port

USB connections provide a wired alternative, useful for desktop or when Bluetooth is unavailable.

### Setup

1. Connect your radio via USB cable to your device.
2. Rakendus küsib USB luba – puuduta **Luba**.
3. The connection is established automatically.

> ⚠️ **Märkus:** USB ühenduste jaoks on Android-seadmetes vaja OTG tuge.

## TCP/IP (WiFi)

Mõned Meshtastic raadiod toetavad WiFi ühendust, mis võimaldab TCP põhiseid ühendusi.

### Sätted

1. Ühenda raadio WiFi võrguga raadio veebiliidese või sätete kaudu.
2. In the app, go to **Connect → TCP**.
3. Enter the radio's IP address and port (default: 4403).
4. Puuduta **Ühenda**.

![WiFi seadmete otsimine](../../assets/screenshots/connections_wifi_scanning.png)

When a device is found, it appears in the connection list:

![WiFi seade leitud](/assets/screenshots/connections_wifi_device_found.png)

A successful connection is confirmed with a status indicator:

![WiFi ühendus õnnestus](../../assets/screenshots/connections_wifi_success.png)

### When to Use TCP

- Radio is on the same local network
- Testing with a simulated radio
- Environments where Bluetooth has interference issues

## Reconnection Behavior

The app reconnects to the **last selected device** on startup. You can switch transports from the Connect screen at any time.

Ühenduse katkestamiseks puuduta ühenduse loomise ekraanil katkestamise nuppu:

![Disconnect from radio](../../assets/screenshots/connections_disconnect.png)

## Desktop Connections

On Desktop (Linux/macOS/Windows), the app supports:

- **Bluetooth (BLE)** — via the Kable library; works on macOS, Linux, and Windows
- **USB port** – peamine juhtmega ühendusmeetod
- **TCP/IP** — for network-connected radios

See [Desktop App](desktop) for platform-specific details and keyboard shortcuts.

## Related Topics

- [Getting Started](onboarding) — first-launch setup and permissions
- [Settings — Radio & User](settings-radio-user) — Bluetooth and network configuration
- [Desktop App](desktop) — desktop-specific connection details
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — full list of compatible radios on meshtastic.org

---

