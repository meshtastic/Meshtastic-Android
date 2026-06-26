---
title: Yhteydet
parent: Käyttöopas
nav_order: 2
last_updated: 2026-06-25
description: Yhdistä puhelin tai työpöytä Meshtastic-radioon Bluetoothin, USB:n tai TCP/IP:n kautta.
aliases:
  - bluetooth
  - usb
  - tcp
  - pariliitos
---

# Yhteydet

Meshtastic tukee useita siirtotapoja puhelimen/työpöydän ja radion välillä viestimiseen.

## Bluetooth (BLE)

Bluetooth Low Energy on oletus ja yleisin yhteystapa Androidilla.

### Laitteen pariliitos

1. Varmista, että Meshtastic-radio on päällä ja paritustilassa.
2. Avaa sovellus ja siirry **Yhdistä**-välilehdelle.
3. Paina **Etsi laitteita** — lähellä olevat Meshtastic-radiot tulevat näkyviin.
4. Valitse laitteesi listasta.
5. Hyväksy Bluetooth-pariliitospyyntö, jos se tulee näkyviin.

![Scanning for Bluetooth devices, with a discovered radio in the list](../../assets/screenshots/connections_bluetooth_scan.png)

Voit suodattaa laitteita yhteystavan mukaan yläreunan suodatinpainikkeilla:

![Yhteystavan suodatinpainikkeet](../../assets/screenshots/connections_transport_filters.png)

> 💡 **Vinkki:** Jos laitteesi ei näy, varmista että Bluetooth ja sijaintiluvat on myönnetty ja että radio ei ole jo yhdistettynä toiseen laitteeseen.

### Yhteyden tila

| Ikoni | Tila          | Kuvaus                             |
| ----- | ------------- | ---------------------------------- |
| 🟢    | Yhdistetty    | Aktiivinen radiolinkki muodostettu |
| 🟡    | Yhdistetään   | Yhteyden muodostus käynnissä       |
| 🔴    | Ei yhdistetty | Ei aktiivista yhteyttä             |
| ⚪     | Ei määritetty | Ei laitetta valittuna              |

Yhdistettäessä tilailmaisin näyttää nykyisen yhteyden tilan:

![Yhdistämisen tila](../../assets/screenshots/connections_connecting.png)

Jos laitteita ei löydy, sovellus näyttää tyhjän näkymän ohjeiden kanssa:

![Laitteita ei löytynyt](../../assets/screenshots/connections_empty_state.png)

### Bluetoothin vianmääritys

- **Laitetta ei löydy:** Kytke Bluetooth pois/päälle ja varmista, että sijainti on käytössä.
- **Yhteys katkeaa:** Siirry lähemmäs radiota ja tarkista mahdolliset häiriöt.
- **Paritus hylätty:** Poista laite Androidin Bluetooth-asetuksista ja yritä uudelleen.

## USB-sarjaporttiyhteys

USB-yhteydet tarjoavat langallisen vaihtoehdon, hyödyllinen työpöytäkäytössä tai kun Bluetooth ei ole käytettävissä.

### Asetukset

1. Yhdistä radio USB-kaapelilla laitteeseesi.
2. Sovellus pyytää USB-oikeuksia — paina **Salli**.
3. Yhteys muodostetaan automaattisesti.

> ⚠️ **Huom:** USB-yhteydet vaativat OTG-tuen Android-laitteissa.

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

### Milloin TCP-yhteyttä kannattaa käyttää

- Radio on samassa lähiverkossa
- Testaus simuloidulla radiolla
- Ympäristöt, joissa Bluetoothissa on häiriöongelmia

## Uudelleenyhdistämisen toiminta

Sovellus yhdistyy käynnistyksen yhteydessä **viimeksi valittuun laitteeseen**. Voit vaihtaa yhteystapaa Yhdistä-näkymästä milloin tahansa.

Yhteyden katkaisemiseksi paina Yhdistä-näkymän katkaisupainiketta:

![Katkaise yhteys radiosta](../../assets/screenshots/connections_disconnect.png)

## Työpöytäyhteydet

Työpöydällä (Linux/macOS/Windows) sovellus tukee:

- **Bluetooth (BLE)** — Kable-kirjaston kautta; toimii macOS:llä, Linuxilla ja Windowsilla
- **USB-sarjaportti** — ensisijainen langallinen yhteystapa
- **TCP/IP** — verkkoyhteydellä oleville radioille

Katso [Työpöytäsovellus](desktop) alustakohtaiset tiedot ja pikanäppäimet.

## Aiheeseen liittyvät aiheet

- [Aloitus](onboarding) — ensikäynnistyksen käyttöönotto ja käyttöoikeudet
- [Asetukset — Radio & käyttäjä](settings-radio-user) — Bluetooth- ja verkkoasetukset
- [Työpöytäsovellus](desktop) — työpöytäkohtaiset yhteystiedot
- [Tuetut laitteet](https://meshtastic.org/docs/hardware/devices) — täydellinen lista yhteensopivista radioista meshtastic.org -sivustolla

---

