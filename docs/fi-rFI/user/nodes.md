---
title: Laitteet
parent: Käyttöopas
nav_order: 4
last_updated: 2026-07-08
description: Selaa, suodata ja lajittele verkon radioita — tarkastele tietoja, signaalin laatua, rooleja ja pikatoimintoja.
aliases:
  - radiolista
  - mesh-radiot
  - vertaisradiot
  - Hyppymääräjakauma
---

# Laitteet

Radiot-näyttö näyttää kaikki verkossasi näkyvät laitteet.

## Radiolista

Radioluettelo näyttää kaikki radiot, joista radiosi on vastaanottanut tietoja, mukaan lukien:

- **Radion nimi** — käyttäjän määrittämä pitkä nimi
- **Lyhyt nimi** — 4-merkkinen tunniste
- **Signaalin laatu** — viimeksi vastaanotetun signaalin voimakkuus
- **Viimeksi kuultu** — aika viimeisimmästä yhteydestä
- **Etäisyys** — arvioitu etäisyys (jos sijaintitiedot jaetaan)
- **Akku** — etäradion akun varaustaso (jos telemetria on käytössä)

### Radion tilailmaisimet

| Tunniste     | Tarkoitus                              |
| ------------ | -------------------------------------- |
| 🟢 Verkossa  | Radio kuultu viimeisen 2 tunnin aikana |
| Ei verkkossa | Radiosta ei ole kuultu yli 2 tuntiin   |
| ⭐ Suosikki   | Käyttäjän suosikiksi merkitsemä radio  |

Radio katsotaan **verkossa olevaksi**, jos siitä on kuultu viimeisten 2 tunnin aikana. Muussa tapauksessa se katsotaan **poissa verkosta** olevaksi — erillistä "poissa"-tilaa ei ole.

### Radion roolit

Radioille voidaan määrittää erilaisia rooleja, jotka vaikuttavat niiden toimintaan verkossa:

| Rooli                | Kuvaus                                                                                                                                                                         |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Client               | Tavallinen loppukäyttäjän laite                                                                                                                                                |
| Client Base          | Käsittelee suosikkiradioiden liikenteen Router Late -prioriteetilla, kaiken muun liikenteen Client -prioriteetilla                                                             |
| Client Mute          | Vastaanottaa viestejä, mutta ei lähetä niitä edelleen                                                                                                                          |
| Client Hidden        | Kuten Client Mute, mutta piilotetaan myös radioluettelosta                                                                                                                     |
| Router               | Priorisoi viestien välittämistä; pysyy hereillä välittääkseen viestejä                                                                                                         |
| Router Late          | Infrastruktuuriradio, joka lähettää viestin uudelleen kerran, mutta vasta kaikkien muiden tilojen jälkeen (tarjoaa lisäpeittoa)                             |
| ~~Router Client~~    | ⚠️ **Vanhentunut** (poistettu laiteohjelmistossa 2.3.15) — ei enää valittavissa; käytä sen sijaan Router- tai Client-roolia |
| ~~Repeater~~         | ⚠️ **Vanhentunut** (poistettu laiteohjelmistossa 2.7.11) — ei enää valittavissa; käytä sen sijaan Router-roolia             |
| Tracker              | Optimoitu sijainnin raportointiin säännöllisin väliajoin                                                                                                                       |
| Sensor               | Optimoitu telemetrian raportointiin                                                                                                                                            |
| TAK                  | Yhteensopiva TAK-järjestelmien kanssa (lähettää ja vastaanottaa CoT-viestejä)                                                                               |
| TAK Tracker          | Vain TAK-sijainnin raportointi                                                                                                                                                 |
| Kadonnut ja löydetty | Jatkuva sijaintimajakka löytämistä varten                                                                                                                                      |

### Roolin valitseminen

Useimpien käyttäjien kannattaa käyttää oletusarvoista **Client**-roolia. Harkitse muuta roolia seuraavissa tilanteissa:

- **Router** — Sinulla on radio kiinteässä, korkealla sijaitsevassa paikassa, jossa on luotettava virransyöttö (katto, mäki). Routerit pysyvät jatkuvasti hereillä välittääkseen muiden viestejä ja ovat tärkeitä verkon peittoalueen laajentamisessa. Älä käytä **Router**-roolia akkukäyttöisissä käsilaitteissa.
- **Router Late** — Infrastruktuuriradio, joka lähettää paketit uudelleen kerran, mutta vasta kaikkien muiden reititystilojen jälkeen. Tarjoaa lisäpeittoa paikallisille ryhmille kilpailematta ensisijaisten Routerien kanssa.
- **Client Base** — Käsittelee suosikkiradioihisi menevän tai niistä tulevan liikenteen Router Late -prioriteetilla (varmistaen näille viesteille ylimääräisen välityspeiton), samalla kun kaikki muu käsitellään tavallisen Client-roolin tavoin.
- **Client Mute** — Voit vastaanottaa verkkoliikennettä, mutta et osallistu viestien välittämiseen. Hyödyllinen vain valvontaan käytettäville laitteille tai ruuhkien vähentämiseen tiheästi rakennetuilla alueilla.
- **Tracker** — Valvomaton laite, jonka ainoa tarkoitus on lähettää GPS-sijaintiaan (esim. ajoneuvo, lemmikki tai omaisuus). Nukkuu lähetysten välillä akun säästämiseksi.
- **Sensor** — Valvomaton laite, joka raportoi ympäristötelemetriaa (lämpötila, kosteus, ilmanlaatu). Samanlainen virrankulutusprofiili kuin Tracker-roolissa.
- **TAK / TAK Tracker** — Tarvitaan vain yhteensopivuuteen ATAK-/WinTAK-järjestelmien kanssa. Katso [TAK-integraatio](tak) lisätietoja varten.

> 💡 **Vinkki:** Verkko toimii parhaiten, kun suurin osa radioista käyttää **Client**- tai **Router**-roolia. Liian suuri määrä Client Mute -rooleja heikentää verkon toimintavarmuutta, kun taas liian suuri määrä Router-rooleja tiheässä verkossa voi aiheuttaa ruuhkaa. Hyvä nyrkkisääntö on yksi Router jokaista 5–10 Client-roolia kohden alueellasi.

### Salausilmaisimet

Radiot näyttävät nimensä vieressä salauksen tilaa kuvaavat kuvakkeet:

| Kuvake          | Merkitys                                                                                                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 🔒 Lukittu      | Yhteys käyttää PKI:tä (julkisen avaimen infrastruktuuria) — päästä päähän salattu ja varmennetulla identiteetillä suojattu. |
| 🔓 Lukitsematon | Yhteys käyttää PKI:tä (julkisen avaimen infrastruktuuria) — päästä päähän salattu ja varmennetulla identiteetillä suojattu. |
| ⚠️ Ei täsmää    | Julkinen avain ei täsmää — radion avain on muuttunut viime näkemän jälkeen (tutki ennen luottamista).                                       |

> 💡 **Vinkki:** PKI-salaus (laiteohjelmisto 2.5+) tarjoaa vahvemman suojauksen kuin kanavan PSK-avain, koska jokaisella radiolla on oma yksilöllinen avainparinsa. Jos näet avaimen täsmäämättömyysvaroituksen, radio on voitu nollata tai sen tietoturva on voinut vaarantua.

## Pikatoiminnot

Radioluettelosta voit:

- **Napauttaa** radiota avataksesi sen tietosivun
- **Pitkä painallus** pikatoimintoja varten:
  - Merkitse tai poista suosikki
  - Mykistä tai poista mykistys ilmoituksista
  - Lähetä yksityisviesti
  - Reitinselvitys
  - Ohita tai poista ohitus
  - Poista laite

## Suodatus ja lajittelu

### Tekstihaku

Kirjoita hakukenttään suodattaaksesi radioita nimen tai lyhyen nimen perusteella. Suodatus päivittyy reaaliajassa kirjoittaessasi.

### Suodatusvalinnat

| Suodatus                              | Kuvaus                                                                                          |
| ------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Vain verkossa olevat**              | Näytä vain radiot, joista on kuultu viimeisten 2 tunnin aikana                                  |
| **Vain suorat**                       | Näytä vain radiot, joihin on suora yhteys (ei välitetty yhteys)              |
| **Näytä tuntemattomat**               | Näytä radiot, jotka eivät ole vielä lähettäneet käyttäjätietoja                                 |
| **Ohita infrastruktuurilaitteet**     | Piilottaa infrastruktuuriroolit (Router, Repeater, Router Late, Client Base) |
| **Rajaa MQTT pois**                   | Piilottaa radiot, joista on kuultu vain MQTT-internetsillan kautta                              |
| **Näytä vain huomioimattomat radiot** | Näytä radiot, jotka olet aiemmin ohittanut tai mykistänyt                                       |

### Lajitteluvaihtoehdot

| Lajittelu                                       | Kuvaus                                                                            |
| ----------------------------------------------- | --------------------------------------------------------------------------------- |
| **Viimeksi kuultu** (oletus) | Näytä viimeksi kuullut radiot ensin                                               |
| **Aakkosjärjestys**                             | Lajiteltu radion pitkän nimen mukaan                                              |
| **Etäisyys**                                    | Lähimpänä olevat radiot ensin (edellyttää sijainnin jakamista) |
| **Hyppyjä**                                     | Vähiten välityshyppyjä vaativat radiot ensin                                      |
| **Kanava**                                      | Ryhmitelty kanavaindeksin mukaan                                                  |
| **MQTT:n kautta**               | Ryhmitelty MQTT:n kautta kuultuihin ja radiolla kuultuihin        |
| **Suosikkien kautta**                           | Suosikkiradiot ensin                                                              |

## Radiot hyppymäärän mukaan

Avaa pylväskaavio, joka näyttää radioiden määrän kullakin hyppyetäisyydellä, napauttamalla radioluettelon sovelluspalkissa olevaa hyppyhistogrammikuvaketta (0 = suora yhteys, 1 = yksi välityshyppy ja niin edelleen). Suodata kaavio **Viimeksi kuultu** -ajanjakson mukaan — Kaikki ajat, 1 tunti, 8 tuntia tai 24 tuntia — nähdäksesi, miltä mesh-verkko näyttää juuri nyt verrattuna pidempään ajanjaksoon. Tämä on nopea tapa arvioida, kuinka laaja ja kuormittunut paikallinen mesh-verkkosi on.

## Radion tiedot

Radion napauttaminen avaa tietonäkymän, jossa on kattavat tiedot. Katso [Radion mittarit](node-metrics) saadaksesi täydelliset tiedot mittareista ja telemetriasta.

![Radion tietonäkymä](../../assets/screenshots/nodes_node_list.png)

Tietonäyttö sisältää laitetiedot, sijainnin ja toimintopainikkeet:

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

Täydellinen selattava hakemisto kaikista linkeistä on saatavilla myös kohdassa **Asetukset → Laite-linkit**.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — yksityiskohtaiset telemetriakoontinäytöt jokaiselle radiolle
- [Viestit ja kanavat](messages-and-channels) — lähetä yksityisviesti radiolle
- [Kartta ja reittipisteet](map-and-waypoints) — tarkastele radioiden sijainteja kartalla
- [Haku](discovery) — reitinselvitys- ja naapuritiedot verkon topologian tutkimiseen
- [Signaalimittari](signal-meter) — ymmärrä, mitä signaalipalkit tarkoittavat

---

