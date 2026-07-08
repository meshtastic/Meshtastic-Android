---
title: Ühendus
parent: User Guide
nav_order: 2
last_updated: 2026-06-25
description: Ühenda oma telefon või arvuti Meshtastic raadioga Bluetoothi, USB või TCP/IP kaudu.
aliases:
  - sinihammas
  - usb
  - tcp
  - seon
---

# Ühendus

Meshtastic toetab mitut transpordimeetodit telefoni/arvuti ja raadiosõlme vaheliseks suhtluseks.

## Sinihammas (BLE)

Bluetooth Low Energy is the default and most common connection method on Android.

### Seadme sidumine

1. Veendu, et Meshtastic seade on sisse lülitatud ja sidumisrežiimis.
2. Ava rakendus ja navigeeri vahekaardile **Ühendused**.
3. Tap **Scan for Bluetooth devices** — nearby Meshtastic radios will appear.
4. Select your device from the list.
5. Nõustu Bluetoothi ​​sidumise taotlusega, kui see kuvatakse.

![Scanning for Bluetooth devices, with a discovered radio in the list](../../assets/screenshots/connections_bluetooth_scan.png)

You can filter devices by transport type using the filter chips at the top:

![Transport filter chips](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Vihje:** Kui sinu seadet ei kuvata, kontrolli, kas sinihamba ​​ja asukoha load on antud ning et raadio poleks juba teise seadmega ühendatud.

### Connection Status

| Icon | Olek             | Kirjeldus                     |
| ---- | ---------------- | ----------------------------- |
| 🟢   | Ühendatud        | Active radio link established |
| 🟡   | Ühendan          | Handshake in progress         |
| 🔴   | Ühendus katkenud | No active connection          |
| ⚪    | Pole seadistatud | Seadet pole valitud           |

Ühenduse loomisel näitab olekuindikaator ühenduse praegust olekut:

![Connecting status](../../assets/screenshots/connections_connecting.png)

Kui seadmeid ei leita, kuvab rakendus tühja oleku koos juhistega:

![Ühtegi seadet ei leitud](../../assets/screenshots/connections_empty_state.png)

### Troubleshooting Bluetooth

- **Device not found:** Toggle Bluetooth off/on, ensure location is enabled.
- **Connection drops:** Move closer to the radio; check for interference.
- **Sidumine tagasi lükatud:** Unusta seade Androidi sinihamba ​​seadetes ja proovi uuesti.

## USB port

USB connections provide a wired alternative, useful for desktop or when Bluetooth is unavailable.

### Seadistamine

1. Connect your radio via USB cable to your device.
2. Rakendus küsib USB luba – puuduta **Luba**.
3. The connection is established automatically.

> ⚠️ **Märkus:** USB ühenduste jaoks on Android-seadmetes vaja OTG tuge.

## TCP/IP (Network)

Some Meshtastic radios support WiFi/Ethernet connectivity, allowing TCP-based connections over your local network. Get the radio onto your network first — using the radio's own WiFi settings (via the firmware web interface or another connection) — then connect to it from the app.

### Connecting over the Network

1. Make sure the radio is on the same local network as your phone/desktop.
2. On the Connect screen, select the **Network** transport filter.
3. Choose the radio one of two ways:
   - **Scan Network Devices** — toggle this on to auto-discover radios that advertise themselves on the local network (mDNS / `_meshtastic._tcp`). Discovered devices appear in the list; tap one to connect.
   - **Add Network Device Manually** — enter the radio's IP address (or hostname) and port (default: `4403`).
4. Previously-used network addresses are remembered under **Recent Network Devices** for quick reconnection (long-press to remove one).

> 💡 **Tip:** Network discovery uses mDNS, which only works when both devices are on the same subnet. On Android 17+ the app needs the local-network permission for scanning; if discovery finds nothing, add the device manually by IP.

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

- [Alustamine](onboarding) — esmakäivituse seadistamine ja load
- [Seaded — Raadio ja kasutaja](settings-radio-user) — sinihamba ​​ja võrgu seadistus
- [Desktop App](desktop) — desktop-specific connection details
- [Supported devices](https://meshtastic.org/docs/hardware/devices) — full list of compatible radios on meshtastic.org

---

