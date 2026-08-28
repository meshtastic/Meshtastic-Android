---
title: Asetukset — Radio ja käyttäjä
parent: Käyttöopas
nav_order: 7
last_updated: 2026-08-27
description: Määritä radion laitteisto, LoRa-esiasetukset, käyttäjäprofiili, sijainnin jakaminen, virranhallinta ja tietoturva.
aliases:
  - asetukset
  - radion asetukset
  - käyttäjän asetukset
  - lora
---

# Asetukset — Radio ja käyttäjä

Määritä radion laitteisto ja käyttäjätunnistetiedot.

## Käyttäjäasetukset

### Käyttäjäprofiili

| Asetus                   | Kuvaus                                                                                                                                                                                                                                                                                        |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Pitkä nimi               | Näyttönimesi (enintään 39 merkkiä)                                                                                                                                                                                                                                         |
| Lyhytnimi                | 4-merkkinen lyhytnimi                                                                                                                                                                                                                                                                         |
| Ei vastaanota viestejä   | Merkitsee radion sellaiseksi, jolle kenenkään ei pitäisi yrittää lähettää viestejä – tarkoitettu valvomattomalle tai infrastruktuuriradiolle. Muut sovellukset piilottavat sen yhteystietoluettelosta. Edellyttää yhteensopivaa laiteohjelmistoa              |
| Lisensoitu radioamatööri | Ota käyttöön, jos sinulla on radioamatöörilupa (sallii suuremman lähetystehon). Käyttöönotto muuttaa **Pitkä nimi** -kentän **Kutsumerkki**-kentäksi ja lisää erillisen **Pitkä nimi** -kentän. Muutos vahvistetaan ensin valintaikkunassa |

### Muutosten käyttöönotto

Asetusten muuttamisen jälkeen napauta **Tallenna** kirjoittaaksesi määritykset radioon. Laite voidaan käynnistää uudelleen muutosten käyttöönottoa varten.

## Asetukset

### Laitteen asetukset

| Asetus                                     | Kuvaus                                                                                                                                                                                             | Oletus      |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| Rooli                                      | Radion rooli (Client, Router jne.) – jokaisella vaihtoehdolla on oma kuvaus valintaluettelossa. Reititin-tilan valitseminen pyytää vahvistuksen | Client      |
| Uudelleenlähetyksen tila                   | How the node retransmits messages; each mode is described in the picker                                                                                                                            | Kaikki      |
| Radiotiedon lähetys (s) | Radion tietojen lähetysväli                                                                                                                                                                        | 10800       |
| Kaksoisnapautuspainike                     | Treat a double tap as a button press                                                                                                                                                               | Ei käytössä |
| Kolmoisklikkaus Ad Hoc -pingille           | Send an ad-hoc position ping on a triple click                                                                                                                                                     | Ei käytössä |
| Ledin valvontasignaali                     | Blink the status LED periodically                                                                                                                                                                  | Käytössä    |
| Aikavyöhyke                                | POSIX time-zone string for the device clock, with buttons to copy your phone's zone or clear it                                                                                                    | —           |
| Button / Buzzer GPIO                       | Advanced: which pins the button and buzzer are wired to                                                                                                                            | —           |

### LoRa:n asetukset

| Asetus                                             | Kuvaus                                                                                                                                                                                           | Oletus                                           |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------ |
| Alue                                               | Taajuusalueiden sääntelyalue                                                                                                                                                                     | Ei asetettu (on määritettävä) |
| Modeemin esiasetus                                 | Nopeuden ja kantaman välinen kompromissi                                                                                                                                                         | LongFast                                         |
| Hyppyraja                                          | Suurin hyppyjen määrä                                                                                                                                                                            | 3                                                |
| Lähetysteho                                        | Lähetysteho (dBm): 0 = alueen sallima enimmäisteho                                                                                                            | 0 (alueen enimmäisteho)       |
| Taajuuden ohitus                                   | Overrides the computed operating frequency outright (MHz). It does not offset the calculated value — leave at 0 unless you know you need a specific frequency | 0 (use calculated)            |
| Kanavan kaistanleveys                              | Kaistanleveysasetus                                                                                                                                                                              | Esiasetuksen oletusarvo                          |
| Käytä esiasetusta                                  | On by default. Turn it off to set Spread Factor, Coding Rate and Bandwidth by hand instead of taking them from the modem preset                                                  | On                                               |
| Levennyskerroin (Spread Factor) | Manual mode only: 7–12. Higher spreads further but slower                                                                                                        | From preset                                      |
| Koodausnopeus                                      | Manual mode only: 5–8. More redundancy costs airtime                                                                                                             | From preset                                      |
| Taajuuspaikka                                      | Which slot within the region's band to use. 0 derives it from the primary channel name                                                                                           | 0 (automatic)                 |
| Lähetys käytössä                                   | Turning this off makes the node receive-only                                                                                                                                                     | On                                               |
| Ohita käyttöaste (Duty Cycle)   | Ignore the region's duty-cycle limit. Only legal where you are permitted to                                                                                                      | Pois                                             |
| Ohita MQTT                                         | Drop packets that arrived from MQTT rather than over the air                                                                                                                                     | Pois                                             |
| MQTT päällä                                        | Allow your packets to be forwarded to MQTT by gateways                                                                                                                                           | Pois                                             |
| RX tehostettu vahvistus                            | Extra receive gain on SX126x radios; costs a little current                                                                                                                                      | Pois                                             |
| PA tuuletin pois käytöstä                          | Turn off the power-amplifier fan on hardware that has one                                                                                                                                        | Pois                                             |

> ⚠️ **Tärkeää:** Sinun **täytyy** määrittää alueesi ennen lähettämistä. Lähettäminen väärällä alueasetuksella voi rikkoa paikallisia radiomääräyksiä. Katso [alueasetusten määritysopas](https://meshtastic.org/docs/getting-started/initial-config) meshtastic.org-sivustolta saadaksesi lisätietoja.

### Esiasetukset

> 💡 **Vinkki:** **SNR-raja**-arvot ovat tarkoituksella negatiivisia. LoRa pystyy purkamaan signaaleja _kohinatason alapuolelta_, joten negatiivisempi raja tarkoittaa, että esiasetus sietää heikomman ja kohinaisemman signaalin (suurempi kantama). Katso [Miten signaalimittari toimii](signal-meter) saadaksesi täydellisen selityksen.

| Esiasetus          | Kantama                 | Nopeus                    | SNR-raja                 | Paras käyttöön                                                                                                                     |
| ------------------ | ----------------------- | ------------------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| Short Turbo        | ~1 km   | 21.9 kbps | −7.5 dB  | Tiheä kaupunkiympäristö suoralla näköyhteydellä; paljon dataa siirtävät sovellukset                                                |
| Short Fast         | ~3 km   | 10.9 kbps | −7.5 dB  | Kaupunkialueet, rakennuksia muutaman korttelin säteellä                                                                            |
| Short Slow         | ~5 km   | 5.5 kbps  | −10 dB                   | Lyhyen kantaman esikaupunkialueet; kohtalainen rakennustiheys                                                                      |
| Medium Fast        | ~5 km   | 5.5 kbps  | −12.5 dB | Esikaupunkialueet; kohtalainen rakennustiheys                                                                                      |
| Medium Slow        | ~8 km   | 1.1 kbps  | −15 dB                   | Esikaupunki-/maaseutualueet; kohtalainen kantama ja hitaampi nopeus                                                                |
| Long Turbo         | ~10 km  | 4.4 kbps  | −12.5 dB | Samankaltainen kantama kuin Long Fast -asetuksella, mutta 500 kHz:n kaistanleveydellä; suurempi tiedonsiirtonopeus |
| Long Fast          | ~10 km  | 1.1 kbps  | −17.5 dB | **Yleiskäyttö (oletus)** — tasapaino kantaman ja nopeuden välillä                                               |
| Long Moderate      | ~20 km  | 0.34 kbps | −17.5 dB | Maaseutualueet, joissa on jonkin verran maastonmuotoja; satunnainen käyttö                                                         |
| Lite Fast          | ~5 km   | 5.5 kbps  | −12.5 dB | EU 866 MHz SRD -alue (125 kHz BW); verrattavissa Medium Fast -asetukseen                                        |
| Lite Slow          | ~10 km  | 1.1 kbps  | −15 dB                   | EU 866 MHz SRD -alue (125 kHz BW); verrattavissa Long Fast -asetukseen                                          |
| Narrow Fast        | ~5 km   | 2.7 kbps  | −10 dB                   | EU 868 MHz -alue (62,5 kHz BW); välttää häiriöitä muiden laitteiden kanssa                                      |
| Narrow Slow        | ~10 km  | 1.1 kbps  | −12.5 dB | EU 868 MHz -alue (62,5 kHz BW); verrattavissa Long Fast -asetukseen                                             |
| ~~Long Slow~~      | ~30 km  | 0.18 kbps | −20 dB                   | ⚠️ **Vanhentunut** — edelleen valittavissa, mutta voidaan poistaa tulevassa laiteohjelmistoversiossa                               |
| ~~Very Long Slow~~ | ~40+ km | 0.09 kbps | −20 dB                   | ⚠️ **Vanhentunut** — edelleen valittavissa, mutta voidaan poistaa tulevassa laiteohjelmistoversiossa                               |

> ℹ️ **Huomautus:** Tässä taulukossa käytetään yleisesti käytössä olevia lyhyitä nimiä. Sovelluksen esiasetusvalikossa ne näkyvät nimillä **Lyhyt kantama - Nopea**, **Pitkä kantama - Nopea**, **Lite - Nopea**, **Kapea - Nopea** ja niin edelleen.

#### Modeemiesiasetuksen valitseminen

Modeemiesiasetus määrittää tärkeimmän kompromissin **kantaman** ja **tiedonsiirtonopeuden** välillä:

- **Hitaammat esiasetukset** käyttävät enemmän hajautusta, jolloin signaali voidaan purkaa heikommilla signaalitasoilla (alempi SNR-raja). Tämä tarkoittaa pidempää kantamaa, mutta vähemmän tavuja sekunnissa.
- **Nopeammat esiasetukset** siirtävät enemmän dataa, mutta vaativat vahvemman signaalin purkamista varten.

**Käytännön ohje:**

- **Kaupunkiverkko (paljon radioita, lyhyet etäisyydet):** Käytä **Long Fast** -asetusta (oletus) tai **Short Fast** -asetusta. Suurempi nopeus tarkoittaa vähemmän käyttöasteruuhkaa, kun monet radiot jakavat saman kanavan.
- **Maaseutu tai harva verkko (vähän radioita, pitkät etäisyydet):** Käytä **Long Moderate** -asetusta. Kantama on tärkeämpi kuin nopeus, kun radiot ovat kaukana toisistaan.
- **EU 866/868 MHz -alueen säädösten noudattaminen:** Käytä **Lite Fast**, **Lite Slow**, **Narrow Fast** tai **Narrow Slow** -asetuksia — ne on optimoitu EU:n SRD/868 MHz -alueille kapeammilla kaistanleveyksillä.
- **Kiinteät infrastruktuurilinkit:** Käytä **Short Turbo**- tai **Long Turbo** -asetusta erillisille pisteestä pisteeseen -linkeille, joissa on hyvät antennit ja suora näköyhteys.
- **Sekaverkot:** Pysy **Long Fast** -asetuksessa — se on yhteisön oletusasetus ja varmistaa yhteensopivuuden alueesi muiden käyttäjien kanssa.

> ⚠️ **Tärkeää:** Kaikkien samalla kanavalla olevien radioiden **täytyy** käyttää samaa modeemiesiasetusta. Radiot, joiden modeemiesiasetukset eivät täsmää, eivät voi viestiä keskenään, vaikka ne käyttäisivät samaa taajuutta ja salausavainta.

> 💡 **Vinkki:** Yllä olevat kantama-arviot perustuvat tasaiseen maastoon ja vaatimattomiin antenneihin. Korkeuseroetu (mäki, rakennuksen katto) kasvattaa käytännön kantamaa huomattavasti. Hyvin sijoitettu Long Fast -asetusta käyttävä Router voi usein toimia paremmin kuin maan tasalla oleva Long Slow -asetusta käyttävä radio.

### Näytön asetukset

These control the **radio's own screen**, not the app's.

| Asetus                              | Kuvaus                                                                                                                                                    |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Näytön päälläoloaika                | How long the display stays lit before sleeping                                                                                                            |
| Karusellin aikaväli                 | How often the device cycles between screens on its own                                                                                                    |
| Näyttötila                          | Screen layout/density used by the firmware                                                                                                                |
| Näyttöyksiköt                       | Metric or Imperial on the device's screen                                                                                                                 |
| Käytä 12 tunnin kelloa              | Show the device clock as 12-hour rather than 24-hour                                                                                                      |
| Bold heading                        | Draw the screen's heading text in bold                                                                                                                    |
| Käännä näyttö                       | Rotate the display 180° for an inverted mounting                                                                                                          |
| OLED-tyyppi                         | Auto, SSD1306, SH1106, SH1107                                                                                                                             |
| Herätä napautuksesta tai liikkeestä | Light the screen when the device is tapped or moved                                                                                                       |
| Kompassin suuntaus                  | Rotation offset for the compass rose (0°, 90°, 180°, 270°)                                                                             |
| Osoita aina pohjoiseen              | Locks the compass rose north-up instead of rotating it with your heading. Independent of Compass orientation — neither replaces the other |

### Sijainnin asetukset

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Asetus                                            | Kuvaus                                                                                                                                                |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| GPS-tila (fyysinen laitteisto) | Three-state: GPS enabled, disabled, or not present. Not a simple on/off                                               |
| GPS-kyselyn aikaväli                              | How often the radio asks its GPS for a fix                                                                                                            |
| Lähetyksen aikaväli                               | How often the position is shared with the mesh                                                                                                        |
| Älykäs sijainti                                   | Broadcast based on movement rather than purely on the clock                                                                                           |
| Älykäs aikaväli                                   | With Smart Position on, the shortest gap between broadcasts                                                                                           |
| Älykäs etäisyys                                   | With Smart Position on, how far you must move before broadcasting                                                                                     |
| Kiinteä sijainti                                  | Use a manually entered latitude, longitude and altitude instead of the GPS                                                                            |
| Sijaintimerkinnät                                 | A group of toggles choosing which fields ride along with a position — altitude, its reference and precision, satellites in view, timestamp, and so on |
| GPS EN / Receive / Transmit GPIO                  | Advanced: the pins the GPS module is wired to                                                                                         |

### Virran asetukset

| Asetus                                                      | Kuvaus                                                          |
| ----------------------------------------------------------- | --------------------------------------------------------------- |
| Ota virransäästötila käyttöön                               | Let the radio sleep aggressively between activity               |
| Sammuta virran katketessa                                   | Power the device down after external power disappears           |
| Super-syväunen kesto                                        | How long the deepest sleep state lasts                          |
| Vähimmäisherätyksen kesto                                   | The shortest time the radio stays awake once woken              |
| Bluetoothin odotusaika                                      | How long to wait for a phone to connect before sleeping         |
| ADC-kertoimen ohitus                                        | Turn on a manual correction for battery-voltage readings        |
| Korvaava AD-muuntimen kerroin                               | The correction factor itself, used only when the override is on |
| INA_2XX-akun valvontapiirin I2C-osoite | Address of an external INA-series power sensor, if fitted       |

### Verkon asetukset

> ⚠️ **Warning:** Saving this screen always reboots the radio.

| Asetus                           | Kuvaus                                                                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| WiFi käytössä                    | Enable the WiFi radio (ESP32 devices)                                                                   |
| SSID                             | Network name to connect to. **Scan WiFi QR code** fills this and the password from a standard WiFi QR code |
| Salasana                         | Verkon salasana                                                                                                            |
| Ethernet käytössä                | Use a wired connection on hardware that has one                                                                            |
| IPv4-tila                        | DHCP, or a static address configured with the four fields below                                                            |
| Wifi IP / Subnet / Gateway / DNS | The static address, only used when IPv4 mode is static                                                                     |
| UDP-lähetys                      | Share mesh traffic with other nodes over the local network                                                                 |
| NTP palvelin                     | Ajan synkronointipalvelin (NTP-palvelin)                                                                |
| rsyslog-palvelin                 | Etätietojen palvelin                                                                                                       |

![IP-osoitekenttä](../../assets/screenshots/settings_ipv4_field.png)

### Bluetooth asetukset

| Asetus             | Kuvaus                                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| Bluetooth käytössä | Ota Bluetooth-radio käyttöön tai poista käytöstä                                                       |
| Pariliitostila     | Kiinteä PIN-koodi, satunnainen PIN-koodi tai ei PIN-koodia                                             |
| Kiinteä PIN-koodi  | PIN code for pairing. Must be **exactly six digits** — the field rejects anything else |

### Turvallisuusasetukset

| Asetus                      | Kuvaus                                                                                                                                                                                                                                        |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Julkinen avain              | Radiosi julkinen avain (vain luku)                                                                                                                                                                                         |
| Ylläpitäjän avain           | Keys permitted to administer this node remotely — up to three                                                                                                                                                                                 |
| Yksityinen avain            | Your node's private key (handle securely). Shown redacted when you are viewing another node over remote admin — the firmware does not send it                                                              |
| Luo uusi yksityinen avain   | Issues a new keypair for this node, behind a confirmation. Every peer that knew your old key must learn the new one                                                                                                           |
| Suoran viestin avain        | The key used for direct-message encryption                                                                                                                                                                                                    |
| ~~Ylläpitokanava käytössä~~ | ⚠️ Poistettu — määritetään nyt automaattisesti, kun ylläpitoavain asetetaan                                                                                                                                                                   |
| Virheenkorjausloki          | Tulosta reaaliaikainen virheenkorjausloki sarjaportin tai bluetoothin kautta                                                                                                                                                                  |
| Sarjaportti käytössä        | Ota sarjakonsoliyhteys käyttöön (siirretty laiteasetuksista)                                                                                                                                                               |
| Hallintatila                | Restrict non-admin channel changes. Only selectable once an Admin Key is set                                                                                                                                                  |
| Varmuuskopioi avaimet       | Tallenna radion avaimista salattu varmuuskopio tälle laitteelle (vain Android)                                                                                                                                             |
| Palauta avaimet             | Kirjoita varmuuskopioidut avaimet takaisin radioon (käytettävissä, kun varmuuskopio on olemassa)                                                                                                                           |
| Poista avaimen varmuuskopio | Poista tälle laitteelle tallennettu avainten varmuuskopio                                                                                                                                                                                     |
| Suojaustaso                 | Pakettien aitous – miten allekirjoittamattomia tai välitettyjä paketteja käsitellään: **Tiukka**, **Tasapainoinen** tai **Yhteensopiva** (edellyttää tuettua laiteohjelmistoa; Tiukka pyytää vahvistuksen) |

#### Lockdown Mode

Lockdown encrypts the device's storage and requires a passphrase for each connection. It needs
supporting firmware; the row does not appear otherwise.

Enabling it asks you to set and confirm a passphrase, and to acknowledge that **it locks the debug
(SWD) port on hardware that supports locking**. You can turn lockdown off again at any time with
the passphrase, and a full device erase restores the hardware regardless.

Alongside the passphrase you set the limits that end a session automatically:

| Field                                                 | Mitä se tekee                                     |
| ----------------------------------------------------- | ------------------------------------------------- |
| Jäljellä olevat käynnistykset                         | How many device boots the unlocked state survives |
| Tuntia vanhenemiseen saakka                           | Wall-clock lifetime of the unlocked state         |
| Istunnon enimmäiskesto (minuuttia) | Maximum length of a single unlocked connection    |

Once active, the row reads _Active — storage encrypted, this connection authenticated_ when
unlocked, or _Active — enter your passphrase to unlock this connection_ when not. **Lock Now**
ends the current session immediately. Repeated wrong passphrases are rate-limited with a
back-off before you can try again.

> ⚠️ **Warning:** There is no passphrase recovery. Losing it means erasing the device to get it
> back, which destroys its keys, channels and settings.

![Salasanakenttä](../../assets/screenshots/settings_password_field.png)

Asetukset käyttävät tavallisia asetussäätimiä — pudotusvalikoita, kytkimiä ja liukusäätimiä:

| Säädin         | Kuvakaappaus                                                      |
| -------------- | ----------------------------------------------------------------- |
| Pudotusvalikko | ![Pudotusvalikko](../../assets/screenshots/settings_dropdown.png) |
| Kytkin         | ![Kytkin](../../assets/screenshots/settings_switch.png)           |
| Liukusäädin    | ![Liukusäädin](../../assets/screenshots/settings_slider.png)      |

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — valinnaiset ominaisuusmoduulit ja laitteen ylläpitotoiminnot
- [Signaalimittari](signal-meter) — miten modeemiesiasetukset vaikuttavat signaalin laadun raja-arvoihin
- [LoRa-määritykset](https://meshtastic.org/docs/configuration/radio/lora) — yksityiskohtainen LoRa-asetusten viite meshtastic.org-sivustolla
- [Alkumääritykset](https://meshtastic.org/docs/getting-started/initial-config) — alueasetusten määritysopas meshtastic.org-sivustolla

---

