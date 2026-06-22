---
title: Yhteydet
parent: Käyttöopas
nav_order: 2
last_updated: 2026-05-20
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

![Laiteluettelon kohde](../../assets/screenshots/connections_bluetooth_scan.png)

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

## TCP/IP (WiFi)

Jotkin Meshtastic-radiot tukevat WiFi-yhteyttä, jolloin yhteys voidaan muodostaa TCP:n kautta.

### Asetukset

1. Yhdistä radio WiFi-verkkoon radion web-käyttöliittymän tai asetusten kautta.
2. Sovelluksessa siirry kohtaan **Yhdistä → TCP**.
3. Syötä radion IP-osoite ja portti (oletus: 4403).
4. Paina **Yhdistä**.

![WiFi-laitteiden haku](../../assets/screenshots/connections_wifi_scanning.png)

Kun laite löytyy, se näkyy yhteyslistassa:

![WiFi-laite löytyi](../../assets/screenshots/connections_wifi_device_found.png)

Onnistunut yhteys vahvistetaan tilailmaisimella:

![WiFi-yhteys onnistui](../../assets/screenshots/connections_wifi_success.png)

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

