---
title: Radion mittarit
parent: Käyttöopas
nav_order: 5
last_updated: 2026-06-25
description: Telemetrianäkymät jokaiselle verkon radiolle — laitteen kunto, ympäristöanturit, ilmanlaatu, signaalin laatu, virta, reitinselvitys ja sijaintihistoria.
aliases:
  - mittarit
  - telemetria
  - laitteen mittarit
  - signaali
---

# Radion mittarit

Radion tietonäyttö tarjoaa kattavat telemetria- ja mittaritiedot jokaiselle verkon radiolle.

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

Ympäristömittarit esitetään kaavioina ajan kuluessa tapahtuvan trendianalyysin helpottamiseksi — lämpötila, kosteus ja ilmanpaine saavat kukin oman viivakaavionsa, jossa mittayksikkö näkyy Y-akselilla.

The BME680 **IAQ (Indoor Air Quality)** index is a single 0–500+ value derived from gas resistance, shown against a color-coded scale from _Excellent_ to _Dangerously Polluted_:

![IAQ index scale from Excellent to Dangerously Polluted](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **Vinkki:** Ympäristömittarit edellyttävät etäradioon liitettyä anturia. Kaikki radiot eivät raportoi ympäristötietoja. Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi täydellisen luettelon tuetuista antureista.

## Ilmanlaatumittarit

Ilmanlaatu on erillinen mittarinäkymä radioille, joissa on hiukkas- ja/tai CO₂-anturi. Se on **erillinen BME680:n IAQ-lukemasta**, joka on lueteltu ympäristömittareissa — IAQ on yksittäinen kaasuvastukseen perustuva indeksi, kun taas ilmanlaatunäkymä esittää varsinaiset hiukkas- ja CO₂-mittaukset kaavioina.

| Metrijärjestelmä      | Yksikkö | Kuvaus                                      |
| --------------------- | ------- | ------------------------------------------- |
| PM1.0 | µg/m³   | Enintään 1,0 mikrometrin kokoiset hiukkaset |
| PM2.5 | µg/m³   | Enintään 2,5 mikrometrin kokoiset hiukkaset |
| PM10                  | µg/m³   | Enintään 10 mikrometrin kokoiset hiukkaset  |
| CO₂                   | ppm     | Hiilidioksidipitoisuus                      |

CO₂-lukemat on värikoodattu vakavuusasteen mukaan, jotta ilmanlaadun arviointi onnistuu yhdellä silmäyksellä:

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

> 💡 **Vinkki:** Ilmanlaatumittarit edellyttävät yhteensopivaa ilmanlaatuanturia etäradiossa. Jos radiossa ei ole hiukkas- tai CO₂-anturia, ilmanlaatupainiketta ei näytetä. Katso [Telemetria ja anturit](telemetry-and-sensors) saadaksesi lisätietoja tuetusta laitteistosta.

## Signaalin voimakkuudet

Radiosignaalin laatutiedot:

| Metrijärjestelmä | Kuvaus                                                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| SNR              | Signaali-kohinasuhde (suurempi SNR on parempi)                                                         |
| RSSI             | Vastaanotetun signaalin voimakkuusindikaattori (RSSI) (lähempänä nollaa on parempi) |
| Kohinataso       | Paikallinen taustakohina dBm-arvona (negatiivisempi on hiljaisempi)                                    |
| Hyppylaskuri     | Verkon hyppymäärä viimeisimmälle viestille                                                                                |

### Signaalin laadun viitearvot

Signal quality is rated from **SNR relative to the active LoRa modem preset's demodulation floor**, not from fixed thresholds — a given SNR means different things on different presets (e.g. −15 dB is fine on LongSlow but unusable on ShortFast). RSSI is shown but is not part of the rating. Letting `limit` be the preset's SNR limit:

| Laatu       | Kriteerit                                        |
| ----------- | ------------------------------------------------ |
| Hyvä        | SNR above the preset's limit                     |
| Kohtalainen | within 5.5 dB below the limit    |
| Huono       | within 7.5 dB below the limit    |
| ei mitään   | more than 7.5 dB below the limit |

See [Understanding the Signal Meter](signal-meter) for the full explanation.

Yhdistetyn radion paikalliset tilastot näytetään myös Signaalin laatu -näkymässä silloin, kun ne ovat saatavilla. Nämä kerätyt tiedot sisältävät kohinatason, liikennelaskurit, välityslaskurit, verkossa olevien radioiden määrän sekä radion käyttöajan. Kohinatason kaaviossa käytetään katkoviivalla merkittyä viiteviivaa arvossa -85 dBm, jotta kuormittunut RF-ympäristö on helpompi tunnistaa. Käytä **Pyydä**-painiketta pyytääksesi yhdistetystä radiosta tuoreen Paikalliset tilastot -telemetriaraportin, **Tyhjennä**-painiketta poistaaksesi Paikalliset tilastot -lokit kyseiseltä radiolta ja **Tallenna**-painiketta viedäksesi näkyvän Paikalliset tilastot -historian CSV-tiedostoksi.

## Virranhallinnan arvot

Virranhallintatelemetria (edellyttää INA-anturia tai yhteensopivaa laitteistoa):

| Metrijärjestelmä | Kuvaus                        |
| ---------------- | ----------------------------- |
| Väyläjännite     | Syöttöjännite                 |
| Virta            | Virrankulutus milliampeereina |
| Virta            | Laskennallinen teho           |

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

## Mittareiden tarkastelu

1. Siirry kohtaan **Radiot**.
2. Napauta radiota, jota haluat tarkastella.
3. Valitse mittariluokka tietonäytön välilehdistä.

![Radion tietonäyttö – paikallinen laite](../../assets/screenshots/nodes_detail_local.png)

Sijainti-välilehti näyttää sijaintitiedot radioille, jotka jakavat GPS-sijaintinsa:

![Sijaintivälilehden sisältö](../../assets/screenshots/nodes_position.png)

> ⚠️ **Huomautus:** Mittarit ovat käytettävissä vain, jos etäradio on raportoinut ne. Mittarit päivittyvät kunkin radion telemetria-asetuksissa määritetyin väliajoin.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — radioluettelo, suodatus ja lajittelu
- [Telemetria ja anturit](telemetry-and-sensors) — tuetut anturit ja määritykset
- [Signaalimittari](signal-meter) — miten signaalin laatu lasketaan SNR- ja RSSI-arvoista
- [Haku](discovery) — reitinselvityksen tiedot ja naapuritiedot
- [Yksiköt ja aluekohtaiset asetukset](units-and-locale) — lämpötilan, etäisyyden ja nopeuden näyttömuodot

---
