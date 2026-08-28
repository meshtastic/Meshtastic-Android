---
title: Asetukset — Moduulit ja ylläpito
parent: Käyttöopas
nav_order: 8
last_updated: 2026-08-27
description: Määritä valinnaiset ominaisuusmoduulit (MQTT, telemetria, valmiit viestit, TAK ja muut) sekä suorita laitteen ylläpitotoimia.
aliases:
  - moduulit
  - moduulin asetukset
  - ylläpito
---

# Asetukset — Moduulit ja ylläpito

Määritä valinnaiset ominaisuusmoduulit ja suorita laitteen ylläpitotoimia. Moduulit laajentavat Meshtasticia erikoisominaisuuksilla — jokainen voidaan ottaa käyttöön tai poistaa käytöstä erikseen.

> 💡 **Vinkki:** Ota käyttöön vain ne moduulit, joita todella käytät. Käyttämättömien moduulien poistaminen käytöstä vähentää lähetyksen käyttöastetta, säästää akkua ja yksinkertaistaa määrityksiä.

Moduuliasetukset käyttävät korttipohjaista asettelua, jossa on kytkimiä, pudotusvalikoita, tekstikenttiä ja liukusäätimiä:

![Kytkin](../../assets/screenshots/settings_switch.png)

![Pudotusvalikko](../../assets/screenshots/settings_dropdown.png)

![Tekstikenttä](../../assets/screenshots/settings_text_field.png)

![Asetuskortin asettelu](../../assets/screenshots/settings_titled_card.png)

## Moduulin määritys

### MQTT-moduuli

Yhdistää verkon viestejä MQTT-välityspalvelimeen ja sieltä takaisin internet-yhteyksiä varten. Näin laajennat verkkoasi radiokantaman ulkopuolelle tai integroit sen kodin automaatiojärjestelmiin.

| Asetus                                 | Kuvaus                                                                                                                                                                                                      |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä                               | Ota MQTT-välityspalvelin käyttöön                                                                                                                                                                           |
| Palvelin                               | MQTT-välityspalvelimen osoite                                                                                                                                                                               |
| Käyttäjänimi                           | Todennuksen käyttäjätunnus                                                                                                                                                                                  |
| Salasana                               | Todennuksen salasana                                                                                                                                                                                        |
| Salaus                                 | Salaa MQTT-viestisisällöt                                                                                                                                                                                   |
| JSON-tuloste                           | Julkaise ja vastaanota MQTT-viestejä JSON-muodossa. Merkitty protobuf-rakenteessa vanhentuneeksi, mutta tämä on edelleen ainoa asetus tähän toimintaan, ja laiteohjelmisto käyttää sitä yhä |
| TLS                                    | Käytä suojattua yhteyttä                                                                                                                                                                                    |
| Juuriaihe                              | MQTT:n perusaihepolku                                                                                                                                                                       |
| Välityspalvelin käytössä               | Anna yhdistetyn puhelimen välittää radion MQTT-liikenne sen sijaan, että radio muodostaisi itse yhteyden välityspalvelimeen                                                                                 |
| MQTT-välityspalvelin tällä puhelimella | Yllä olevan toiminnon puhelinpään asetus: käyttääkö **tämä** puhelin tällä hetkellä kyseistä välitystä. Katso [MQTT](mqtt)                                                  |
| Karttaraportointi                      | Julkaise sijainti julkiselle kartalle – katso alla                                                                                                                                                          |

**Karttajulkaisu** laajenee omaksi ryhmäkseen:

| Asetus             | Kuvaus                                                                                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Käytössä           | Julkaise julkiselle kartalle                                                                                                                                             |
| Jaa sijainti       | Anna nimenomainen suostumus sijaintisi julkaisemiseen. Karttajulkaisua ei voi tallentaa ilman tätä                                                       |
| Sijainnin tarkkuus | Sijaintisi julkaisun tarkkuus                                                                                                                                            |
| Julkaisuväli       | Kuinka usein sijainti julkaistaan. Välin on oltava **vähintään 3600 s (1 tunti)** – sovellus estää tätä pienemmän arvon tallentamisen |

Katso [MQTT](mqtt) saadaksesi yksityiskohtaisen käyttöoppaan, joka sisältää salauksen, tietosuojan ja välityspalvelimen määrityksen.

### Sarjaporttimoduuli

Mahdollistaa sarjaporttiviestinnän ulkoisten laiteintegraatioiden kanssa (GPS-moduulit, anturit tai mukautettu laitteisto). Kun tämä on käytössä, radion sarjaportti voi lähettää ja vastaanottaa protobuf- tai tekstimuotoista dataa, jolloin ulkoiset mikrokontrollerit tai tietokoneet voivat olla vuorovaikutuksessa verkon kanssa.

| Asetus                      | Kuvaus                                                                                                                                                                                            |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sarjaportti käytössä        | Ota sarjaporttiviestintä käyttöön                                                                                                                                                                 |
| Palautus päällä             | Toista vastaanotettu sarjaporttidata takaisin                                                                                                                                                     |
| Sarjaportin tila            | Portin käyttämä protokolla – Default, Simple, Proto, Text message, NMEA, CalTopo, WS85 weather station, YE.Direct, MeshSolar config, Log tai Log (vain teksti) |
| Vastaanotto / lähetys       | Sarjayhteyden GPIO-nastat                                                                                                                                                                         |
| Sarjaportin nopeus          | Portin nopeus                                                                                                                                                                                     |
| Aikakatkaisu                | Kuinka kauan odotetaan ennen kuin saapuva viesti katsotaan kokonaiseksi                                                                                                                           |
| Korvaa konsolin sarjaportti | Ota käyttöön portti, jota virheenkorjauskonsoli normaalisti käyttää                                                                                                                               |

### Ulkoisten ilmoitusten moduuli

Ohjaa radion laitteiston summeri-, LED- tai värinähälytyksiä. Hyödyllinen laitteille, joiden täytyy ilmoittaa fyysisesti viestin saapumisesta — erityisen hyödyllinen valvomattomissa tai ulkokäyttöön asennetuissa laitteissa.

Käynnistimiä on kaksi – saapuva **viesti** ja vastaanotettu **BEL**-ohjausmerkki – ja kumpikin voi ohjata LED-valoa, summeria ja värinämoottoria erikseen, joten käytettävissä on kuusi kytkintä.

| Asetus                                                | Kuvaus                                                                                                      |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Ulkoiset ilmoitukset käytössä                         | Moduulin pääkytkin                                                                                          |
| Hälytysviesti: LED / summeri / värinä | Mitkä lähdöt aktivoituvat saapuvasta viestistä                                                              |
| Hälytysmerkki: LED / summeri / värinä | Mitkä lähdöt aktivoituvat vastaanotetusta BEL-ohjausmerkistä                                                |
| Ulostulon LED (GPIO)               | LED on kytketty nastaan                                                                                     |
| Ulostulon LED aktiivinen                              | Onko LED-nasta aktiivinen korkealla vai matalalla tasolla                                                   |
| Ulostulon äänimerkki (GPIO)        | Summeri on kytketty nastaan                                                                                 |
| Ulostulon värinä (GPIO)            | Värinämoottori on kytketty nastaan                                                                          |
| Käytä PWM-äänimerkkiä                                 | Ohjaa summeria PWM:llä, jolloin voidaan toistaa ääniä yhden kiinteän taajuuden sijaan       |
| Käytä I2S protokollaa äänimerkille                    | Lähetä hälytys sen sijaan I2S-äänilähdön kautta                                                             |
| Ulostulon kesto (millisekuntia)    | Kuinka kauan yksittäinen hälytys kestää                                                                     |
| Hälytysaikakatkaisu (sekuntia)     | Toista hälytystä tämän ajan, kunnes se kuitataan. 0 poistaa toistuvan muistutuksen käytöstä |
| Soittoääni                                            | PWM-summerilla toistettava RTTTL-soittoääni. Voidaan tuoda tiedostosta                      |

### Varastoi & välitä -moduuli

Puskuroi viestejä radioille, jotka ovat tilapäisesti poissa verkosta, ja toimittaa ne, kun nämä radiot yhdistyvät uudelleen. Välttämätön verkoissa, joissa radiot siirtyvät säännöllisesti kuuluvuusalueelle ja sen ulkopuolelle — varmistaa, etteivät viestit katoa lyhyiden yhteyskatkosten aikana.

| Asetus                                             | Kuvaus                                                                                                                                                                |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä                                           | Ota Varastoi & välitä käyttöön                                                                                                                    |
| Valvontasignaali                                   | Ilmoita tämän radion varastoi & välitä -ominaisuudesta säännöllisesti                                                                             |
| Tiedot                                             | Tallennettujen viestien enimmäismäärä                                                                                                                                 |
| Historian palautus (enintään)   | Toistettavien viestien enimmäismäärä                                                                                                                                  |
| Historian palautus (aikaikkuna) | Aikaikkuna viestien toistolle                                                                                                                                         |
| Palvelin                                           | Toimi mesh-verkon varastoi & välitä -palvelimena (edellyttää riittävästi muistia, esimerkiksi ESP32 PSRAM:lla) |

> 💡 **Vinkki:** Varastoi & välitä toimii parhaiten radioissa, joissa on runsaasti muistia (ESP32 ja PSRAM). Router-roolin radiot ovat ihanteellisia ehdokkaita, koska ne ovat yleensä jatkuvasti käynnissä.

### Kuuluvuustesti-moduuli

> ⚠️ **Varoitus:** Kuuluvuustesti toimii vain suojatulla ensisijaisella kanavalla. Niin kauan kuin ensisijainen kanavasi käyttää oletusarvoista julkista avainta, Käytössä-, Väli- ja Tallenna CSV -asetukset pysyvät poissa käytöstä. Tallentaminen poistaa moduulin automaattisesti käytöstä, jos kanava on palautunut julkiseksi.

Automaattinen kuuluvuustestityökalu radioiden välisen yhteyden laadun arviointiin. Kun toiminto on käytössä, radio lähettää säännöllisesti testiviestejä kasvavilla laskuriarvoilla. Vastaanottava radio kirjaa nämä viestit, jolloin voit myöhemmin kävellä tai ajaa pois ja analysoida, millä etäisyydellä viestien saapuminen loppui.

| Asetus                             | Kuvaus                                       |
| ---------------------------------- | -------------------------------------------- |
| Käytössä                           | Ota kuuluvuustesti käyttöön                  |
| Lähetysväli (s) | Aika testilähetysten välillä                 |
| Tallenna CSV-tiedosto              | Kirjaa vastaanotetut testitiedot SD-kortille |

### Telemetriamoduuli

Määrittää, mitä telemetriatietoja radiosi jakaa verkkoon. Telemetria sisältää laitteen kuntoon liittyviä tietoja (akun varaustaso, käyttöaika) sekä ympäristöanturien tietoja (lämpötila, kosteus, ilmanpaine).

Jokaisella neljällä mittausryhmällä on oma käyttöönottokytkin ja oma mittausväli, joten esimerkiksi akun tila voidaan raportoida usein ja anturitiedot harvemmin.

| Asetus                                | Kuvaus                                                                                                                                                                           |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Lähetä laitteen telemetriatiedot      | Laitemittausten pääkytkin. Näkyy vain laiteohjelmistoversiossa 2.7.12 ja uudemmissa                                              |
| Laitemittareiden päivitysväli         | Kuinka usein akun tila, käyttöaika ja kanavan käyttö raportoidaan                                                                                                                |
| Ympäristötietojen moduuli käytössä    | Raportoi liitettyjen ympäristöanturien tiedot                                                                                                                                    |
| Ympäristömittareiden päivitysväli     | Kuinka usein tiedot raportoidaan                                                                                                                                                 |
| Näytä ympäristötiedot näytöllä        | Näytä nämä tiedot myös radion omalla näytöllä                                                                                                                                    |
| Käytä Fahrenheit yksikköä             | Käytä radion näytössä Fahrenheit-asteita. Tämä koskee vain radion näyttöä – sovellus käyttää puhelimesi alueasetuksia, katso [Yksiköt ja alue](units-and-locale) |
| Ilmanlaadun tietojen moduuli käytössä | Raportoi hiukkas- ja CO₂-anturin tiedot                                                                                                                                          |
| Ilmanlaatumittareiden päivitysväli    | Kuinka usein tiedot raportoidaan                                                                                                                                                 |
| Virrankulutuksen moduuli käytössä     | Raportoi kanavakohtaiset jännite- ja virtamittaukset                                                                                                                             |
| Virtamittareiden päivitysväli         | Kuinka usein tiedot raportoidaan                                                                                                                                                 |
| Virrankulutuksen näyttö käytössä      | Näytä virtamittaukset myös radion omalla näytöllä                                                                                                                                |

Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi tietoa tuetuista antureista ja määrityssuosituksista.

### Valmiiden viestien moduuli

Esimääritetyt viestit, joita voidaan käyttää laitteen fyysisillä painikkeilla (radioille, joissa on kiertokooderi, näppäimistö tai vastaava laitteisto). Määritä luettelo pikaviesteistä, jotka voidaan lähettää ilman yhdistettyä puhelinta — ihanteellinen kenttäkäyttöön.

| Asetus                                                          | Kuvaus                                                                                                                 |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| ~~Valmisviesti käytössä~~                                       | ⚠️ **Vanhentunut** protobuf-rakenteessa                                                                                |
| Viestit                                                         | Rivinvaihdoilla eroteltu viestiluettelo                                                                                |
| Lähetä äänimerkki                                               | Lähetä viestin mukana BEL-ohjausmerkki, jotta vastaanottavan radion Ulkoiset ilmoitukset -moduuli voi antaa hälytyksen |
| Kiertokoodain käytössä                                          | Käytä kiertokoodainta syöttölaitteena                                                                                  |
| Kiertokoodaimen A / B / painikenastan GPIO-nasta                | Kolme nastaa, joihin kiertokoodain on kytketty                                                                         |
| Luo syötetapahtuma painalluksesta / myötäpäivään / vastapäivään | Minkä näppäintapahtuman kukin kiertokoodaimen toiminto tuottaa                                                         |
| Ylös/Alas/Valitse syöte käytössä                                | Erillinen, yksinkertaisempi syöttötapa, jossa käytetään ylös-/alas-/valitse-painikkeita kiertokoodaimen sijaan         |
| ~~Salli syöttölähde~~                                           | ⚠️ **Vanhentunut** protobuf-rakenteessa                                                                                |

### Äänimoduuli

Codec2-äänituki matalan kaistanleveyden puheviestintään verkossa. Tämä on **kokeellinen** ominaisuus, joka koodaa puheen erittäin pieniksi datapaketeiksi käyttäen Codec2-koodekkia.

| Asetus                             | Kuvaus                                             |
| ---------------------------------- | -------------------------------------------------- |
| Käytössä                           | Ota äänimoduuli käyttöön                           |
| Codec2-nopeus                      | Äänenlaadun ja kaistanleveyden välinen kompromissi |
| PTT pinni                          | PTT-painikkeen GPIO-nasta                          |
| I2S Word Select                    | GPIO-pinni I2S WS:lle              |
| I2S-datasisääntulo                 | GPIO-nasta I2S DIN:lle             |
| I2S-dataulostulo                   | GPIO-pinni I2S DOUT:lle            |
| I2S-kello (SCK) | I2S-bittikellon GPIO-nasta                         |

> ℹ️ **Huomautus:** Ääniominaisuus edellyttää yhteensopivaa laitteistoa (I2S-mikrofoni ja -kaiutin). Äänenlaatu on hyvin matalakaistainen — ajattele "ymmärrettävää radiopuhetta", ei puhelinlaatua.

### Etälaitteiston moduuli

GPIO-ohjaus mesh-verkon kautta. Mahdollistaa etäradion lukea tai kirjoittaa GPIO-nastoja toisessa radiossa — hyödyllinen releiden aktivointiin, kytkimien lukemiseen tai ulkoisen laitteiston ohjaamiseen etäältä.

| Asetus                          | Kuvaus                                                                      |
| ------------------------------- | --------------------------------------------------------------------------- |
| Käytössä                        | Ota etä-GPIO-käyttö käyttöön                                                |
| Salli määrittelemättömät pinnit | Salli pääsy mihin tahansa GPIO-nastaan (tietoturvariski) |
| Käytettävissä olevat pinnit     | Enintään 4 tämän radion etälukuun tai kirjoitukseen tarjoamaa GPIO-pinniä   |

> ⚠️ **Varoitus:** Määrittelemättömien pinnien salliminen antaa etäradioille pääsyn kaikkiin GPIO-pinneihin, mikä voi häiritä radion omaa laitteistoa. Ota käyttöön vain erillisissä GPIO-radioissa.

### Naapuritieto-moduuli

Lähettää tietoa suoraan kuulluista naapureista mahdollistaen verkon topologian kartoituksen. Jokainen käyttöön otettu radio jakaa säännöllisesti luettelon muista radioista, jotka se kuulee, sekä niiden signaalin laadun.

| Asetus                              | Kuvaus                                                                                                                                                                                              |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä                            | Ota naapuritiedon lähetys käyttöön                                                                                                                                                                  |
| Päivitysväli (s) | Kuinka usein naapuriluettelo lähetetään                                                                                                                                                             |
| Lähetä LoRan kautta                 | Lähetä myös naapuritiedot LoRa:n kautta, ei pelkästään MQTT:n tai puhelimen kautta. Ei käytettävissä kanavalla, joka käyttää oletusavainta ja nimeä |

Katso [Paikallinen mesh-haku](discovery), miten naapuritietoja käytetään mesh-verkon rakenteen tutkimiseen.

### Ympäristövalaistusmoduuli

Ohjaa tuetuissa laitteissa olevaa NeoPixeliä tai muita osoitteellisia RGB-LEDejä. Voidaan käyttää visuaalisina tilailmaisimina, ilmoitusvaloina tai koriste-efekteinä.

| Asetus                      | Kuvaus                                                     |
| --------------------------- | ---------------------------------------------------------- |
| Ledin tila                  | Kytke LED päälle tai pois päältä                           |
| Virta                       | LED-virran rajoitus (0–31)              |
| Punainen / vihreä / sininen | Yksittäisten värikanavien arvot (0–255) |

### Tunnistusanturimoduuli

Muuttaa radiosi liike- tai ovitunnistimeen perustuvaksi hälytysjärjestelmäksi. Kun GPIO-pinni havaitsee tilamuutoksen (liike havaittu, ovi avattu), radio lähettää hälytysviestin verkkoon.

| Asetus                                          | Kuvaus                                                                                                                                    |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä                                        | Ota tunnistusanturi käyttöön                                                                                                              |
| Valvonta pinni                                  | Anturiin kytketty GPIO-pinni                                                                                                              |
| Havaitsemisen laukaisutyyppi                    | Miten pinnin tila vastaa havaitsemistapahtumaa (esim. aktiivinen korkea/matala taso tai reunalaukaisu) |
| Käytä sisäistä ylösvetovastusta                 | Ota pinnin sisäinen ylösvetovastus käyttöön                                                                                               |
| Lähetyksen vähimmäisväli (s) | Hälytyslähetysten vähimmäisaika                                                                                                           |
| Tilalähetys (s)              | Tilatiedon lähetysväli                                                                                                                    |
| Lähetä äänimerkki                               | Sisällytä soittomerkkimerkki hälytyksiin                                                                                                  |
| Käyttäjäystävälinen nimi                        | Tälle anturille määritetty nimi                                                                                                           |

### PAX-laskurimoduuli

Henkilölaskuri, joka hyödyntää Wi-Fi- ja BLE-koepyyntöjä. Laskee lähellä olevia laitteita kuuntelemalla passiivisesti koepyyntöjä, joita puhelimet ja kannettavat tietokoneet lähettävät etsiessään verkkoja. Saatavilla vain ESP32-laitteissa.

| Asetus                              | Kuvaus                                                                                                                                        |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä                            | Ota henkilölaskenta käyttöön                                                                                                                  |
| Päivitysväli (s) | Kuinka usein laskentatiedot raportoidaan                                                                                                      |
| Wi-Fi-signaalin RSSI-kynnys         | Ohita tätä heikommat Wi-Fi-hakukyselyt, jotta kaukana olevia laitteita ei lasketa mukaan (oletus: -80 dBm) |
| BLE RSSI threshold                  | The same cut-off for BLE advertisements (defaults to −80 dBm)                                                              |

> 💡 **Vinkki:** PAX-laskuri on hyödyllinen jalankulkijamäärien arviointiin retkeilyreittien lähtöpisteissä, tapahtumapaikoilla tai muissa kohteissa. Laskentatulokset ovat arvioita — yhdellä henkilöllä voi olla useita laitteita mukana.

### Status Message Module

Publishes a short free-text status line for your node, which other nodes can display alongside it.

| Asetus                    | Kuvaus                                                                                                                                                                           |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Käytössä oleva tilaviesti | Up to 80 characters. The **✕** in the field clears it. (That is the app's own label for the field, verbatim.) |

Saving takes effect immediately — this is one of the few module settings that never asks the
node to reboot.

> ℹ️ **Note:** The screen only appears for firmware that reports support for the status-message
> module. If you do not see it in the module list, your node's firmware does not have it.

### Mesh Beacon Module

Broadcasts an invitation to your mesh, and receives invitations from others. See
[Local Mesh Discovery](discovery) for the full walkthrough.

### TAK-moduuli

> ℹ️ **Note:** This module only appears in the list once the node's **Device Role** (Device Config)
> is set to **TAK** or **TAK Tracker**. Change the role first, or the entry will not be there.

Team Awareness Kit -integraatio yhteensopivuutta varten ATAK- ja WinTAK-järjestelmien kanssa. Katso [TAK-integraatio](tak) saadaksesi tarkemmat määritys- ja käyttöohjeet.

## Ylläpito

### Etähallinta

Määritä etänä radiot, jotka jakavat saman ylläpitoavaimen:

1. Valitse kohderadio radioluettelosta.
2. Siirry kyseisen radion **Asetukset**-kohtaan.
3. Muokkaa määrityksiä.
4. Napauta **Tallenna** — muutokset lähetetään verkon kautta.

> ⚠️ **Edellyttää:** Ylläpitoavain on määritetty sekä omassa radiossasi että kohderadiossa.

### Tyhjennä NodeDB-tietokanta

Prunes your local node database. Two independent controls:

- An **age slider** — remove nodes not heard from within that window.
- **Clean unknown nodes only** — restrict the purge to nodes that never sent their user info,
  leaving named nodes alone regardless of age.

### Palauta tehdasasetukset

Palauttaa kaikki asetukset tehdasasetuksiin. **Tätä toimintoa ei voi perua.**

### Käynnistä uudelleen

Käynnistä yhdistetty tai ylläpidettävä radio etänä uudelleen.

### Vianetsintäpaneeli

Avaa **Paketit**- ja **Sovelluslokit**-välilehdet diagnostiikkatietojen tarkastelua, suodatusta ja vientiä varten. Katso [Virheenjäljityslokit](debug-logs), jossa on täydellinen käyttöohje.

### Tietoja

**Settings → About** carries the app's own identity rather than the radio's:

Three sections:

- **What is Meshtastic?** — a short description of the project.
- **Apps** — opens with **Need Hardware?**, a rotating carousel of popular devices that links out
  to where to buy one, then the GitHub repository, the running app version, and
  **Acknowledgements** (below).
- **Project information** — links to the website and to this documentation.

### Kiitokset

Reached from **About**, this lists every open-source library the app ships, with its license,
generated at build time by AboutLibraries. It was previously called the license screen.

### Etähallinnan vianmääritys

- **Ei vastausta kohderadiolta** — kohderadio voi olla kuuluvuusalueen ulkopuolella, poissa verkosta tai siinä voi olla eri ylläpitoavain. Varmista, että ylläpitoavain on sama molemmissa radioissa.
- **Muutokset eivät tule voimaan** — jotkin asetukset edellyttävät uudelleenkäynnistystä ennen kuin ne astuvat voimaan. Kokeile Uudelleenkäynnistystä tallennuksen jälkeen.
- **Et näe etäasetuksia** — varmista, että radiossasi on kohderadion ylläpitoavain. Ylläpitokanava määritetään automaattisesti, kun ylläpitoavain on asetettu.

## Aiheeseen liittyvät aiheet

- [Asetukset — Radio ja käyttäjä](settings-radio-user) — radion ja käyttäjäprofiilin keskeiset asetukset
- [Moduulien määritysviite](https://meshtastic.org/docs/configuration/module) — yksityiskohtainen moduulidokumentaatio meshtastic.org-sivustolla
- [UKK](https://meshtastic.org/docs/faq/) – usein kysytyt kysymykset meshtastic.org -sivustolla

---

