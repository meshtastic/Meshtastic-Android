---
title: Työpöytäsovellus
nav_order: 14
last_updated: 2026-10-21
description: Asenna ja käytä Meshtastic-työpöytäsovellusta Linuxilla, macOS:llä ja Windowsilla — yhteydet, ominaisuuksien yhtenevyys ja pikanäppäimet.
aliases:
  - työpöytä
  - linux
  - macos
  - windows
  - jvm
---

# Työpöytäsovellus

This page covers installing the Meshtastic desktop app, connecting a node, and how it differs from Android. Työpöytä-sovellus käyttää samaa ydinkoodia kuin Android Kotlin Multiplatformin kautta, joten useimmat ominaisuudet toimivat samalla tavalla Linuxissa, macOS:ssä ja Windowsissa.

## Asennus

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Each comes in an x86_64 and an arm64 (aarch64) build.
- The AppImage carries its own update information, so AppImageUpdate and other zsync-based updaters can update it in place to the latest production release.
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- Tai rakenna lähdekoodista komennolla `./gradlew :desktopApp:run`

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). It is built for Apple silicon only.
- Tai rakenna lähdekoodista

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases). Both are x64 builds.
- Tai rakenna lähdekoodista

## Radioon yhdistäminen

All three transports live on the **Connect** tab, the same as on Android. Pick one with the transport selector. See [Connections](connections) for the full walkthrough.

### USB-sarjaportti (ensisijainen)

Luotettavin yhteystapa Työpöydällä:

Connect your node by USB, select **USB** in the transport selector, and click the node's serial port in the list. The list refreshes on its own as ports appear and disappear.

### TCP/IP

For network-connected nodes, select **Network** in the transport selector, then either:

1. Turn on **Scan for network devices** and click a node the scan finds, or
2. Click **Add device manually…** and enter the node's IP address or hostname and port (default: `4403`).

### Bluetooth (BLE)

Bluetooth Low Energy on tuettu Työpöydällä [Kable](https://github.com/JuulLabs/kable)-kirjaston avulla:

1. Ensure your system has a Bluetooth adapter, and select **Bluetooth** in the transport selector.
2. Click **Scan for Bluetooth devices**, then select your node from the list.

## Ominaisuuksien yhtenevyys

| Ominaisuus                                                  | Android | Työpöytä | Viestit                                                                                                                                                                                                                                                                        |
| ----------------------------------------------------------- | ------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Viestit                                                     | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                                                                                               |
| Radiolista                                                  | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                                                                                               |
| Kartta                                                      | ✓       | ✓        | Vuorovaikutteinen MapLibre-kartta, jossa on taustakartan ja karttatasojen valitsimet sekä mukautetut karttalähteet. Offline terrain (hillshade and contours) can be downloaded; offline base-map packs and local `.mbtiles` archives cannot |
| Karttatasot (`.kml` / `.kmz` / GeoJSON)  | ✓       | ✓        | Sama karttatasojen hallinta kuin Androidissa. Tuodut tiedostot piirretään Työpöytä-kartalle                                                                                                                                                                    |
| Site Planner                                                | ✓       | ✓        | Computes the coverage estimate in the app and adds it to the map as a layer, as on Android                                                                                                                                                                                     |
| Asetukset                                                   | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                                                                                               |
| Bluetooth (BLE)                          | ✓       | ✓        | Työpöydällä Kable-kirjaston kautta                                                                                                                                                                                                                                             |
| Laiteohjelmiston päivitys                                   | ✓       | ✓        | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only                                                                          |
| Ilmoitukset                                                 | ✓       | ✓        | Käyttöjärjestelmän natiivit ilmoitukset                                                                                                                                                                                                                                        |
| Widgetit                                                    | ✓       | ✗        | Vain Android                                                                                                                                                                                                                                                                   |
| Tekoälyavustaja (Chirpy)                 | ✓\*     | ✗        | Vain Google-version Android-laitteissa                                                                                                                                                                                                                                         |
| Sovellustoiminnot (järjestelmän tekoäly) | ✓†      | ✗        | Vain Google-version Android-laitteissa                                                                                                                                                                                                                                         |

\*Chirpy AI vaatii Android 14+ -version Google-version Android-laitteissa, joissa on tuettu laitteisto.

†Sovellustoiminnot tuo sovellustoiminnot Android-järjestelmän tekoälylle Google-version Android-laitteissa. Katso [Sovellustoiminnot](app-functions).

## Käyttöliittymäerot

Työpöytä-sovellus käyttää samaa Compose Multiplatform -käyttöliittymää, mutta sitä on mukautettu suuremmille näytöille ja Työpöytä-käyttöön.

### Pikanäppäimet

Pikanäppäimissä käytetään macOS:ssä **⌘**-näppäintä (Command) ja Windowsissa sekä Linuxissa **Ctrl**-näppäintä. (Super-/Windows-näppäimelle ei ole määritetty toimintoa.)

| Pikanäppäin  | Toiminto                    |
| ------------ | --------------------------- |
| **⌘/Ctrl+Q** | Sulje sovellus              |
| **⌘/Ctrl+,** | Avaa asetukset              |
| **⌘/Ctrl+1** | Vaihda Viestit-välilehdelle |
| **⌘/Ctrl+2** | Vaihda Radiot-välilehdelle  |
| **⌘/Ctrl+3** | Vaihda Kartta-välilehdelle  |
| **⌘/Ctrl+4** | Vaihda Yhdistä-välilehdelle |
| **⌘/Ctrl+/** | Avaa tietoja                |

### Ikkuna ja järjestelmätarjotin

- **Ikkunan koon muuttaminen** — responsiivinen asettelu mukautuu ikkunan kokoon
- **System tray** — closing the window minimizes to the system tray for background mesh operation, provided the tray icon can be clicked to bring the window back. On Linux that needs a desktop environment with a StatusNotifierItem tray. Where there is no such tray, closing quits the app instead
- **Tray menu** — right-click the tray icon for **Show Meshtastic** or **Quit**
- **Hiiritoiminnot** — hover-tilat ja tavallinen työpöydän navigointi

### Ilmoitusasetukset

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, **Low battery notifications**, **Reaction notifications**, and **Waypoint notifications**.

## Sisäänrakennettu dokumentaatioselain

Työpöytä-sovelluksessa on sisäänrakennettu dokumentaation selain, jonka avulla ohjeisiin pääsee nopeasti poistumatta sovelluksesta.

![Dokumentaatioselain ja sisällysluettelo](../../assets/screenshots/docs-browser_toc.png)

The browser searches page titles and keywords across all documentation:

![Haku dokumentaatioselaimessa](../../assets/screenshots/docs-browser_search.png)

Yksittäiset dokumenttisivut renderöidään täydellä muotoilulla:

![Dokumenttisivu](../../assets/screenshots/docs-browser_page.png)

## Rakentaminen lähdekoodista

```shell
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Vaatimukset:

- JDK 25 to run Gradle (Gradle can provision it itself via foojay)
- JetBrains Runtime (JBR) 25 installed where Gradle's toolchain detection finds it. The desktop app compiles against it and bundles it, and foojay does not offer it, so Gradle cannot download it
- Android SDK:ta ei tarvita pelkkien työpöytäversioiden rakentamiseen

## Tunnetut rajoitukset

- Offline base-map downloads and local `.mbtiles` archives are not available on desktop. Offline terrain is — see [Map & Waypoints](map-and-waypoints).
- `.kml`-, `.kmz`- ja GeoJSON-karttatasojen tuonti toimii — katso [Kartta ja reittipisteet](map-and-waypoints#map-layers). Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only, and the
  desktop app does not offer it. Use the [Web Flasher](https://flasher.meshtastic.org) instead
- Jotkin Android-kohtaiset ominaisuudet (widgetit, tietyt ilmoituskanavat) eivät ole käytettävissä
- Suorituskyky voi vaihdella heikkotehoisella laitteistolla ajettaessa Compose Desktopia
- BLE bonding is not supported on desktop (pairing works without bonding)

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhteystapojen yleiskatsaus
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
