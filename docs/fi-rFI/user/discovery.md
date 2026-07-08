---
title: Haku
parent: Käyttöopas
nav_order: 12
last_updated: 2026-06-11
description: Tutki mesh-verkkoasi — paikallinen verkon haku, reitinselvitykset, naapurikartat ja radion hakuun liittyvät työkalut.
aliases:
  - mesh-verkon haku
  - paikallinen haku
  - verkkohaku
  - reitinselvitys
  - naapuritieto
---

# Haku

Hakutyökalut auttavat ymmärtämään **miten** mesh-verkko on yhteydessä — mitkä radiot kuulevat toisensa, mitä reittejä viestit kulkevat ja missä on pullonkauloja tai heikkoja yhteyksiä.

Sovellus tarjoaa kaksi toisiaan täydentävää lähestymistapaa:

- Paikallinen verkon haku (Scanner) — automaattinen tila, joka kierrättää yhdistettyä radiota eri LoRa-esiasetusten läpi, kuuntelee jokaisella ja arvioi, mikä esiasetus toimii parhaiten sijainnissasi.
- Manuaalinen etsiminen — reitinselvityksen reitit, naapuritiedot ja radiolista, joita voit käyttää milloin tahansa yksittäisten reittien ja topologian tarkasteluun.

---

## Paikallinen verkon haku (Scanner)

Paikallinen verkon haku on erillinen skannaustila, joka auttaa löytämään parhaan LoRa-modeemiesiasetuksen sijaintiisi ja näkemään, mitkä radiot ovat aktiivisia kullakin esiasetuksella. Se kierrättää yhdistettyä radiota valitsemiesi esiasetusten läpi, kuuntelee jokaisella asetuksella määrätyn ajan kerätäkseen paketteja ja analysoi sekä pisteyttää tulokset.

Avaa se kohdasta **Asetukset → Paikallinen verkon haku**.

> ⚠️ **Huom:** Haku muuttaa radiosi LoRa-asetuksia väliaikaisesti skannauksen ajaksi ja palauttaa alkuperäisen konfiguraation sen päätyttyä. Laitteen täytyy olla yhdistettynä, jotta skannaus voidaan suorittaa.

### Skannauksen asetukset

Ennen aloittamista määritä nämä asetukset:

| Hallinta                       | Kuvaus                                                                                                                                                                                                                                    |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LoRa-esiasetuksen valitsin** | Valitse yksi tai useampi esiasetus skannattavaksi. Haku pysähtyy jokaisessa valitussa esiasetuksessa vuorollaan kuuntelemaan liikennettä.                                                                 |
| **Kuunteluaika**               | Kunkin esiasetuksen kuunteluaika. Valitse 1, 5, 15, 30, 45, 60, 90, 120 tai 180 minuuttia. Pidempi kuunteluaika kerää enemmän paketteja ja antaa tarkemman kuvan, mutta kestää pidempään. |
| **Pidä näyttö päällä**         | Valinnainen kytkin, joka estää näytön siirtymisen lepotilaan pitkän skannauksen aikana.                                                                                                                                   |

**Käynnistä**-painike ei ole käytettävissä ja näyttää syyn, kunnes skannaus voidaan suorittaa. Yleisiä syitä, miksi se on pois käytöstä:

- Laite **ei ole yhdistetty**
- **Esiasetuksia ei ole valittu** skannattavaksi.
- Valittu esiasetus käyttää **2,4 GHz -taajuutta**, jota laitteistosi ei tue.

### Reaaliaikainen edistyminen

Skannauksen aikana haku näyttää sen nykyisen vaiheen:

| Tila                                                          | Mitä tapahtuu                                                                                                               |
| ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| **Valmistelee**                                               | Tallennetaan nykyinen kokoonpano ja valmistaudutaan skannaukseen.                                           |
| **Vaihdetaan kohteeseen <preset\>** | Vaihdetaan radio seuraavaan esiasetukseen testausta varten.                                                 |
| **Yhdistetään uudelleen**                                     | Yhteys muodostetaan uudelleen esiasetuksen vaihdon jälkeen.                                                 |
| **Kuuntelu**                                                  | Nykyisen esiasetuksen kuuntelu pakettien keräämiseksi, seuraavaan vaiheeseen siirtymisen laskuri käynnissä. |
| **Analyysi**                                                  | Kerättyjen pakettien käsittely ja esiasetusten vertailu ja pisteytys.                                       |
| **Palautetaan asetuksia**                                     | Palautetaan alkuperäinen LoRa-konfiguraatio takaisin.                                                       |

![Kuuntelun laskuri näyttää jäljellä olevan ajan nykyisessä esiasetuksessa](../../assets/screenshots/discovery_dwell_progress.png)

### Tulosten lukeminen

Kun skannaus valmistuu, haku näyttää jokaiselle testatulle esiasetukselle oman tuloskortin sekä yleisen yhteenvedon.

![Esiasetuksen tuloskortti, jossa näkyy sijoitus ja kerätyt mittarit](../../assets/screenshots/discovery_preset_result.png)

Mittarit sisältävät:

| Metrijärjestelmä                     | Mitä ne kertovat sinulle                                                                             |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| Radiolaatu (RF)   | Kyseisen esiasetuksen radioympäristön kokonaislaatu                                                  |
| Kanavan käyttöaste                   | Kuinka kuormitetut radiotaajuudet olivat kuuntelun aikana.                           |
| Lähetysaika                          | Havaittu lähetysaika.                                                                |
| Suorat ja välitetyt radiot           | Kuinka monta mesh-radiota kuultiin suoraan verrattuna välitettyihin.                 |
| Virheelliset / päällekkäiset paketit | Vioittuneiden ja toistettujen pakettien määrä, joka kertoo ruuhkasta tai häiriöistä. |

Tuloksista saatavilla olevat lisätoiminnot:

- **Skannaus­historia** — tallennetut istunnot, joita voit tarkastella myöhemmin; katsele tai poista aiempia skannauksia.
- **Haun kartta** — kartta skannauksessa löydetyistä radioista.
- **Raportin vienti** — vie raportti PDF-tiedostona Androidilla tai tekstinä muilla alustoilla.

> 💡 **Vinkki:** Androidilla haku voi luoda laitteessa toimivan tekoälyyhteenvedon (Gemini Nano) tuloksistasi. Jos laitteessa toimivaa mallia ei ole käytettävissä, käytetään algoritmista yhteenvetoa — näin saat aina luettavan tulkinnan skannauksesta.

---

## Manuaalinen haku

Alla olevat työkalut ovat käytettävissä milloin tahansa radiolistasta ja radion tietonäkymistä. Käytä niitä yksittäisten reittien tutkimiseen ja topologian muodostamiseen, joko osana skannausta tai sen sijaan.

## Reitinselvitys

Reitinselvitys näyttää tarkan polun, jota kautta viesti kulkee omalta radioltasi mihin tahansa toiseen verkon radioon. Se on yksittäisistä työkaluista hyödyllisin yhteysongelmien vianmääritykseen.

### Reitinselvityksen suorittaminen

1. Siirry kohtaan **Radiot** ja napauta radiota, jota haluat jäljittää.
2. Radion tietonäkymässä napauta **Reitinselvitys**.
3. Sovellus lähettää reitinselvityspyynnön ja odottaa vastausta.
4. Tulokset näyttävät jokaisen hypyn järjestyksessä sekä signaalin laadun jokaisessa vaiheessa.

### Tulosten lukeminen

Reitinselvityksen tulos näyttää tältä:

```
Sinä → Radio A (SNR: 8.5, RSSI: -95) → Radio B (SNR: 5.2, RSSI: -108) → Kohde
```

Jokainen hyppy on välittäjä-radio, joka lähetti viestin eteenpäin. Jokaisen hypyn SNR- ja RSSI-arvot kertovat kyseisen yhteysvälin laadusta.

| Mitä kannattaa tarkkailla                                                                            | Mitä se tarkoittaa                                                               |
| ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Kaikki hypyt näyttävät hyvää SNR-arvoa (> 5 dB)                                   | Hyvä reitti — viestit kulkevat luotettavasti                                     |
| Yksi hyppy näyttää huonon SNR:n (< 0 dB) | Heikko yhteys — tämä välityssegmentti on haavoittuva                             |
| Useita hyppyjä (4+)                                                               | Pitkä reitti — harkitse radion siirtämistä sen lyhentämiseksi                    |
| Eri reitti uudelleenyrityksellä                                                                      | Verkko mukautuu — useita reittejä on olemassa (tämä on hyvä!) |

> Vinkki: Aja reitinselvitys useita kertoja muutaman minuutin aikana. Jos reitti muuttuu, verkossasi on varareittejä — merkki hyvin kytketystä verkosta.

### Reitinselvityksen vianmääritys

- **Reittiä ei löytynyt** — kohderadio voi olla kiinni, kantaman ulkopuolella tai eri kanavalla. Varmista, että molemmat radiot jakavat vähintään yhden kanavan samalla salausavaimella.
- **Reitinselvitys aikakatkaistu** — reitti voi olla liian pitkä (ylittää hyppymäärärajan) tai välittäjä-radio on ruuhkautunut. Kokeile nostaa hyppymäärärajaa kohdassa **Asetukset → LoRa-asetukset**.
- **Epäsymmetriset reitit** — reitinselvitys A→B voi kulkea eri reittiä kuin B→A. Tämä on normaalia — radiosignaalin eteneminen ei aina ole symmetristä.

---

## Naapuritieto

Naapuritieto-moduuli antaa jokaisen radion lähettää listan radioista, jotka se voi **kuulla suoraan** (yhden hypyn päässä). Kun useat radiot jakavat naapurilistansa, voit muodostaa koko verkon topologian kartan.

### Naapuritiedon käyttöönotto

1. Siirry kohtaan **Asetukset → Moduuliasetukset → Naapuritieto**.
2. Ota moduuli käyttöön.
3. Aseta lähetysväli (oletus: 900 sekuntia / 15 minuuttia).

Kun toiminto on käytössä, radio lähettää säännöllisesti naapuritiedot. Myös muut radiot, joissa naapuritieto on käytössä, toimivat samalla tavalla.

### Naapuritiedon katselu

- Avaa minkä tahansa radion tietonäkymä ja etsi **Naapurit**-osio.
- Jokainen naapurimerkintä näyttää radion, joka on kuultu suoraan, sekä sen signaalilaadun.
- Yhdistä naapuritiedot useista radioista ymmärtääksesi koko mesh-verkon topologian.

> ⚠️ Huom: Naapuritieto lisää lähetysajan käyttöä, koska jokainen käytössä oleva radio lähettää säännöllisesti naapurilistansa. Vilkkaissa verkoissa, joissa on paljon radioita, harkitse pidempiä lähetysvälejä (3600 sekuntia tai enemmän) ruuhkautumisen välttämiseksi.

---

## Radiolista hakutyökaluna

Radiolista on itsessään tehokas hakutyökalu, kun käytät sen suodatus- ja lajitteluominaisuuksia oikein.

### Uusien radioiden löytäminen

- Lajittele **Viimeksi kuultu** nähdäksesi viimeksi aktiiviset radiot ylimpänä.
- Ota käyttöön **Näytä tuntemattomat** nähdäksesi radiot, jotka ovat ilmestyneet verkkoon, mutta eivät ole vielä lähettäneet käyttäjätietoja — usein juuri käyttöönotettuja laitteita.

### Yhteyksien arviointi

- Lajittele **Hyppyjen määrä** nähdäksesi mitkä radiot ovat suoraan tavoitettavissa (0 hyppyä) ja mitkä välitettyinä.
- Lajittele **Etäisyys** löytääksesi lähellä olevat radiot ja varmistaaksesi niiden tavoitettavuuden.
- Käytä **Rajaa MQTT pois** keskittyäksesi radioyhteyksillä tavoitettaviin radioihin (ei internet-sillan kautta).

### Infrastruktuurin tarkistus

- Poista käytöstä **Ohita infrastruktuurilaitteet** nähdäksesi Router-, Repeater-, Router Late- ja Client Base -radiot.
- Tarkista niiden signaalin laatu ja viimeksi kuultu -ajat varmistaaksesi, että infrastruktuuriradiot ovat kunnossa.

Katso [Radiot](nodes) saadaksesi lisätietoa suodatus- ja lajitteluasetuksista.

---

## Vinkkejä mesh-verkon tutkimiseen

- Aloita reitinselvityksellä — se antaa välittömän ja käytännöllisen tiedon yksittäisestä reitistä.
- Ota naapuritieto käyttöön keskeisissä radioissa — erityisesti reitittimissä ja toistimissa, jotta saat näkyvyyden runkoverkkoon.
- Tarkista kartta — [Kartta](map-and-waypoints) yhdessä signaalitiedon kanssa auttaa ymmärtämään, miksi jotkin yhteydet ovat vahvoja ja toiset heikkoja.
- Seuraa signaalin muutoksia ajan kuluessa — käytä [Signaalimittari](signal-meter) -opasta tulkitaksesi SNR- ja RSSI-arvot oikein.

---

