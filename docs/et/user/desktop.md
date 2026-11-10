---
title: Desktop App
nav_order: 14
last_updated: 2026-10-21
description: Meshtastic arvuti rakendus pakub samu võrgusuhtluse funktsioone Linuxis, macOS-is ja Windowsis.
aliases:
  - töölaud
  - linux
  - macos
  - windows
  - jvm
---

# Desktop App

This page covers installing the Meshtastic desktop app, connecting a node, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## Paigaldus

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Each comes in an x86_64 and an arm64 (aarch64) build.
- The AppImage carries its own update information, so AppImageUpdate and other zsync-based updaters can update it in place to the latest production release.
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- Or build from source using `./gradlew :desktopApp:run`

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). It is built for Apple silicon only.
- Or build from source

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Both are x64 builds.
- Või ehita lähtekoodist

## Connecting Your Radio

All three transports live on the **Connect** tab, the same as on Android. Pick one with the transport selector. See [Connections](connections) for the full walkthrough.

### USB port (esmane)

The most reliable connection method on desktop:

Connect your node by USB, select **USB** in the transport selector, and click the node's serial port in the list. The list refreshes on its own as ports appear and disappear.

### TCP/IP

For network-connected nodes, select **Network** in the transport selector, then either:

1. Turn on **Scan for network devices** and click a node the scan finds, or
2. Click **Add device manually…** and enter the node's IP address or hostname and port (default: `4403`).

### Sinihammas (BLE)

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Ensure your system has a Bluetooth adapter, and select **Bluetooth** in the transport selector.
2. Click **Scan for Bluetooth devices**, then select your node from the list.

## Feature Parity

| Feature                                                | Android | Desktop | Sõnumid                                                                                                                                                                                                                                         |
| ------------------------------------------------------ | ------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Messaging                                              | ✓       | ✓       | Full parity                                                                                                                                                                                                                                     |
| Sõlmede loend                                          | ✓       | ✓       | Full parity                                                                                                                                                                                                                                     |
| Kaart                                                  | ✓       | ✓       | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. Offline terrain (hillshade and contours) can be downloaded; offline base-map packs and local `.mbtiles` archives cannot |
| Map layers (`.kml`/`.kmz`/GeoJSON)  | ✓       | ✓       | Same layer store and sheet as Android; imported files draw on the desktop map                                                                                                                                                                   |
| Saidi planeerija                                       | ✓       | ✓       | Computes the coverage estimate in the app and adds it to the map as a layer, as on Android                                                                                                                                                      |
| Sätted                                                 | ✓       | ✓       | Full parity                                                                                                                                                                                                                                     |
| Sinihammas (BLE)                    | ✓       | ✓       | Kable'i kaudu töölauale                                                                                                                                                                                                                         |
| Püsivara uuendus                                       | ✓       | ✓       | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only                                           |
| Märguanded                                             | ✓       | ✓       | Emakeelsed op.süsteemi märguanded                                                                                                                                                                                               |
| Widgets                                                | ✓       | ✗       | Android-only                                                                                                                                                                                                                                    |
| TI assistent (Chirpy)               | ✓\*     | ✗       | Google flavor Android only                                                                                                                                                                                                                      |
| Rakenduse funktsioonid (süstemi TI) | ✓†      | ✗       | Google flavor Android only                                                                                                                                                                                                                      |

\*Chirpy tehisintellekti jaoks on vaja Google'i eriversioonidel Android 14+ ja toetatud riistvaraga.

†Rakendusfunktsioonid paljastavad rakenduse toimingud Androidi süsteemi tehisintellektile Google'i eri versioonides. Vaata [Rakenduse funktsioonid](app-functions).

## UI Differences

The desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Kiirklahvid

Otseteed kasutavad macOS-is **⌘** (Command) ja Windowsis ning Linuxis **Ctrl**. (Super/Windowsi võti pole seotud.)

| Otsetee      | Action                     |
| ------------ | -------------------------- |
| **⌘/Ctrl+Q** | Quit the app               |
| **⌘/Ctrl+,** | Ava sätted                 |
| **⌘/Ctrl+1** | Switch to Messages tab     |
| **⌘/Ctrl+2** | Switch to Nodes tab        |
| **⌘/Ctrl+3** | Lülitu kaardi vahekaardile |
| **⌘/Ctrl+4** | Switch to Connect tab      |
| **⌘/Ctrl+/** | Open About                 |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — closing the window minimizes to the system tray for background mesh operation, provided the tray icon can be clicked to bring the window back. On Linux that needs a desktop environment with a StatusNotifierItem tray. Where there is no such tray, closing quits the app instead
- **Tray menu** — right-click the tray icon for **Show Meshtastic** or **Quit**
- **Hiire interaktsioon** — hõljumisseisundid ja standardne töölaua navigeerimine

### Notification Preferences

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, **Low battery notifications**, **Reaction notifications**, and **Waypoint notifications**.

## Sisseehitatud dokumentatsioonibrauser

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![Dokumentide brauser sisukorraga](../../assets/screenshots/docs-browser_toc.png)

The browser searches page titles and keywords across all documentation:

![Dokumentide brauserist otsimine](../../assets/screenshots/docs-browser_search.png)

Individual doc pages render with full formatting:

![Dokumentatsioonileht](../../assets/screenshots/docs-browser_page.png)

## Building from Source

```shell
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Nõuded:

- JDK 25 to run Gradle (Gradle can provision it itself via foojay)
- JetBrains Runtime (JBR) 25 installed where Gradle's toolchain detection finds it. The desktop app compiles against it and bundles it, and foojay does not offer it, so Gradle cannot download it
- No Android SDK required for desktop-only builds

## Known Limitations

- Offline base-map downloads and local `.mbtiles` archives are not available on desktop. Offline terrain is — see [Map & Waypoints](map-and-waypoints).
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only, and the
  desktop app does not offer it. Use the [Web Flasher](https://flasher.meshtastic.org) instead
- Mõned Androidile omased funktsioonid (vidinad, kindlad teavituskanalid) pole saadaval
- Performance may vary on low-spec hardware running Compose Desktop
- BLE bonding is not supported on desktop (pairing works without bonding)

## Seotud teemad

- [Ühendused] (connections) — ühendusmeetodite ülevaade
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
