---
title: Yhteydet
parent: Käyttöopas
nav_order: 2
last_updated: 2026-08-27
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

> 💡 **Vinkki:** Jos laitettasi ei näy, tarkista, ettei radio ole jo yhdistetty toiseen laitteeseen tai kantaman ulkopuolella.

Näytössä kerrotaan kaikki sovelluksen puolella olevat syyt, jotka estävät haun, sekä niiden ratkaisut:

| Näet seuraavan                                                           | Mitä se tarkoittaa                                                                                                                                                                           |
| ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kortti pyytää **Lähistön laitteet** -käyttöoikeutta                      | Käyttöoikeutta ei ole myönnetty. **Myönnä käyttöoikeus** pyytää sitä. Kun Android lakkaa kysymästä, painikkeeksi muuttuu **Avaa asetukset**. |
| **Bluetooth on pois käytöstä**                                           | Bluetooth-sovitin on poistettu käytöstä – kortti avaa Bluetooth-asetukset.                                                                                                   |
| **Bluetooth-laitteiden haku edellyttää myös sijaintipalvelujen käyttöä** | Vain Android 11:ssä ja vanhemmissa versioissa: käyttöoikeus on myönnetty, mutta järjestelmän sijaintipalvelut ovat pois käytöstä.            |
| Ei korttia, tyhjä luettelo                                               | Mikään sovelluksen puolella ei estä hakua – radio on kantaman ulkopuolella, pois käytöstä tai jo yhdistetty toiseen laitteeseen.                                             |

Kun napautat **Hae** sen jälkeen, kun olet kerran hylännyt käyttöoikeuspyynnön, sovellus kertoo ensin, mihin käyttöoikeutta tarvitaan, ennen kuin pyytää sitä uudelleen. Näin voit myös kieltäytyä rauhassa ilman painostusta.

### Yhteyden tila

| Ikoni | Tila                 | Kuvaus                                                                                     |
| ----- | -------------------- | ------------------------------------------------------------------------------------------ |
| 🟢    | Yhdistetty           | Aktiivinen radiolinkki muodostettu                                                         |
| 🟡    | Yhdistetään          | Yhteyden muodostus käynnissä                                                               |
| 🔴    | Ei yhdistetty        | No active connection; the app keeps trying to reconnect                                    |
| ⚪     | Laite on lepotilassa | The radio is in light sleep — the app is waiting for it to wake and reconnect, not failing |

These are the four states the app models. "Device sleeping" is normal on power-saving configurations and needs no action.

When connecting, a status indicator shows the current connection state — tap **Stop Connecting** to abandon the attempt:

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

> ℹ️ **Note:** USB connections require OTG support on Android devices.

## TCP/IP (Verkko)

Jotkin Meshtastic-radiot tukevat WiFi tai Ethernet-yhteyttä, mikä mahdollistaa TCP-pohjaiset yhteydet lähiverkkosi kautta. Liitä radio ensin verkkoon käyttämällä radion omia WiFi-asetuksia (laiteohjelmiston verkkokäyttöliittymän tai muun yhteystavan kautta) — yhdistä siihen sitten sovelluksesta.

> ℹ️ **Note:** **Settings → Wi-Fi Provisioning for mPWRD-OS** is a separate, narrower tool. It provisions WiFi
> credentials over Bluetooth to **mPWRD-OS** devices only, using their own protocol — it does not
> configure WiFi on an ordinary Meshtastic radio. It scans over BLE, lists the networks the device
> can see (including an option for a hidden SSID), takes the password, and reports success or
> failure. Available on both Android and Desktop.

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

