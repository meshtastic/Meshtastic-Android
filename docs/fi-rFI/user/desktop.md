---
title: Työpöytäsovellus
parent: Käyttöopas
nav_order: 14
last_updated: 2026-08-30
description: Asenna ja käytä Meshtastic-työpöytäsovellusta Linuxilla, macOS:llä ja Windowsilla — yhteydet, ominaisuuksien yhtenevyys ja pikanäppäimet.
aliases:
  - työpöytä
  - linux
  - macos
  - windows
  - jvm
---

# Työpöytäsovellus

Tällä sivulla kerrotaan Meshtastic Työpöytä -sovelluksen asentamisesta, radion yhdistämisestä ja siitä, miten se eroaa Android-versiosta. Työpöytä-sovellus käyttää samaa ydinkoodia kuin Android Kotlin Multiplatformin kautta, joten useimmat ominaisuudet toimivat samalla tavalla Linuxissa, macOS:ssä ja Windowsissa.

## Asennus

### Linux

- Download the `.deb`, `.rpm`, or `.AppImage` package from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Or install from Flathub: `flatpak install flathub org.meshtastic.MeshtasticDesktop`
- Tai rakenna lähdekoodista komennolla `./gradlew :desktopApp:run`

### macOS

- Lataa `.dmg`-paketti [julkaisusivulta](https://github.com/meshtastic/Meshtastic-Android/releases)
- Tai rakenna lähdekoodista

### Windows

- Download the `.msi` or `.exe` installer from the [releases page](https://github.com/meshtastic/Meshtastic-Android/releases)
- Tai rakenna lähdekoodista

## Radioon yhdistäminen

### USB-sarjaportti (ensisijainen)

Luotettavin yhteystapa Työpöydällä:

Yhdistä radiosi USB:n kautta. Sovellus tunnistaa sarjaportin automaattisesti. Jos se ei onnistu, valitse portti Yhdistä-valikosta.

### TCP/IP

Verkkoyhteydellä oleville radioille:

1. Syötä radion IP-osoite ja portti (oletus: 4403).
2. Paina **Yhdistä**.

### Bluetooth (BLE)

Bluetooth Low Energy on tuettu Työpöydällä [Kable](https://github.com/JuulLabs/kable)-kirjaston avulla:

1. Varmista, että järjestelmässäsi on Bluetooth-adapteri. Sovellus etsii lähellä olevia Meshtastic-radioita automaattisesti.
2. Valitse radiosi Yhdistä-näytöltä.

## Ominaisuuksien yhtenevyys

| Ominaisuus                                                  | Android | Työpöytä | Viestit                                                                                                                                                                                               |
| ----------------------------------------------------------- | ------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Viestit                                                     | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                      |
| Radiolista                                                  | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                      |
| Kartta                                                      | ✓       | ✓        | Vuorovaikutteinen MapLibre-kartta, jossa on taustakartan ja karttatasojen valitsimet sekä mukautetut karttalähteet. Ei offline-latauksia eikä paikallisia `.mbtiles`-arkistoja        |
| Karttatasot (`.kml` / `.kmz` / GeoJSON)  | ✓       | ✓        | Sama karttatasojen hallinta kuin Androidissa. Tuodut tiedostot piirretään Työpöytä-kartalle                                                                                           |
| Site Planner                                                | ✓       | ✓\*      | \*Avautuu selaimessa Työpöydällä, eikä arviota piirretä Työpöytä-kartalle                                                                                                                             |
| Asetukset                                                   | ✓       | ✓        | Täysi yhtenevyys                                                                                                                                                                                      |
| Bluetooth (BLE)                          | ✓       | ✓        | Työpöydällä Kable-kirjaston kautta                                                                                                                                                                    |
| Laiteohjelmiston päivitys                                   | ✓       | ✓        | In-app USB, BLE, and Wi-Fi (ESP32) update work the same as Android. The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only |
| Ilmoitukset                                                 | ✓       | ✓        | Käyttöjärjestelmän natiivit ilmoitukset                                                                                                                                                               |
| Widgetit                                                    | ✓       | ✗        | Vain Android                                                                                                                                                                                          |
| Tekoälyavustaja (Chirpy)                 | ✓\*     | ✗        | Vain Google-version Android-laitteissa                                                                                                                                                                |
| Sovellustoiminnot (järjestelmän tekoäly) | ✓†      | ✗        | Vain Google-version Android-laitteissa                                                                                                                                                                |

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
- **System tray** — closing the window minimizes to the system tray for background mesh operation. On a desktop environment with no tray, there is nowhere to minimize to, so closing quits the app instead
- **Valikko** — napsauta järjestelmätarjottimen kuvaketta hiiren oikealla näyttääksesi ikkunan tai sulkeaksesi sovelluksen
- **Hiiritoiminnot** — hover-tilat ja tavallinen työpöydän navigointi

### Ilmoitusasetukset

The desktop app provides in-app toggles for controlling which notifications are shown. Find them in the **App Notifications** section of the Settings screen: **Direct message notifications**, **New node notifications**, and **Low battery notifications**.

## Sisäänrakennettu dokumentaatioselain

Työpöytä-sovelluksessa on sisäänrakennettu dokumentaation selain, jonka avulla ohjeisiin pääsee nopeasti poistumatta sovelluksesta.

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

- JDK 25 (Gradle voi asentaa työkaluketjun itse Foojayn kautta)
- Android SDK:ta ei tarvita pelkkien työpöytäversioiden rakentamiseen

## Tunnetut rajoitukset

- Offline-kartta-alueiden lataukset ja paikalliset `.mbtiles`-arkistot eivät ole käytettävissä Työpöydällä.
- `.kml`-, `.kmz`- ja GeoJSON-karttatasojen tuonti toimii — katso [Kartta ja reittipisteet](map-and-waypoints#map-layers). Site Planner opens in your browser
  rather than in the app; to bring its coverage estimate onto the map, click the transmitter pin
  in the browser and use the planner's GeoJSON export, then add the file as a layer — not the KML
  export, which is a ground-overlay image this map cannot draw. Custom network tile sources work
  too — see [Map & Waypoints](map-and-waypoints#adding-your-own-tile-source)
- The USB maintenance flow — nRF52/RP2040 factory erase and bootloader upgrade — is Android-only. The
  desktop app still shows the option, but it cannot complete there
- Jotkin Android-kohtaiset ominaisuudet (widgetit, tietyt ilmoituskanavat) eivät ole käytettävissä
- Suorituskyky voi vaihdella heikkotehoisella laitteistolla ajettaessa Compose Desktopia
- BLE-paritus ei vielä tallenna laiteparia työpöydällä (paritus toimii ilman tallennusta)

## Aiheeseen liittyvät aiheet

- [Yhteydet](connections) — yhteystapojen yleiskatsaus
- [Firmware Updates](firmware) — in-app USB, BLE, and Wi-Fi update all work the same as on Android
- [Map & Waypoints](map-and-waypoints) — base maps, layers, custom tile sources, and what the desktop map does not do
