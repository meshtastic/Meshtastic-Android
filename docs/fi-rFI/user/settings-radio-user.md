---
title: Asetukset — Radio ja käyttäjä
parent: Käyttöopas
nav_order: 7
last_updated: 2026-08-29
description: Määritä radion laitteisto, LoRa-esiasetukset, käyttäjäprofiili, sijainnin jakaminen, virranhallinta ja tietoturva.
aliases:
  - asetukset
  - radion asetukset
  - käyttäjän asetukset
  - lora
---

# Asetukset — Radio ja käyttäjä

Määritä radion käyttäjätiedot, alue ja LoRa-asetukset, sijainti- ja virta-asetukset, verkko- ja Bluetooth-yhteydet sekä suojausasetukset.

## Käyttäjäasetukset

### Käyttäjäprofiili

| Asetus                   | Kuvaus                                                                                                                                                                                                                                                                                                                |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Pitkä nimi               | Näyttönimesi (enintään 39 merkkiä)                                                                                                                                                                                                                                                                 |
| Lyhytnimi                | 4-merkkinen lyhytnimi                                                                                                                                                                                                                                                                                                 |
| Tilaviesti               | Lyhyt vapaamuotoinen tilaviesti, jonka muut radiot näyttävät radion nimen yhteydessä – enintään 80 merkkiä. Kentän voi tyhjentää kirjoittamalla siihen **✕**. Edellyttää laiteohjelmistoversiota 2.8 tai uudempaa. Muussa tapauksessa tätä ei näytetä |
| Ei vastaanota viestejä   | Merkitsee radion sellaiseksi, jolle kenenkään ei pitäisi yrittää lähettää viestejä – tarkoitettu valvomattomalle tai infrastruktuuriradiolle. Muut sovellukset piilottavat sen yhteystietoluettelosta. Edellyttää yhteensopivaa laiteohjelmistoa                                      |
| Lisensoitu radioamatööri | Ota käyttöön, jos sinulla on radioamatöörilupa (sallii suuremman lähetystehon). Käyttöönotto muuttaa **Pitkä nimi** -kentän **Kutsumerkki**-kentäksi ja lisää erillisen **Pitkä nimi** -kentän. Muutos vahvistetaan ensin valintaikkunassa                         |

### Muutosten käyttöönotto

Asetusten muuttamisen jälkeen napauta **Tallenna** kirjoittaaksesi määritykset radioon. Radio saattaa käynnistyä uudelleen muutosten ottamiseksi käyttöön.

Tilaviesti tallennetaan samalla **Tallenna**-painikkeella, mutta se ei koskaan käynnistä radiota uudelleen. Kuten muitakin tämän näkymän asetuksia, sitä voidaan muokata etähallittavassa radiossa.

## Asetukset

### Laitteen asetukset

| Asetus                                     | Kuvaus                                                                                                                                                                                             | Oletus      |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- |
| Rooli                                      | Radion rooli (Client, Router jne.) – jokaisella vaihtoehdolla on oma kuvaus valintaluettelossa. Reititin-tilan valitseminen pyytää vahvistuksen | Client      |
| Uudelleenlähetyksen tila                   | Miten radio välittää viestejä edelleen. Jokaisen tilan kuvaus näkyy valintaluettelossa                                                                                             | Kaikki      |
| Radiotiedon lähetys (s) | Radion tietojen lähetysväli                                                                                                                                                                        | 10800       |
| Kaksoisnapautuspainike                     | Käsittele kaksoisnapautus painikkeen painalluksena                                                                                                                                                 | Ei käytössä |
| Kolmoisklikkaus Ad Hoc -pingille           | Lähetä kertaluonteinen sijaintipyyntö kolminkertaisella painalluksella                                                                                                                             | Ei käytössä |
| Ledin valvontasignaali                     | Vilkuta tilan merkkilediä säännöllisesti                                                                                                                                                           | Käytössä    |
| Aikavyöhyke                                | Laitteen kellon POSIX-aikavyöhyke. Painikkeilla voit kopioida puhelimesi aikavyöhykkeen tai tyhjentää kentän                                                                       | —           |
| Painikkeen / summerin GPIO                 | Lisäasetukset: GPIO-nastat, joihin painike ja summeri on kytketty                                                                                                                  | —           |

### LoRa:n asetukset

| Asetus                                             | Kuvaus                                                                                                                                                                           | Oletus                                           |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| Alue                                               | Taajuusalueen sääntelyalue. Tämä on määritettävä ennen lähettämistä                                                                                              | Ei asetettu (on määritettävä) |
| Modeemin esiasetus                                 | Nopeuden ja kantaman välinen kompromissi                                                                                                                                         | LongFast                                         |
| Hyppyraja                                          | Suurin hyppyjen määrä                                                                                                                                                            | 3                                                |
| Lähetysteho                                        | Lähetysteho (dBm): 0 = alueen sallima enimmäisteho                                                                                            | 0 (alueen enimmäisteho)       |
| Taajuuden ohitus                                   | Ohittaa lasketun käyttötaajuuden (MHz). Ei siirrä laskettua arvoa – jätä arvoksi 0, ellet tarvitse tiettyä taajuutta                          | 0 (käytä laskettua arvoa)     |
| Kanavan kaistanleveys                              | Kaistanleveysasetus                                                                                                                                                              | Esiasetuksen oletusarvo                          |
| Käytä esiasetusta                                  | Oletusarvoisesti käytössä. Poista tämä käytöstä, jos haluat määrittää hajotuskerroin-, koodausnopeus- ja kaistanleveysasetukset käsin modeemiesiasetuksen sijaan | Käytössä                                         |
| Levennyskerroin (Spread Factor) | Vain manuaalitilassa: 7–12. Suurempi hajotuskerroin lisää kantamaa, mutta hidastaa tiedonsiirtoa                                                 | Esiasetuksesta                                   |
| Koodausnopeus                                      | Vain manuaalitilassa: 5–8. Suurempi virheenkorjaus lisää lähetysaikaa                                                                            | Esiasetuksesta                                   |
| Taajuuspaikka                                      | Määrittää, mitä alueen taajuusväliä käytetään. 0 muodostetaan ensisijaisen kanavan nimestä                                                                       | 0 (automaattinen)             |
| Lähetys käytössä                                   | Tämän poistaminen käytöstä tekee radiosta vain vastaanottavan                                                                                                                    | Käytössä                                         |
| Ohita käyttöaste (Duty Cycle)   | Ohittaa alueen lähetysajan käyttörajoituksen. Laitonta useimmilla alueilla. Ota käyttöön vain, jos radioamatöörilupasi sallii sen                | Pois                                             |
| Ohita MQTT                                         | Hylkää MQTT:n kautta saapuneet paketit sen sijaan, että ne olisi vastaanotettu radion kautta                                                                     | Pois                                             |
| MQTT päällä                                        | Salli yhdyskäytävien välittää pakettisi MQTT:hen                                                                                                                 | Pois                                             |
| RX tehostettu vahvistus                            | Lisävastaanottovahvistus SX126x-radioille. Kuluttaa hieman enemmän virtaa                                                                                        | Pois                                             |
| PA tuuletin pois käytöstä                          | Poista päätevahvistimen tuuletin käytöstä laitteissa, joissa sellainen on                                                                                                        | Pois                                             |

> ⚠️ **Tärkeää:** Käyttö väärällä alueasetuksella voi rikkoa paikallisia radiomääräyksiä. Katso [alueasetusten määritysopas](https://meshtastic.org/docs/getting-started/initial-config) meshtastic.org-sivustolta saadaksesi lisätietoja.

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

Kaikkien samalla kanavalla olevien radioiden on käytettävä samaa modeemiesiasetusta. Radiot, joiden modeemiesiasetukset eivät täsmää, eivät voi viestiä keskenään, vaikka ne käyttäisivät samaa taajuutta ja salausavainta.

[Modeemiesiasetukset](#modem-presets)-taulukon kantama-arviot perustuvat tasaiseen maastoon ja vaatimattomiin antenneihin. Korkeuseroetu (mäki, rakennuksen katto) kasvattaa käytännön kantamaa huomattavasti. Hyvin sijoitettu Long Fast -asetusta käyttävä Router voi usein toimia paremmin kuin maan tasalla oleva Long Slow -asetusta käyttävä radio.

### Näytön asetukset

Nämä ohjaavat **radion omaa näyttöä**, eivät sovellusta.

| Asetus                              | Kuvaus                                                                                                                                                                        |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Näytön päälläoloaika                | Kuinka kauan näyttö pysyy päällä ennen siirtymistä lepotilaan                                                                                                                 |
| Karusellin aikaväli                 | Kuinka usein radio vaihtaa näyttönäkymää automaattisesti                                                                                                                      |
| Näyttötila                          | Laiteohjelmiston käyttämä näytön asettelu/tiheys                                                                                                                              |
| Näyttöyksiköt                       | Metrinen tai imperiaalinen radion näytössä                                                                                                                                    |
| Käytä 12 tunnin kelloa              | Näytä radion kello 12 tunnin muodossa 24 tunnin sijaan                                                                                                                        |
| Lihavoitu otsikko                   | Näytä näytön otsikkoteksti lihavoituna                                                                                                                                        |
| Käännä näyttö                       | Kierrä näyttö 180° ylösalaisin asennusta varten                                                                                                                               |
| OLED-tyyppi                         | Auto, SSD1306, SH1106, SH1107                                                                                                                                                 |
| Herätä napautuksesta tai liikkeestä | Laita näyttö päälle, kun radioon kosketaan tai sitä liikutetaan                                                                                                               |
| Kompassin suuntaus                  | Kompassinäytön kiertopoikkeama (0°, 90°, 180°, 270°)                                                                                                       |
| Osoita aina pohjoiseen              | Lukitsee kompassinäytön näyttämään pohjoiseen sen sijaan, että se kääntyisi kulkusuunnan mukaan. Riippumaton kompassin suunnasta — kumpikaan ei korvaa toista |

### Sijainnin asetukset

> ⚠️ **Tärkeää:** Tämän näkymän tallentaminen käynnistää radion aina uudelleen.

| Asetus                                            | Kuvaus                                                                                                                                                       |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| GPS-tila (fyysinen laitteisto) | Kolmitilainen: GPS käytössä, poistettu käytöstä tai ei käytettävissä. Ei pelkkä käytössä / poistettu käytöstä -asetus        |
| GPS-kyselyn aikaväli                              | Kuinka usein radio pyytää GPS:ltä sijainnin                                                                                                  |
| Lähetyksen aikaväli                               | Kuinka usein sijainti jaetaan mesh-verkkoon                                                                                                                  |
| Älykäs sijainti                                   | Lähettää sijainnin liikkeen perusteella pelkän aikavälin sijaan                                                                                              |
| Älykäs aikaväli                                   | Älykäs sijainti -toiminnon ollessa käytössä lyhin sallittu aika sijaintilähetysten välillä                                                                   |
| Älykäs etäisyys                                   | Älykäs sijainti -toiminnon ollessa käytössä, kuinka pitkän matkan on liikuttava ennen sijainnin lähettämistä                                                 |
| Kiinteä sijainti                                  | Käytä käsin syötettyä leveyspiiriä, pituuspiiriä ja korkeutta GPS:n sijaan                                                                   |
| Sijaintimerkinnät                                 | Asetusryhmä, jolla valitaan, mitkä tiedot liitetään sijaintiin — kuten korkeus, viitekehys, tarkkuus, näkyvissä olevat satelliitit, aikaleima ja muut tiedot |
| GPS EN / Vastaanotto / Lähetys GPIO               | Lisäasetukset: nastat, joihin GPS-moduuli on kytketty                                                                                        |

### Virran asetukset

| Asetus                                                      | Kuvaus                                                                               |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Ota virransäästötila käyttöön                               | Anna radion siirtyä mahdollisimman herkästi lepotilaan toimintojen välillä           |
| Sammuta virran katketessa                                   | Sammuta laite, kun ulkoinen virta katkeaa                                            |
| Super-syväunen kesto                                        | Kuinka kauan syvin lepotila kestää                                                   |
| Vähimmäisherätyksen kesto                                   | Kuinka vähän aikaa radio pysyy hereillä heräämisen jälkeen                           |
| Bluetoothin odotusaika                                      | Kuinka kauan odotetaan puhelimen yhteyden muodostumista ennen lepotilaan siirtymistä |
| ADC-kertoimen ohitus                                        | Ota akun jännitemittauksille käyttöön manuaalinen korjaus                            |
| Korvaava AD-muuntimen kerroin                               | Korjauskerroin, käytössä vain kun manuaalinen korjaus on käytössä                    |
| INA_2XX-akun valvontapiirin I2C-osoite | Ulkoisen INA-sarjan tehoanturin osoite, jos sellainen on asennettu                   |

### Verkon asetukset

> ⚠️ **Tärkeää:** Tämän näkymän tallentaminen käynnistää radion aina uudelleen.

| Asetus                                   | Kuvaus                                                                                                                               |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| WiFi käytössä                            | Ota WiFi-radio käyttöön (ESP32-radiot)                                                                            |
| SSID                                     | Verkon nimi, johon yhdistetään. **Skannaa WiFi-QR-koodi** täyttää tämän sekä salasanan tavallisesta WiFi-QR-koodista |
| Salasana                                 | Verkon salasana                                                                                                                      |
| Ethernet käytössä                        | Käytä langallista yhteyttä sitä tukevalla laitteistolla                                                                              |
| IPv4-tila                                | DHCP tai alla olevilla neljällä kentällä määritetty kiinteä osoite                                                                   |
| WiFi IP / Aliverkko / Yhdyskäytävä / DNS | Kiinteä IP-osoite, käytössä vain kun IPv4-tila on kiinteä                                                                            |
| UDP-lähetys                              | Jaa mesh-verkon liikenne muiden radioiden kanssa lähiverkon kautta                                                                   |
| NTP palvelin                             | Ajan synkronointipalvelin (NTP-palvelin)                                                                          |
| rsyslog-palvelin                         | Etätietojen palvelin                                                                                                                 |

![Verkkoasetukset, joissa on määritetty kiinteä IPv4-osoite](../../assets/screenshots/settings_ipv4_field.png)

### Bluetooth asetukset

| Asetus             | Kuvaus                                                                                                                  |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Bluetooth käytössä | Ota Bluetooth-radio käyttöön tai poista käytöstä                                                                        |
| Pariliitostila     | Kiinteä PIN-koodi, satunnainen PIN-koodi tai ei PIN-koodia                                                              |
| Kiinteä PIN-koodi  | Pariliitoksen PIN-koodi. Siinä on oltava **täsmälleen kuusi numeroa** — kenttä hylkää kaikki muut arvot |

### Turvallisuusasetukset

| Asetus                      | Kuvaus                                                                                                                                                                                                                                        |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Julkinen avain              | Radiosi julkinen avain (vain luku)                                                                                                                                                                                         |
| Ylläpitäjän avain           | Avaimet, joilla tätä radiota voidaan hallita etänä – enintään kolme                                                                                                                                                                           |
| Yksityinen avain            | Radiosi yksityinen avain (säilytä turvallisesti). Näytetään peitettynä, kun tarkastelet toista radiota etähallinnan kautta — laiteohjelmisto ei lähetä sitä                                                |
| Luo uusi yksityinen avain   | Luo tälle radiolle uuden avainparin vahvistuksen jälkeen. Kaikkien vanhan avaimesi tunteneiden vertaisradioiden on opittava uusi avain                                                                                        |
| Suoran viestin avain        | Suoraviestien salaukseen käytettävä avain                                                                                                                                                                                                     |
| ~~Ylläpitokanava käytössä~~ | ⚠️ Poistettu — määritetään nyt automaattisesti, kun ylläpitoavain asetetaan                                                                                                                                                                   |
| Virheenkorjausloki          | Tulosta reaaliaikainen virheenkorjausloki sarjaportin tai bluetoothin kautta                                                                                                                                                                  |
| Sarjaportti käytössä        | Ota sarjakonsoliyhteys käyttöön (siirretty laiteasetuksista)                                                                                                                                                               |
| Hallintatila                | Rajoita muiden kuin järjestelmänvalvojan tekemiä kanavamuutoksia. Voidaan valita vasta, kun järjestelmänvalvojan avain on määritetty                                                                                          |
| Varmuuskopioi avaimet       | Tallenna radion avaimista salattu varmuuskopio tälle laitteelle (vain Android)                                                                                                                                             |
| Palauta avaimet             | Kirjoita varmuuskopioidut avaimet takaisin radioon (käytettävissä, kun varmuuskopio on olemassa)                                                                                                                           |
| Poista avaimen varmuuskopio | Poista tälle laitteelle tallennettu avainten varmuuskopio                                                                                                                                                                                     |
| Suojaustaso                 | Pakettien aitous – miten allekirjoittamattomia tai välitettyjä paketteja käsitellään: **Tiukka**, **Tasapainoinen** tai **Yhteensopiva** (edellyttää tuettua laiteohjelmistoa; Tiukka pyytää vahvistuksen) |

#### Lukitustila

Lukitustila salaa laitteen tallennustilan ja edellyttää tunnuslausetta jokaiselle yhteydelle. Edellyttää sitä tukevaa laiteohjelmistoa. Muussa tapauksessa tätä ei näytetä.

Käyttöönoton yhteydessä sinua pyydetään määrittämään ja vahvistamaan tunnuslause sekä vahvistamaan, että **se lukitsee virheenkorjausportin (SWD)** sitä tukevassa laitteistossa. Voit poistaa lukitustilan käytöstä milloin tahansa tunnuslauseella, ja laitteen täydellinen tyhjennys palauttaa laitteiston joka tapauksessa.

Tunnuslauseen lisäksi määrität rajat, joiden täyttyessä istunto päättyy automaattisesti:

| Kenttä                                                | Mitä se tekee                                      |
| ----------------------------------------------------- | -------------------------------------------------- |
| Jäljellä olevat käynnistykset                         | Kuinka monen käynnistyksen ajan avattu tila säilyy |
| Tuntia vanhenemiseen saakka                           | Avatun tilan enimmäiskesto                         |
| Istunnon enimmäiskesto (minuuttia) | Yhden avatun yhteyden enimmäiskesto                |

Kun lukitustila on käytössä, tässä lukee _Aktiivinen – tallennustila salattu, tämä yhteys todennettu_, kun yhteys on avattu, tai _Aktiivinen – avaa tämä yhteys antamalla tunnuslause_, kun sitä ei ole avattu. **Lukitse nyt** päättää nykyisen istunnon välittömästi. Toistuvat virheelliset tunnuslauseet rajoitetaan odotusajalla, ennen kuin voit yrittää uudelleen.

> ⚠️ **Varoitus:** Tunnuslausetta ei voi palauttaa. Jos kadotat sen, laite on tyhjennettävä sen palauttamiseksi, jolloin sen avaimet, kanavat ja asetukset poistuvat.

![Salasanakenttä](../../assets/screenshots/settings_password_field.png)

Asetukset käyttävät tavallisia asetussäätimiä — pudotusvalikoita, kytkimiä ja liukusäätimiä:

| Säädin         | Kuvakaappaus                                                                                                      |
| -------------- | ----------------------------------------------------------------------------------------------------------------- |
| Pudotusvalikko | ![Pudotusvalikkoasetus avattuna näyttämään vaihtoehtoluettelonsa](../../assets/screenshots/settings_dropdown.png) |
| Kytkin         | ![Kytkinasetus käytössä-asennossa](../../assets/screenshots/settings_switch.png)                                  |
| Liukusäädin    | ![Liukusäädinasetus nykyinen numeerinen arvo näkyvissä](../../assets/screenshots/settings_slider.png)             |

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — valinnaiset ominaisuusmoduulit ja laitteen ylläpitotoiminnot
- [Signaalimittari](signal-meter) — miten modeemiesiasetukset vaikuttavat signaalin laadun raja-arvoihin
- [LoRa-määritykset](https://meshtastic.org/docs/configuration/radio/lora) — yksityiskohtainen LoRa-asetusten viite meshtastic.org-sivustolla
- [Alkumääritykset](https://meshtastic.org/docs/getting-started/initial-config) — alueasetusten määritysopas meshtastic.org-sivustolla
