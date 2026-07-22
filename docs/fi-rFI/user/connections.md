---
title: Yhteydet
parent: Käyttöopas
nav_order: 2
last_updated: 2026-07-08
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
3. Napauta **Hae bluetooth-laitteita** — lähellä olevat Meshtastic-radiot ilmestyvät näkyville.
4. Valitse laitteesi listasta.
5. Hyväksy Bluetooth-pariliitospyyntö, jos se tulee näkyviin.

![Bluetooth-laitteiden haku, jossa löytynyt radio näkyy luettelossa](../../assets/screenshots/connections_bluetooth_scan.png)

Vaihda bluetooth-, verkko- ja USB-yhteyksien välillä käyttämällä yhteyskortin alapuolella olevaa yhteysvalitsinta (vain yksi yhteystapa voi olla aktiivinen kerrallaan):

![Yhteysvalitsin](../../assets/screenshots/connections_transport_filters.png)

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

## TCP/IP (Verkko)

Jotkin Meshtastic-radiot tukevat WiFi tai Ethernet-yhteyttä, mikä mahdollistaa TCP-pohjaiset yhteydet lähiverkkosi kautta. Liitä radio ensin verkkoon käyttämällä radion omia WiFi-asetuksia (laiteohjelmiston verkkokäyttöliittymän tai muun yhteystavan kautta) — yhdistä siihen sitten sovelluksesta.

### Yhdistäminen verkon kautta

1. Varmista, että radio on samassa lähiverkossa kuin puhelimesi tai tietokoneesi.
2. Valitse yhdistä-näytössä yhteysvalitsimesta **Verkko**.
3. Valitse radio jommallakummalla seuraavista tavoista:
   - **Hae verkkolaitteita** — ota tämä käyttöön, jotta lähiverkossa itsensä ilmoittavat radiot löytyvät automaattisesti (mDNS / `_meshtastic._tcp`). Löydetyt laitteet näkyvät luettelossa; yhdistä napauttamalla haluamaasi laitetta.
   - **Lisää laite manuaalisesti** — anna radion IP-osoite (tai isäntänimi) ja portti (oletus: `4403`).
4. Aiemmin käytetyt verkko-osoitteet tallennetaan **Viimeisimmät verkkolaitteet** -osioon nopeaa uudelleenyhdistämistä varten (poista pitämällä painettuna).

> 💡 **Vinkki:** Verkkolaitteiden haku käyttää mDNS:ää, joka toimii vain, kun molemmat laitteet ovat samassa aliverkossa. Android 17:ssä ja uudemmissa versioissa sovellus tarvitsee lähiverkon käyttöoikeuden laitteiden hakuun. Jos haku ei löydä mitään, lisää laite manuaalisesti IP-osoitteella.

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

