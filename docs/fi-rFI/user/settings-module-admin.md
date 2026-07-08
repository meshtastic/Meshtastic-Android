---
title: Asetukset — Moduulit ja ylläpito
parent: Käyttöopas
nav_order: 8
last_updated: 2026-07-08
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

| Asetus            | Kuvaus                                                                        |
| ----------------- | ----------------------------------------------------------------------------- |
| Käytössä          | Ota MQTT-välityspalvelin käyttöön                                             |
| Palvelin          | MQTT-välityspalvelimen osoite                                                 |
| Käyttäjänimi      | Todennuksen käyttäjätunnus                                                    |
| Salasana          | Todennuksen salasana                                                          |
| Salaus            | Salaa MQTT-viestisisällöt                                                     |
| ~~JSON Output~~   | ⚠️ **Vanhentunut** — JSON-tuki poistettu laiteohjelmistosta, kenttä ohitetaan |
| TLS               | Käytä suojattua yhteyttä                                                      |
| Juuriaihe         | MQTT:n perusaihepolku                                         |
| Karttaraportointi | Julkaise sijainti julkiselle kartalle                                         |

Katso [MQTT](mqtt) saadaksesi yksityiskohtaisen käyttöoppaan, joka sisältää salauksen, tietosuojan ja välityspalvelimen määrityksen.

### Sarjaporttimoduuli

Mahdollistaa sarjaporttiviestinnän ulkoisten laiteintegraatioiden kanssa (GPS-moduulit, anturit tai mukautettu laitteisto). Kun tämä on käytössä, radion sarjaportti voi lähettää ja vastaanottaa protobuf- tai tekstimuotoista dataa, jolloin ulkoiset mikrokontrollerit tai tietokoneet voivat olla vuorovaikutuksessa verkon kanssa.

| Asetus            | Kuvaus                                        |
| ----------------- | --------------------------------------------- |
| Käytössä          | Ota sarjaporttiviestintä käyttöön             |
| Toista            | Toista vastaanotettu sarjaporttidata takaisin |
| Tila              | Teksti-, Protobuf- tai NMEA-ulostulo          |
| RX/TX pinnit      | GPIO-pinnit sarjaporttiyhteyttä varten        |
| Baud-siirtonopeus | Sarjaporttiyhteyden nopeus                    |

### Ulkoisten ilmoitusten moduuli

Ohjaa radion laitteiston summeri-, LED- tai värinähälytyksiä. Hyödyllinen laitteille, joiden täytyy ilmoittaa fyysisesti viestin saapumisesta — erityisen hyödyllinen valvomattomissa tai ulkokäyttöön asennetuissa laitteissa.

| Asetus                                 | Kuvaus                                                           |
| -------------------------------------- | ---------------------------------------------------------------- |
| Käytössä                               | Ota ilmoitukset käyttöön                                         |
| Hälytysviesti                          | Ilmoita saapuvista viesteistä                                    |
| Hälytysviestin summeri                 | Käytä summeria viesteille                                        |
| Värinähälytys viesteille               | Käytä värinää viesteille                                         |
| Hälytysääni                            | Ilmoita soittomerkkimerkistä (bell character) |
| Ulostulo (GPIO)     | Pinni ilmoitusulostuloa varten                                   |
| Käytössä                               | Aktiivinen korkealla tai matalalla tasolla                       |
| Kesto (ms)          | Ilmoituksen kesto                                                |
| Käytä I2S:ää summerina | Käytä I2S-äänilähtöä                                             |

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

Automaattinen kuuluvuustestityökalu radioiden välisen yhteyden laadun arviointiin. Kun toiminto on käytössä, radio lähettää säännöllisesti testiviestejä kasvavilla laskuriarvoilla. Vastaanottava radio kirjaa nämä viestit, jolloin voit myöhemmin kävellä tai ajaa pois ja analysoida, millä etäisyydellä viestien saapuminen loppui.

| Asetus                             | Kuvaus                                       |
| ---------------------------------- | -------------------------------------------- |
| Käytössä                           | Ota kuuluvuustesti käyttöön                  |
| Lähetysväli (s) | Aika testilähetysten välillä                 |
| Tallenna CSV-tiedosto              | Kirjaa vastaanotetut testitiedot SD-kortille |

### Telemetriamoduuli

Määrittää, mitä telemetriatietoja radiosi jakaa verkkoon. Telemetria sisältää laitteen kuntoon liittyviä tietoja (akun varaustaso, käyttöaika) sekä ympäristöanturien tietoja (lämpötila, kosteus, ilmanpaine).

| Asetus                  | Kuvaus                                     |
| ----------------------- | ------------------------------------------ |
| Laitemittarien väli     | Kuinka usein laitemittarit raportoidaan    |
| Ympäristömittarien väli | Kuinka usein ympäristöanturit raportoidaan |
| Ilmanlaatu käytössä     | Raportoi hiukkasanturin tiedot             |
| Virtamittarit käytössä  | Raportoi virrankulutus                     |

Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi tietoa tuetuista antureista ja määrityssuosituksista.

### Valmiiden viestien moduuli

Esimääritetyt viestit, joita voidaan käyttää laitteen fyysisillä painikkeilla (radioille, joissa on kiertokooderi, näppäimistö tai vastaava laitteisto). Määritä luettelo pikaviesteistä, jotka voidaan lähettää ilman yhdistettyä puhelinta — ihanteellinen kenttäkäyttöön.

| Asetus                         | Kuvaus                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------- |
| ~~Käytössä~~                   | ⚠️ **Vanhentunut** — nykyinen laiteohjelmisto saattaa ohittaa tämän asetuksen |
| Viestit                        | Rivinvaihdoilla eroteltu viestiluettelo                                       |
| Lähetä äänimerkki              | Toista merkkiääni lähetyksen yhteydessä                                       |
| Kiertokooderi                  | Ota kiertokooderin syöte käyttöön                                             |
| Ylös, alas ja painallus-pinnit | GPIO-pinnien määritykset syötteille                                           |

### Äänimoduuli

Codec2-äänituki matalan kaistanleveyden puheviestintään verkossa. Tämä on **kokeellinen** ominaisuus, joka koodaa puheen erittäin pieniksi datapaketeiksi käyttäen Codec2-koodekkia.

| Asetus             | Kuvaus                                             |
| ------------------ | -------------------------------------------------- |
| Käytössä           | Ota äänimoduuli käyttöön                           |
| Codec2-nopeus      | Äänenlaadun ja kaistanleveyden välinen kompromissi |
| I2S Word Select    | GPIO-pinni I2S WS:lle              |
| I2S-datasisääntulo | GPIO-nasta I2S DIN:lle             |
| I2S-dataulostulo   | GPIO-pinni I2S DOUT:lle            |

> ⚠️ **Huomautus:** Ääniominaisuudet edellyttävät yhteensopivaa laitteistoa (I2S-mikrofoni ja kaiutin). Äänenlaatu on hyvin matalakaistainen — ajattele "ymmärrettävää radiopuhetta", ei puhelinlaatua.

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

Katso [Haku](discovery) saadaksesi lisätietoja naapuritietojen käyttämisestä verkon topologian tutkimiseen.

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

| Asetus                              | Kuvaus                                   |
| ----------------------------------- | ---------------------------------------- |
| Käytössä                            | Ota henkilölaskenta käyttöön             |
| Päivitysväli (s) | Kuinka usein laskentatiedot raportoidaan |

> 💡 **Vinkki:** PAX-laskuri on hyödyllinen jalankulkijamäärien arviointiin retkeilyreittien lähtöpisteissä, tapahtumapaikoilla tai muissa kohteissa. Laskentatulokset ovat arvioita — yhdellä henkilöllä voi olla useita laitteita mukana.

### TAK-moduuli

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

Poistaa vanhentuneet radiot paikallisesta tietokannastasi, jos niistä ei ole kuultu määritettävän aikaikkunan aikana.

### Palauta tehdasasetukset

Palauttaa kaikki asetukset tehdasasetuksiin. **Tätä toimintoa ei voi perua.**

### Käynnistä uudelleen

Käynnistä yhdistetty tai ylläpidettävä radio etänä uudelleen.

### Vianetsintäpaneeli

Avaa **Paketit**- ja **Sovelluslokit**-välilehdet diagnostiikkatietojen tarkastelua, suodatusta ja vientiä varten. Katso [Virheenjäljityslokit](debug-logs), jossa on täydellinen käyttöohje.

### Etähallinnan vianmääritys

- **Ei vastausta kohderadiolta** — kohderadio voi olla kuuluvuusalueen ulkopuolella, poissa verkosta tai siinä voi olla eri ylläpitoavain. Varmista, että ylläpitoavain on sama molemmissa radioissa.
- **Muutokset eivät tule voimaan** — jotkin asetukset edellyttävät uudelleenkäynnistystä ennen kuin ne astuvat voimaan. Kokeile Uudelleenkäynnistystä tallennuksen jälkeen.
- **Et näe etäasetuksia** — varmista, että radiossasi on kohderadion ylläpitoavain. Ylläpitokanava määritetään automaattisesti, kun ylläpitoavain on asetettu.

## Aiheeseen liittyvät aiheet

- [Asetukset — Radio ja käyttäjä](settings-radio-user) — radion ja käyttäjäprofiilin keskeiset asetukset
- [Moduulien määritysviite](https://meshtastic.org/docs/configuration/module) — yksityiskohtainen moduulidokumentaatio meshtastic.org-sivustolla
- [Moduulien määritysviite](https://meshtastic.org/docs/configuration/module) — yksityiskohtainen moduulidokumentaatio meshtastic.org-sivustolla

---

