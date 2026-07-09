---
title: Desktop App
parent: User Guide
nav_order: 14
last_updated: 2026-07-07
description: Meshtastic arvuti rakendus pakub samu võrgusuhtluse funktsioone Linuxis, macOS-is ja Windowsis.
aliases:
  - töölaud
  - linux
  - macos
  - windows
  - jvm
---

# Desktop App

Meshtastic arvuti rakendus jagab oma põhikoodibaasi Androidiga Kotlin Multiplatformi kaudu. Most features work identically on Linux, macOS, and Windows.

## Installation

### Linux

- Lae väljalaske lehelt alla pakett `.deb` või `.AppImage`
- Or build from source using `./gradlew :desktopApp:run`

### macOS

- Lae väljalaske lehelt `.dmg` pakett
- Or build from source

### Windows

- Download the `.msi` installer from releases
- Or build from source

## Connecting Your Radio

### USB port (esmane)

The most reliable connection method on Desktop:

1. Ühenda oma Meshtastic raadio USB kaabliga.
2. Rakendus peaks jadapordi automaatselt tuvastama.
3. Kui seda ei tuvastata, vali menüüst Ühenda õige jadapordiga.

### TCP/IP

For network-connected radios:

1. Sisesta raadio IP-aadress ja port (vaikimisi: 4403).
2. Klõpsa **Ühenda**.

### Sinihammas (BLE)

Sinihamba madal voolutarve on lauaarvutites toetatud [Kable](https://github.com/JuulLabs/kable) teegi kaudu:

1. Veendu, et süsteemil on sinihamba adapter.
2. Rakendus otsib automaatselt lähedalasuvaid Meshtastic raadioid.
3. Select your device from the Connect screen.

## Feature Parity

| Feature                                      | Android | Desktop | Sõnumid                                                                                                     |
| -------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------------------------- |
| Messaging                                    | ✓       | ✓       | Full parity                                                                                                 |
| Node List                                    | ✓       | ✓       | Full parity                                                                                                 |
| Kaart                                        | ✓       | ◐       | Map tab exists on desktop, but the interactive map view is Android-only                                     |
| Sätted                                       | ✓       | ✓       | Full parity                                                                                                 |
| Sinihammas (BLE)          | ✓       | ✓       | Via Kable on desktop                                                                                        |
| Püsivara uuendus                             | ✓       | ✓       | Rakendusesisese USB, BLE ja Wi-Fi (ESP32) värskendused toimivad samamoodi nagu Androidis |
| Märguanded                                   | ✓       | ✓       | Emakeelsed op.süsteemi märguanded                                                           |
| Widgets                                      | ✓       | ✗       | Android-only                                                                                                |
| Android auto                                 | ✓       | ✗       | Android-only — not available on Desktop or iOS                                                              |
| AI Assistant (Chirpy)     | ✓\*     | ✗       | Google flavor Android only                                                                                  |
| App Functions (system AI) | ✓†      | ✗       | Google flavor Android only                                                                                  |

\*Chirpy AI requires Android 14+ on Google flavor builds with supported hardware.

†App Functions exposes app actions to the Android system AI on Google flavor builds. See [App Functions](app-functions).

## UI Differences

The Desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Keyboard Shortcuts

All shortcuts use the **Meta** key — that's ⌘ (Command) on macOS and the Super / Windows key on Linux and Windows. (`Ctrl` is not bound.)

| Shortcut   | Action                 |
| ---------- | ---------------------- |
| **Meta+Q** | Quit the application   |
| **Meta+,** | Ava sätted             |
| **Meta+1** | Switch to Messages tab |
| **Meta+2** | Switch to Nodes tab    |
| **Meta+3** | Switch to Map tab      |
| **Meta+4** | Switch to Connect tab  |
| **Meta+/** | Open About             |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — minimize to system tray for background mesh operation
- **Salvemenüü** – paremklõpsa salveikoonil akna kuvamiseks või sulgemiseks
- **Hiire interaktsioon** — hõljumisseisundid ja standardne töölaua navigeerimine

### Notification Preferences

Töölauarakendus pakub rakendusesiseste kuvatavate märguannete juhtimist – sõnumite, uute sõlmede ja aku tühjenemise märguanded. Nendele pääsed ligi rakenduses menüüst **Seaded → Märguanded**.

## Built-in Documentation Browser

Töölauarakendusel on sisseehitatud dokumentatsioonibrauser, mis võimaldab kiiret juurdepääsu abisisule ilma rakendusest lahkumata.

![Docs browser with table of contents](../../assets/screenshots/docs-browser_toc.png)

The browser supports full-text search across all documentation:

![Searching the docs browser](../../assets/screenshots/docs-browser_search.png)

Individual doc pages render with full formatting:

![A documentation page](../../assets/screenshots/docs-browser_page.png)

## Building from Source

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Requirements:

- JDK 21
- No Android SDK required for desktop-only builds

## Known Limitations

- The interactive map view is Android-only — the Map tab is present but does not render a map on desktop
- Mõned Androidile omased funktsioonid (vidinad, kindlad teavituskanalid) pole saadaval
- Performance may vary on low-spec hardware running Compose Desktop
- Lauaarvutid ei toeta veel BLE liitmist (sidumine toimib ilma ühendamiseta)

## Related Topics

- [Connections](connections) — connection methods overview
- [Püsivara uuendus](firmware) — USB, BLE ja Wi-Fi värskendused toimivad samamoodi nagu Androidis

---

