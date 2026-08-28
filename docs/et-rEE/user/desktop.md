---
title: Desktop App
parent: Kasutusjuhend
nav_order: 14
last_updated: 2026-08-27
description: Meshtastic arvuti rakendus pakub samu võrgusuhtluse funktsioone Linuxis, macOS-is ja Windowsis.
aliases:
  - töölaud
  - linux
  - macos
  - windows
  - jvm
---

# Desktop App

Meshtastic arvuti rakendus jagab oma põhikoodibaasi Androidiga Kotlin Multiplatformi kaudu. Enamik funktsioone töötab identselt Linuxis, macOS-is ja Windowsis.

## Paigaldus

### Linux

- Lae väljalaske lehelt pakett `.deb` või `.AppImage`
- Or build from source using `./gradlew :desktopApp:run`

### macOS

- Lae väljalaske lehelt `.dmg` pakett
- Or build from source

### Windows

- Lae väljalaske lehelt `.msi` paigaldus fail
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
3. Vali ühenduste ekraanilt oma seade.

## Feature Parity

| Feature                                                | Android | Desktop | Sõnumid                                                                                                     |
| ------------------------------------------------------ | ------- | ------- | ----------------------------------------------------------------------------------------------------------- |
| Messaging                                              | ✓       | ✓       | Full parity                                                                                                 |
| Sõlmede loend                                          | ✓       | ✓       | Full parity                                                                                                 |
| Kaart                                                  | ✓       | ◐       | Kaardi vahekaart on küll töölaual olemas, aga interaktiivne kaardivaade on saadaval ainult Androidis        |
| Sätted                                                 | ✓       | ✓       | Full parity                                                                                                 |
| Sinihammas (BLE)                    | ✓       | ✓       | Kable'i kaudu töölauale                                                                                     |
| Püsivara uuendus                                       | ✓       | ✓       | Rakendusesisese USB, BLE ja Wi-Fi (ESP32) värskendused toimivad samamoodi nagu Androidis |
| Märguanded                                             | ✓       | ✓       | Emakeelsed op.süsteemi märguanded                                                           |
| Widgets                                                | ✓       | ✗       | Android-only                                                                                                |
| TI assistent (Chirpy)               | ✓\*     | ✗       | Google flavor Android only                                                                                  |
| Rakenduse funktsioonid (süstemi TI) | ✓†      | ✗       | Google flavor Android only                                                                                  |

\*Chirpy tehisintellekti jaoks on vaja Google'i eriversioonidel Android 14+ ja toetatud riistvaraga.

†Rakendusfunktsioonid paljastavad rakenduse toimingud Androidi süsteemi tehisintellektile Google'i eri versioonides. Vaata [Rakenduse funktsioonid](app-functions).

## UI Differences

The Desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Kiirklahvid

Otseteed kasutavad macOS-is **⌘** (Command) ja Windowsis ning Linuxis **Ctrl**. (Super/Windowsi võti pole seotud.)

| Otsetee      | Action                     |
| ------------ | -------------------------- |
| **⌘/Ctrl+Q** | Sule rakendus              |
| **⌘/Ctrl+,** | Ava sätted                 |
| **⌘/Ctrl+1** | Switch to Messages tab     |
| **⌘/Ctrl+2** | Switch to Nodes tab        |
| **⌘/Ctrl+3** | Lülitu kaardi vahekaardile |
| **⌘/Ctrl+4** | Switch to Connect tab      |
| **⌘/Ctrl+/** | Open About                 |

### Window & System Tray

- **Window resizing** — responsive layout adapts to window dimensions
- **System tray** — minimize to system tray for background mesh operation
- **Salvemenüü** – paremklõpsa salveikoonil akna kuvamiseks või sulgemiseks
- **Hiire interaktsioon** — hõljumisseisundid ja standardne töölaua navigeerimine

### Notification Preferences

Töölauarakendus pakub rakendusesiseste kuvatavate märguannete juhtimist – sõnumite, uute sõlmede ja aku tühjenemise märguanded. Nendele pääsed ligi rakenduses menüüst **Seaded → Märguanded**.

## Sisseehitatud dokumentatsioonibrauser

Töölauarakendusel on sisseehitatud dokumentatsioonibrauser, mis võimaldab kiiret juurdepääsu abisisule ilma rakendusest lahkumata.

![Dokumentide brauser sisukorraga](../../assets/screenshots/docs-browser_toc.png)

Brauser toetab täistekstiotsingut kogu dokumentatsioonis:

![Dokumentide brauserist otsimine](../../assets/screenshots/docs-browser_search.png)

Individual doc pages render with full formatting:

![Dokumentatsioonileht](../../assets/screenshots/docs-browser_page.png)

## Building from Source

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Nõuded:

- JDK 25 (Gradle can provision the toolchain itself via foojay)
- No Android SDK required for desktop-only builds

## Known Limitations

- Interaktiivne kaardivaade on saadaval ainult Androidile – kaardi vahekaart on olemas, kuid see ei kuva kaarti töölaual
- Mõned Androidile omased funktsioonid (vidinad, kindlad teavituskanalid) pole saadaval
- Performance may vary on low-spec hardware running Compose Desktop
- Lauaarvutid ei toeta veel BLE liitmist (sidumine toimib ilma ühendamiseta)

## Seotud teemad

- [Ühendused] (connections) — ühendusmeetodite ülevaade
- [Püsivara uuendus](firmware) — USB, BLE ja Wi-Fi värskendused toimivad samamoodi nagu Androidis

---

