---
title: Radion mittarit
parent: Käyttöopas
nav_order: 5
last_updated: 2026-08-29
description: Telemetrianäkymät jokaiselle verkon radiolle — laitteen kunto, ympäristöanturit, ilmanlaatu, signaalin laatu, virta, reitinselvitys ja sijaintihistoria.
aliases:
  - mittarit
  - telemetria
  - laitteen mittarit
  - signaali
---

# Radion mittarit

Radion tietonäyttö tarjoaa kattavat telemetria- ja mittaritiedot jokaiselle verkon radiolle.

## Mittareiden tarkastelu

1. Siirry kohtaan **Radiot**.
2. Napauta radiota, jota haluat tarkastella.
3. Valitse mittariluokka tietonäytön välilehdistä.

![Radion tietonäyttö – paikallinen laite](../../assets/screenshots/nodes_detail_local.png)

Sijainti-välilehti näyttää sijaintitiedot radioille, jotka jakavat GPS-sijaintinsa:

![Sijaintivälilehden sisältö](../../assets/screenshots/nodes_position.png)

> ℹ️ **Huomautus:** Mittarit näkyvät vain, jos etäradio on lähettänyt ne. Mittarit päivittyvät kunkin radion telemetria-asetuksissa määritetyin väliajoin.

## Laitteen mittausloki

Perustoimintatiedot, jotka jokainen radio raportoi:

| Metrijärjestelmä | Kuvaus                                           |
| ---------------- | ------------------------------------------------ |
| Akun varaustaso  | Nykyinen akun varaustaso                         |
| Jännite          | Akun jännitelukema                               |
| Kanavan Käyttö   | Käytetyn lähetysajan käyttöasteen prosenttiosuus |
| Lähetysaika      | Tämän radion käyttämä lähetysaika                |
| Käyttöaika       | Aika viimeisestä uudelleenkäynnistyksestä        |

Laitemittarit näytetään erillisinä kortteina, joissa trendikäyrät esittävät akun varaustason, jännitteen, kanavan käyttöasteen, käyttöasteen ja käyttöajan kehitystä ajan kuluessa.

> 💡 **Vinkki:** Napauta mitä tahansa mittarikorttia laajentaaksesi sen täydelliseksi kaavioksi, jossa näkyvät historiatiedot. Nipistä lähentääksesi tai loitontaaksesi aika-akselia.

## Ympäristöarvot

Ympäristöanturien tiedot (edellyttää yhteensopivaa laitteistoa):

| Metrijärjestelmä                    | Anturiesimerkkejä     |
| ----------------------------------- | --------------------- |
| Lämpötila                           | BME280, BME680, SHT31 |
| Kosteus                             | BME280, BME680, SHT31 |
| Barometrinen paine                  | BME280, BMP280        |
| Kaasuvastus                         | BME680                |
| IAQ (ilmanlaatu) | BME680                |

Ympäristömittareista piirretään aikasarjat — lämpötila, ilmankosteus ja ilmanpaine näytetään kukin omana viivakaavionaan, ja mittayksikkö näkyy Y-akselilla.

BME680:n **IAQ (Indoor Air Quality)** -indeksi on yksi arvo väliltä 0–500+, joka perustuu kaasun resistanssiin. Se näytetään värikoodatulla asteikolla välillä _Erinomainen_–_Vaarallisen saastunut_:

![IAQ-indeksin asteikko välillä Erinomainen–Vaarallisen saastunut](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **Vinkki:** Ympäristömittarit edellyttävät etäradioon liitettyä anturia. Kaikki radiot eivät raportoi ympäristötietoja. Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi täydellisen luettelon tuetuista antureista.

## Ilmanlaatumittarit

Ilmanlaatu on erillinen mittarinäkymä radioille, joissa on hiukkas- ja/tai CO₂-anturi. Se on **erillinen BME680:n IAQ-lukemasta**, joka on lueteltu ympäristömittareissa — IAQ on yksittäinen kaasuvastukseen perustuva indeksi, kun taas ilmanlaatunäkymä esittää varsinaiset hiukkas- ja CO₂-mittaukset kaavioina.

| Metrijärjestelmä      | Yksikkö     | Kuvaus                                                                                                                                                                                                                                                                                              |
| --------------------- | ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PM1.0 | µg/m³       | Enintään 1,0 mikrometrin kokoiset hiukkaset                                                                                                                                                                                                                                                         |
| PM2.5 | µg/m³       | Enintään 2,5 mikrometrin kokoiset hiukkaset                                                                                                                                                                                                                                                         |
| PM10                  | µg/m³       | Enintään 10 mikrometrin kokoiset hiukkaset                                                                                                                                                                                                                                                          |
| AQI                   | EPA-indeksi | EPA:n **NowCast**-ilmanlaatuindeksi lasketaan radion viimeaikaisen PM2.5-historian perusteella, ja sen vakavuus näytetään värikoodattuna. Näytetään PM2.5-arvon yhteydessä, kun mittauksia on kertynyt riittävästi. |
| CO₂                   | ppm         | Hiilidioksidipitoisuus                                                                                                                                                                                                                                                                              |
| CO₂ lämpötila         | °C / °F     | CO₂ anturin ilmoittama lämpötila (esim. SCD4x)                                                                                                                                                                                                                   |
| CO₂ kosteus           | %           | CO₂ anturin ilmoittama suhteellinen ilmankosteus                                                                                                                                                                                                                                                    |

CO₂-lukemat on värikoodattu vakavuuden mukaan, joten ilmanlaadun näkee yhdellä silmäyksellä:

| Taajuusalue | CO₂-pitoisuus (ppm) | Väri           |
| ----------- | -------------------------------------- | -------------- |
| Hyvä        | < 1000        | Vihreä         |
| Tunkkainen  | < 2000        | Keltainen      |
| Huono       | < 5000        | Oranssi        |
| Vaarallinen | < 30000       | Punainen       |
| Evakuoi     | ≥ 30000                                | Tummanpunainen |

![Ilmanlaatulukemat värikoodatulla CO₂-vakavuusasteella](../../assets/screenshots/node-metrics_air_quality.png)

Ilmanlaatu tai mittaripainike näkyy radion tietonäytössä **vain silloin, kun radio on raportoinut ilmanlaatutelemetriaa**. Ilmanlaatu-näkymässä voit:

- Valita kaavioille **aikajakson**.
- Suodattaa **mittarisiruilla** — vain mittarit, joista on dataa, näytetään.
- **Päivittää ja pyytää** uusimmat ilmanlaatutelemetriatiedot.
- **Viedä CSV-tiedostoon** analysoitavaksi taulukkolaskentaohjelmassa.

> 💡 **Vinkki:** Ilmanlaatumittarit edellyttävät yhteensopivaa ilmanlaatuanturia etäradiossa. Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi lisätietoja tuetusta laitteistosta.

## Signaalin voimakkuudet

Radiosignaalin laatutiedot:

| Metrijärjestelmä | Kuvaus                                                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| SNR              | Signaali-kohinasuhde (suurempi SNR on parempi)                                                         |
| RSSI             | Vastaanotetun signaalin voimakkuusindikaattori (RSSI) (lähempänä nollaa on parempi) |
| Kohinataso       | Paikallinen taustakohina dBm-arvona (negatiivisempi on hiljaisempi)                                    |
| Hyppylaskuri     | Verkon hyppymäärä viimeisimmälle viestille                                                                                |

### Signaalin laadun viitearvot

Signaalin laatu arvioidaan **SNR**-arvon perusteella suhteessa käytössä olevan LoRa-modeemiesiasetuksen **demodulaation alarajaan**, ei kiinteiden raja-arvojen perusteella. Sama SNR-arvo voi tarkoittaa eri asioita eri esiasetuksilla (esim. `-15 dB` on hyvä LongSlow-esiasetuksella, mutta käyttökelvoton ShortFast-esiasetuksella). RSSI näytetään, mutta sitä ei käytetä arvioinnissa. Taulukossa _raja_ tarkoittaa esiasetuksen SNR-rajaa.

| Laatu       | Kriteerit                          |
| ----------- | ---------------------------------- |
| Hyvä        | SNR esiasetuksen rajan yläpuolella |
| Kohtalainen | alle 5,5 dB rajan alapuolella      |
| Huono       | 5,5–7,5 dB rajan alapuolella       |
| ei mitään   | yli 7,5 dB rajan alapuolella       |

Katso [Signaalimittarin toiminta](signal-meter), jos haluat täydellisen selityksen.

Yhdistetyn radiosi Local Stats -tiedot näytetään myös Signaalimittarit-näkymässä, jos ne ovat saatavilla. Nämä kerätyt tiedot sisältävät kohinatason, liikennelaskurit, välityslaskurit, verkossa olevien radioiden määrän sekä radion käyttöajan. Kohinatason kaaviossa käytetään katkoviivalla merkittyä viiteviivaa arvossa -85 dBm, jotta kuormittunut RF-ympäristö on helpompi tunnistaa.

- **Pyydä** — pyydä yhdistettyä radiota lähettämään uusi Local Stats -telemetriaraportti
- **Tyhjennä** — poista tämän radion Local Stats -lokit
- **Tallenna** — vie näkyvä Local Stats -historia CSV-tiedostoon

## Virranhallinnan arvot

Virranhallintatelemetria (edellyttää INA-anturia tai yhteensopivaa laitteistoa):

| Metrijärjestelmä | Kuvaus                                        |
| ---------------- | --------------------------------------------- |
| Jännite          | Kanavakohtainen jännitelukema                 |
| Virta            | Kanavakohtainen virrankulutus milliampeereina |

Enintään kolme anturikanavaa (ch1–ch3) piirretään kaavioon, ja jokaiselle voi määrittää oman nimen. Sovellus ei laske niistä tehoa.

## Reitinselvitys

Reitinselvitys näyttää viestin kulkeman reitin verkossa:

1. Napauta radion tietonäytössä **Reitinselvitys**.
2. Sovellus lähettää reitinselvityspyynnön kohderadiolle.
3. Tulokset näyttävät jokaisen hypyn SNR- ja RSSI-arvoineen.

### Reitinselvityksen tulosten lukeminen

```
Sinä → Radio A (SNR: 8.5) → Radio B (SNR: 5.2) → Kohde
```

Jokainen hyppy edustaa välitysradiota, joka välitti viestin eteenpäin.

## Sijainnin Loki

Historialliset sijaintitiedot radioille, jotka jakavat sijaintinsa:

- GPS-koordinaatit
- Korkeus
- Nopeus (jos radio liikkuu)
- Aikaleima jokaiselle sijaintiraportille

## Naapuritieto

Näyttää, mitkä radiot tietty radio voi kuulla suoraan, mikä auttaa ymmärtämään verkon topologiaa.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — radioluettelo, suodatus ja lajittelu
- [Telemetria ja anturit](telemetry-and-sensors) — tuetut anturit ja määritykset
- [Signaalimittari](signal-meter) — miten signaalin laatu lasketaan SNR- ja RSSI-arvoista
- [Paikallinen mesh-verkon etsintä](discovery) — reitiselvityksen tiedot ja naapuritiedot
- [Yksiköt ja aluekohtaiset asetukset](units-and-locale) — lämpötilan, etäisyyden ja nopeuden näyttömuodot
