---
title: Työpöytäsovellus
parent: Käyttöopas
nav_order: 14
last_updated: 2026-08-29
description: Asenna ja käytä Meshtastic-työpöytäsovellusta Linuxilla, macOS:llä ja Windowsilla — yhteydet, ominaisuuksien yhtenevyys ja pikanäppäimet.
aliases:
  - työpöytä
  - linux
  - macos
  - windows
  - jvm
---

# Työpöytäsovellus

This page covers installing the Meshtastic desktop app, connecting a radio, and how it differs from Android. The desktop app shares its core codebase with Android via Kotlin Multiplatform, so most features work identically across Linux, macOS, and Windows.

## Asennus

### Linux

- Download the `.deb` or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Tai rakenna lähdekoodista komennolla `./gradlew :desktopApp:run`

### macOS

- Download the `.dmg` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Tai rakenna lähdekoodista

### Windows

- Download the `.msi` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Tai rakenna lähdekoodista

## Radioon yhdistäminen

### USB-sarjaportti (ensisijainen)

The most reliable connection method on desktop:

Connect your radio via USB. The app detects the serial port automatically; if it doesn't, select the port from the Connect menu.

### TCP/IP

Verkkoyhteydellä oleville radioille:

1. Syötä radion IP-osoite ja portti (oletus: 4403).
2. Paina **Yhdistä**.

### Bluetooth (BLE)

Bluetooth Low Energy is supported on desktop via the [Kable](https://github.com/JuulLabs/kable) library:

1. Varmista, että järjestelmässäsi on Bluetooth-adapteri. Sovellus etsii lähellä olevia Meshtastic-radioita automaattisesti.
2. Select your radio from the Connect screen.

## Ominaisuuksien yhtenevyys

| Ominaisuus                                                  | Android | Työpöytä | Viestit                                                                                                                                                |
| ----------------------------------------------------------- | ------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Viestit                                                     | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                       |
| Radiolista                                                  | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                       |
| Kartta                                                      | ✓       | ✓        | Interactive MapLibre map, with base map and overlay pickers and custom tile sources. No offline downloads or local `.mbtiles` archives |
| Map layers (`.kml`/`.kmz`/GeoJSON)       | ✓       | ✓        | Same layer store and sheet as Android; imported files draw on the desktop map                                                                          |
| Site Planner                                                | ✓       | ✓\*      | \*Opens in your browser on desktop; the estimate is not drawn on the desktop map                                                                       |
| Asetukset                                                   | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                       |
| Bluetooth (BLE)                          | ✓       | ✓        | Työpöydällä Kable-kirjaston kautta                                                                                                                     |
| Laiteohjelmiston päivitys                                   | ✓       | ✓        | Sovelluksen USB-, BLE- ja Wi-Fi-päivitykset (ESP32) toimivat samalla tavalla kuin Androidissa                                       |
| Ilmoitukset                                                 | ✓       | ✓        | Käyttöjärjestelmän natiivit ilmoitukset                                                                                                                |
| Widgetit                                                    | ✓       | ✗        | Vain Android                                                                                                                                           |
| Tekoälyavustaja (Chirpy)                 | ✓\*     | ✗        | Vain Google-version Android-laitteissa                                                                                                                 |
| Sovellustoiminnot (järjestelmän tekoäly) | ✓†      | ✗        | Vain Google-version Android-laitteissa                                                                                                                 |

\*Chirpy AI vaatii Android 14+ -version Google-version Android-laitteissa, joissa on tuettu laitteisto.

†Sovellustoiminnot tuo sovellustoiminnot Android-järjestelmän tekoälylle Google-version Android-laitteissa. Katso [Sovellustoiminnot](app-functions).

## Käyttöliittymäerot

The desktop app uses the same Compose Multiplatform UI with adaptations for larger screens and desktop interaction.

### Pikanäppäimet

Pikanäppäimissä käytetään macOS:ssä **⌘**-näppäintä (Command) ja Windowsissa sekä Linuxissa **Ctrl**-näppäintä. (Super-/Windows-näppäimelle ei ole määritetty toimintoa.)

| Pikanäppäin  | Toiminto                    |
| ------------ | --------------------------- |
| **⌘/Ctrl+Q** | Quit the app                |
| **⌘/Ctrl+,** | Avaa asetukset              |
| **⌘/Ctrl+1** | Vaihda Viestit-välilehdelle |
| **⌘/Ctrl+2** | Vaihda Radiot-välilehdelle  |
| **⌘/Ctrl+3** | Vaihda Kartta-välilehdelle  |
| **⌘/Ctrl+4** | Vaihda Yhdistä-välilehdelle |
| **⌘/Ctrl+/** | Avaa tietoja                |

### Ikkuna ja järjestelmätarjotin

- **Ikkunan koon muuttaminen** — responsiivinen asettelu mukautuu ikkunan kokoon
- **Järjestelmätarjotin** — pienennä järjestelmätarjottimeen taustalla tapahtuvaa mesh-toimintaa varten
- **Valikko** — napsauta järjestelmätarjottimen kuvaketta hiiren oikealla näyttääksesi ikkunan tai sulkeaksesi sovelluksen
- **Hiiritoiminnot** — hover-tilat ja tavallinen työpöydän navigointi

### Ilmoitusasetukset

The desktop app provides in-app toggles for controlling which notifications are shown — messages, new nodes, and low battery alerts. Avaa nämä kohdasta **Asetukset → Ilmoitukset** sovelluksessa.

## Sisäänrakennettu dokumentaatioselain

The desktop app includes a built-in documentation browser for quick access to help content without leaving the app.

![Dokumentaatioselain ja sisällysluettelo](../../assets/screenshots/docs-browser_toc.png)

Selain tukee koko dokumentaation laajuista kokotekstihakua:

![Haku dokumentaatioselaimessa](../../assets/screenshots/docs-browser_search.png)

Yksittäiset dokumenttisivut renderöidään täydellä muotoilulla:

![Dokumenttisivu](../../assets/screenshots/docs-browser_page.png)

## Rakentaminen lähdekoodista

```bash
git clone https://github.com/meshtastic/Meshtastic-Android.git
cd Meshtastic-Android
./gradlew :desktopApp:run
```

Vaatimukset:

- JDK 25 (Gradle can provision the toolchain itself via foojay)
- Android SDK:ta ei tarvita pelkkien työpöytäversioiden rakentamiseen

## Tunnetut rajoitukset

- Offline tile downloads and local `.mbtiles` archives are not available on desktop.
- `.kml`/`.kmz`/GeoJSON layer import works — see
  [Map & Waypoints](map-and-waypoints#map-layers). Site Planner opens in your browser
  rather than in the app; to bring its coverage estimate onto the map, use the planner's
  **Export › GeoJSON** and add the file as a layer. Custom network tile sources work too — see
  [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- Jotkin Android-kohtaiset ominaisuudet (widgetit, tietyt ilmoituskanavat) eivät ole käytettävissä
- Suorituskyky voi vaihdella heikkotehoisella laitteistolla ajettaessa Compose Desktopia
- BLE-paritus ei vielä tallenna laiteparia työpöydällä (paritus toimii ilman tallennusta)

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhteystapojen yleiskatsaus
- [Laiteohjelmistopäivitykset](firmware) — USB-, BLE- ja Wi-Fi-päivitykset toimivat samalla tavalla kuin Androidissa
