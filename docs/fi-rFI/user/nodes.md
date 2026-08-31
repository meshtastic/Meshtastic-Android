---
title: Laitteet
parent: Käyttöopas
nav_order: 4
last_updated: 2026-08-30
description: Selaa, suodata ja lajittele verkon radioita — tarkastele tietoja, signaalin laatua, rooleja ja pikatoimintoja.
aliases:
  - radiolista
  - mesh-radiot
  - vertaisradiot
  - Hyppymääräjakauma
---

# Laitteet

Radionäkymässä luetellaan kaikki mesh-verkossasi näkyvät radiot.

## Radiolista

Radioluettelo näyttää kaikki radiot, joista radiosi on vastaanottanut tietoja, mukaan lukien:

- **Radion nimi** — käyttäjän määrittämä pitkä nimi
- **Lyhyt nimi** — 4-merkkinen tunniste
- **Signal quality** — SNR, RSSI, and a quality word, shown only for nodes your radio heard directly. In the Complete layout a node reached through a relay shows its hop count here instead; a node heard only over MQTT shows neither
- **Viimeksi kuultu** — aika viimeisimmästä yhteydestä
- **Etäisyys** — arvioitu etäisyys (jos sijaintitiedot jaetaan)
- **Akku** — etäradion akun varaustaso (jos telemetria on käytössä)

### Choosing What the List Shows

The list has two densities, set at **Settings → Node Layout**. **Complete** shows every field a node has reported and hides the ones it hasn't. **Compact** fits more nodes on screen and lets you pick the fields yourself — **Power**, **Last Heard Time**, **Relative Last Heard Time**, **Distance and Bearing**, **Hops Away**, **Signal (Direct Only)**, **Channel**, and **Device & Role**. The **Environment Metrics** toggle applies to both densities. A preview above the toggles shows the effect before you leave the screen.

### Radion tilailmaisimet

| Ilmaisin              | Tarkoitus                                               |
| --------------------- | ------------------------------------------------------- |
| Green last-heard time | Radio kuultu viimeisen 2 tunnin aikana                  |
| Plain last-heard time | Radiosta ei ole kuultu yli 2 tuntiin                    |
| ⭐ Suosikki            | Radio, jonka olet merkinnyt suosikiksi. |

Erillistä "poissa"-tilaa ei ole.

### Radion roolit

Radioille voidaan määrittää erilaisia rooleja, jotka vaikuttavat niiden toimintaan verkossa:

| Rooli             | Kuvaus                                                                                                                                                                         |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Client            | Tavallinen käyttäjäradio                                                                                                                                                       |
| Client Base       | Käsittelee suosikkiradioiden liikenteen Router Late -prioriteetilla, kaiken muun liikenteen Client -prioriteetilla                                                             |
| Client Mute       | Vastaanottaa viestejä, mutta ei lähetä niitä edelleen                                                                                                                          |
| Client Hidden     | Kuten Client Mute, mutta piilotetaan myös radioluettelosta                                                                                                                     |
| Router            | Priorisoi viestien välittämistä; pysyy hereillä välittääkseen viestejä                                                                                                         |
| Router Late       | Infrastruktuuriradio, joka lähettää viestin uudelleen kerran, mutta vasta kaikkien muiden tilojen jälkeen (tarjoaa lisäpeittoa)                             |
| ~~Router Client~~ | ⚠️ **Vanhentunut** (poistettu laiteohjelmistossa 2.3.15) — ei enää valittavissa; käytä sen sijaan Router- tai Client-roolia |
| ~~Repeater~~      | ⚠️ **Vanhentunut** (poistettu laiteohjelmistossa 2.7.11) — ei enää valittavissa; käytä sen sijaan Router-roolia             |
| Tracker           | Optimoitu sijainnin raportointiin säännöllisin väliajoin                                                                                                                       |
| Sensor            | Optimoitu telemetrian raportointiin                                                                                                                                            |
| TAK               | Yhteensopiva TAK-järjestelmien kanssa (lähettää ja vastaanottaa CoT-viestejä)                                                                               |
| TAK Tracker       | Vain TAK-sijainnin raportointi                                                                                                                                                 |
| Lost and Found    | Sends its position to the default channel as a text message at regular intervals, to help recover a lost radio                                                                 |

### Roolin valitseminen

Useimpien käyttäjien kannattaa käyttää oletusarvoista **Client**-roolia. Harkitse muuta roolia seuraavissa tilanteissa:

- **Router** — Sinulla on radio kiinteässä, korkealla sijaitsevassa paikassa, jossa on luotettava virransyöttö (katto, mäki). Routerit pysyvät jatkuvasti hereillä välittääkseen muiden viestejä ja ovat tärkeitä verkon peittoalueen laajentamisessa. Älä käytä Reititin-roolia akkukäyttöisissä käsiradioissa.
- **Router Late** — Infrastruktuuriradio, joka lähettää paketit uudelleen kerran, mutta vasta kaikkien muiden reititystilojen jälkeen. Tarjoaa lisäpeittoa paikallisille ryhmille kilpailematta ensisijaisten Routerien kanssa.
- **Client Base** — Käsittelee suosikkiradioihisi menevän tai niistä tulevan liikenteen Router Late -prioriteetilla (varmistaen näille viesteille ylimääräisen välityspeiton), samalla kun kaikki muu käsitellään tavallisen Client-roolin tavoin.
- **Client Mute** — Voit vastaanottaa verkkoliikennettä, mutta et osallistu viestien välittämiseen. Hyödyllinen vain kuunteluun tarkoitetuissa radioissa tai ruuhkan vähentämiseen tiheillä alueilla.
- **Tracker** "seurantalaite" — miehittämätön radio, jonka ainoa tehtävä on lähettää GPS-sijaintiaan (esimerkiksi ajoneuvo, henkilö tai muu kohde). Nukkuu lähetysten välillä akun säästämiseksi.
- **Anturi** — miehittämätön radio, joka lähettää ympäristötelemetriaa (lämpötila, ilmankosteus, ilmanlaatu). Samanlainen virrankulutusprofiili kuin Tracker-roolissa.
- **TAK / TAK Tracker** — Tarvitaan vain yhteensopivuuteen ATAK-/WinTAK-järjestelmien kanssa. Katso [TAK-integraatio](tak) lisätietoja varten.

> 💡 **Vinkki:** Verkko toimii parhaiten, kun suurin osa radioista käyttää **Client**- tai **Router**-roolia. Liian suuri määrä Client Mute (mykistetty) -radioita heikentää mesh-verkon vikasietoisuutta. Liian useat Router -roolin radiot tiheällä alueella voivat aiheuttaa ruuhkaa. Hyvä nyrkkisääntö on yksi Router jokaista 5–10 Client-roolia kohden alueellasi.

### Salausilmaisimet

Radiot näyttävät nimensä vieressä salauksen tilaa kuvaavat kuvakkeet:

| Kuvake          | Merkitys                                                                                                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 🔒 Lukittu      | Yhteys käyttää PKI:tä (julkisen avaimen infrastruktuuria) — päästä päähän salattu ja varmennetulla identiteetillä suojattu. |
| 🔓 Lukitsematon | Yhteys käyttää PKI:tä (julkisen avaimen infrastruktuuria) — päästä päähän salattu ja varmennetulla identiteetillä suojattu. |
| ⚠️ Ei täsmää    | Julkinen avain ei täsmää — radion avain on muuttunut viime näkemän jälkeen (tutki ennen luottamista).                                       |

> 💡 **Vinkki:** PKI-salaus (laiteohjelmisto 2.5+) tarjoaa vahvemman suojauksen kuin kanavan PSK-avain, koska jokaisella radiolla on oma yksilöllinen avainparinsa. Jos näet avaimen täsmäämättömyysvaroituksen, radio on voitu nollata tai sen tietoturva on voinut vaarantua.

To clear a mismatch, first confirm through another trusted channel that the key change was intentional — a factory reset causes one. Then touch & hold the node, choose **Remove**, and let the two radios exchange keys again the next time yours hears it.

## Pikatoiminnot

Radioluettelosta voit:

- **Napauttaa** radiota avataksesi sen tietosivun
- **Kosketa ja pidä painettuna** nähdäksesi pikatoiminnot:
  - Merkitse tai poista suosikki
  - Mykistä tai poista mykistys ilmoituksista
  - Lähetä yksityisviesti
  - Reitinselvitys
  - Ohita tai poista ohitus
  - Poista

## Sharing a Contact

On a node's detail screen, tap **Share Contact** to produce a link and a QR code for that node. From the same dialog, **Write to NFC tag** saves the link to a writable NFC tag that anyone can tap to open.

To add someone else's contact, use the import button on the node list and choose **Scan Shared Contact QR Code**, **Scan Shared Contact NFC**, or **Input Shared Contact URL**. The app asks you to confirm with **Import Shared Contact?**, and warns you when the contact is one you already have.

## Suodatus ja lajittelu

### Tekstihaku

Kirjoita hakukenttään suodattaaksesi radioita nimen tai lyhyen nimen perusteella. Suodatus päivittyy reaaliajassa kirjoittaessasi.

### Suodatusvalinnat

| Suodatus                          | Kuvaus                                                                                                                                                                                                                                                                                |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Hide offline nodes**            | Näytä vain radiot, joista on kuultu viimeisten 2 tunnin aikana                                                                                                                                                                                                                        |
| **Only show direct nodes**        | Show only nodes your radio heard directly, with no relay in between                                                                                                                                                                                                                   |
| **Näytä tuntemattomat**           | Näytä radiot, jotka eivät ole vielä lähettäneet käyttäjätietoja. **Oletuksena käytössä**, joten radio, joka on kuultu ennen käyttäjätietojensa saapumista, pysyy näkyvissä ja sille voi lähettää viestejä. Tällaiset radiot merkitään keskeneräisiksi |
| **Ohita infrastruktuurilaitteet** | Hide infrastructure-role nodes (Router, Router Late, Client Base, and legacy Repeater nodes) and any node that cannot be messaged, whatever its role                                                                                                               |
| **Rajaa MQTT pois**               | Piilottaa radiot, joista on kuultu vain MQTT-internetsillan kautta                                                                                                                                                                                                                    |
| **Only show ignored Nodes**       | Replace the list with the nodes you have ignored. Every other node is hidden while this is on, and a banner appears at the top of the list to take you back                                                                                                           |

### Lajitteluvaihtoehdot

| Lajittelu                                     | Kuvaus                                                                            |
| --------------------------------------------- | --------------------------------------------------------------------------------- |
| **Last heard**                                | Näytä viimeksi kuullut radiot ensin                                               |
| **A-Z**                                       | Lajiteltu radion pitkän nimen mukaan                                              |
| **Etäisyys**                                  | Lähimpänä olevat radiot ensin (edellyttää sijainnin jakamista) |
| **Hyppyjä**                                   | Vähiten välityshyppyjä vaativat radiot ensin                                      |
| **Kanava**                                    | Ryhmitelty kanavaindeksin mukaan                                                  |
| **via MQTT**                                  | Ryhmitelty MQTT:n kautta kuultuihin ja radiolla kuultuihin        |
| **via Favorite** (default) | Favorited nodes first, then the rest                                              |

## Radiot hyppymäärän mukaan

Avaa pylväskaavio, joka näyttää radioiden määrän kullakin hyppyetäisyydellä, napauttamalla radioluettelon sovelluspalkissa olevaa hyppyhistogrammikuvaketta (0 = suora yhteys, 1 = yksi välityshyppy ja niin edelleen). Suodata kaavio **Viimeksi kuultu** -ajanjakson mukaan — Kaikki ajat, 1 tunti, 8 tuntia tai 24 tuntia — nähdäksesi, miltä mesh-verkko näyttää juuri nyt verrattuna pidempään ajanjaksoon. Tämä on nopea tapa arvioida, kuinka laaja ja kuormittunut paikallinen mesh-verkkosi on.

## Radion tiedot

Radion napauttaminen avaa tietonäkymän, jossa on kattavat tiedot. Katso [Radion mittarit](node-metrics) saadaksesi täydelliset tiedot mittareista ja telemetriasta.

The Details card carries the node's short name, role, IDs, last heard time, hops away, uptime, and its SNR and RSSI:

![Radion tietonäkymän osio](../../assets/screenshots/nodes_detail_section.png)

Rivinsisäiset tilailmaisimet näyttävät tärkeimmät tiedot yhdellä silmäyksellä:

| Ilmaisin        | Kuvakaappaus                                                      |
| --------------- | ----------------------------------------------------------------- |
| Signaalin laatu | ![Signaali](../../assets/screenshots/nodes_signal_info.png)       |
| Akun varaus     | ![Akku](../../assets/screenshots/nodes_battery_info.png)          |
| Hyppymäärä      | ![Hypyt](../../assets/screenshots/nodes_hops_info.png)            |
| Viimeksi kuultu | ![Viimeksi kuultu](../../assets/screenshots/nodes_last_heard.png) |
| Etäisyys        | ![Etäisyys](../../assets/screenshots/nodes_distance_info.png)     |

### Laite-linkit ("Haluan sellaisen")

Kun radion laitteisto tunnistetaan, tietonäkymä näyttää avattavan **"Haluan sellaisen"** -osion, jossa on linkkejä laitteen ostamiseen tai lisätietojen hankkimiseen: valmistajan tuotesivu, tuoteversiot sekä alueelliset kauppapaikkalistaukset (esim. AliExpress, Amazon ja tuetut jälleenmyyjät), suodatettuna maasi mukaan. Jokainen linkki avautuu mesh.to -uudelleenohjauspalvelun kautta. Laitteet, joille ei löydy vastaavia linkkejä, eivät näytä tätä osiota.

A full, browsable directory of every link is also available at **Settings → Device Links**. The item is hidden while you have Settings open for a remote node.

## When No Nodes Appear

The list stays empty until your radio hears another node.

- **No device connected** — the app is not connected to a radio. See [Connections](connections).
- **Searching for nodes** — the radio is connected and listening, but nothing has arrived yet. Check that its region and modem preset match the mesh around you, and leave **Include unknown** on so a node that has not yet sent its name still appears. See [Settings — Radio & User](settings-radio-user).
- A node you expect is missing — check the filter toggles. **Only show direct nodes**, **Exclude MQTT**, and **Exclude infrastructure** each hide a whole category of node.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — yksityiskohtaiset telemetriakoontinäytöt jokaiselle radiolle
- [Viestit ja kanavat](messages-and-channels) — lähetä yksityisviesti radiolle
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja kartalla
- [Paikallinen mesh-verkon etsintä](discovery) — reitiselvitys ja naapuritiedot verkon rakenteen hahmottamiseen
- [Signaalimittari](signal-meter) — ymmärrä, mitä signaalipalkit tarkoittavat
