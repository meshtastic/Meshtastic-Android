---
title: Radion mittarit
nav_order: 5
last_updated: 2026-10-21
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
3. Scroll to the **Telemetry** section and find the category you want — **Signal Quality**, **Device Metrics**, **Environment Metrics**, **Air-Quality Metrics**, **Power Metrics**, **Position**, and the rest.
4. Tap the refresh button on a row to ask the node for a fresh reading. The chart button beside it opens that category's history, and appears once the node has reported that kind of telemetry.

![Radion tietonäyttö – paikallinen laite](../../assets/screenshots/nodes_detail_local.png)

The **Position** row expands to show location data for nodes that share GPS:

![Sijaintivälilehden sisältö](../../assets/screenshots/nodes_position.png)

> ℹ️ **Huomautus:** Mittarit näkyvät vain, jos etäradio on lähettänyt ne. Mittarit päivittyvät kunkin radion telemetria-asetuksissa määritetyin väliajoin.

## Laitteen mittausloki

Perustoimintatiedot, jotka jokainen radio raportoi:

| Metrijärjestelmä       | Kuvaus                                                   |
| ---------------------- | -------------------------------------------------------- |
| Akun varaustaso        | Nykyinen akun varaustaso                                 |
| Jännite                | Akun jännitelukema                                       |
| Kanavan käyttöaste     | Percentage of local airtime in use                       |
| Lähetysajan käyttöaste | Percentage of the last hour this node spent transmitting |
| Käyttöaika             | Aika viimeisestä uudelleenkäynnistyksestä                |

Device Metrics has no cards on the node detail screen. Use the chart button on its row to open the Device Metrics screen, where battery level, voltage, ChUtil, and AirUtil are plotted over time and every reading — uptime included — is listed with its timestamp underneath. Pick a time frame at the top of the screen, and use the save icon in the app bar to export the visible history as CSV.

> 💡 **Tip:** Where a category does show cards — Environment, Air Quality, and Power — touch & hold a card to copy its value to the clipboard. On a chart screen, pinch to zoom the time axis.

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

The **Air-Quality Metrics** row is always listed on the node detail screen; its chart button appears once the node has reported air-quality telemetry. Ilmanlaatu-näkymässä voit:

- Valita kaavioille **aikajakson**.
- Suodattaa **mittarisiruilla** — vain mittarit, joista on dataa, näytetään.
- **Päivittää ja pyytää** uusimmat ilmanlaatutelemetriatiedot.
- **Viedä CSV-tiedostoon** analysoitavaksi taulukkolaskentaohjelmassa.

See [Telemetry & Sensors](telemetry-and-sensors) for supported air-quality hardware.

## Signaalin laatu

Radiosignaalin laatutiedot:

| Metrijärjestelmä | Kuvaus                                                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| SNR              | Signaali-kohinasuhde (suurempi SNR on parempi)                                                         |
| RSSI             | Vastaanotetun signaalin voimakkuusindikaattori (RSSI) (lähempänä nollaa on parempi) |
| Kohinataso       | Paikallinen taustakohina dBm-arvona (negatiivisempi on hiljaisempi)                                    |

### Signaalin laadun viitearvot

Signal quality is rated from **SNR relative to the active LoRa modem preset's demodulation floor**, not from fixed thresholds — a given SNR means different things on different presets (for example, −16 dB rates Good on Long Fast but None on Short Fast). When RSSI and a noise-floor reading are both available, the app also rates their difference against the preset limit and uses the worse rating. Otherwise, RSSI is display-only. Taulukossa _raja_ tarkoittaa esiasetuksen SNR-rajaa.

| Laatu       | Kriteerit                          |
| ----------- | ---------------------------------- |
| Hyvä        | SNR esiasetuksen rajan yläpuolella |
| Kohtalainen | alle 5,5 dB rajan alapuolella      |
| Huono       | 5,5–7,5 dB rajan alapuolella       |
| ei mitään   | yli 7,5 dB rajan alapuolella       |

Katso [Signaalimittarin toiminta](signal-meter), jos haluat täydellisen selityksen.

Local Stats from your connected node are also shown in Signal Quality when available. These logs include noise floor, traffic counters, relay counters, online node counts, and uptime. The noise floor chart uses a dashed reference line at −85 dBm to help identify a busy RF environment.

- **Request** — ask the connected node for a fresh Local Stats telemetry report
- **Tyhjennä** — poista tämän radion Local Stats -lokit
- **Tallenna** — vie näkyvä Local Stats -historia CSV-tiedostoon

## Virranhallinnan arvot

Virranhallintatelemetria (edellyttää INA-anturia tai yhteensopivaa laitteistoa):

| Metrijärjestelmä | Kuvaus                                        |
| ---------------- | --------------------------------------------- |
| Jännite          | Kanavakohtainen jännitelukema                 |
| Virta            | Kanavakohtainen virrankulutus milliampeereina |

The node detail screen shows cards for channels 1 to 3. Use the chart button on the **Power Metrics** row to open the chart screen, which lists a chip for every channel that reported data — up to eight — and charts the one you select. Use the label field under the chips to give a channel a name of your own, such as Solar or Battery. The app does not derive a wattage figure from voltage and current.

## Reitinselvitys

Reitinselvitys näyttää viestin kulkeman reitin verkossa:

1. From the node detail screen's **Telemetry** section, tap the refresh button on the **Traceroute** row. You cannot traceroute your own node. The app sends at most one traceroute every 30 seconds, to any node, and **Trace Route** in the node list's touch & hold menu shares that cooldown.
2. Sovellus lähettää reitinselvityspyynnön kohderadiolle.
3. Results show each hop with its SNR.

### Reitinselvityksen tulosten lukeminen

A traceroute is a round trip, so each saved result carries a hop count in each direction, **Forward Hops** and **Return Hops**, plus the **Round Trip** time in seconds. A result marked **Direct** reached the target with no relay in between. Tap a result to read the route traced toward the destination and the route traced back to you, with the SNR of every hop. That view offers **View on map**, which draws the same path, as long as the start and destination nodes have both shared a position.

A relay that didn't record itself in the route shows as **Unknown hop**, and a relay your node has no record of shows as its node ID, such as `!a1b2c3d4`.

The history also keeps traceroutes this app didn't request:

- **Observed** marks a response from this node to a request made elsewhere, such as one started from your node's own screen. It has hop counts but no round-trip time, and its points on the chart are hollow rings.
- **Traced by** _node_ marks a traceroute that node ran to yours. It shows the inbound route with the SNR of each hop, but no return route, no round-trip time, and no map, and it stays off the chart.

A result marked **No Response** means the target never answered. It may be out of range, asleep, or configured not to reply. Wait for the 30-second cooldown to clear and try again; if it keeps failing, send a direct message first to confirm the node is reachable at all.

## Sijainnin Loki

The chart button on the **Position** row opens the node's position history: its track on a map, and a list of every position report with latitude, longitude, satellites in view, altitude, speed, heading, and timestamp. From the app bar you can:

- **Export GPX** to save the track as a GPX file. Reports without a GPS fix are left out.
- Save the list as CSV.
- **Clear** the recorded track, after a confirmation. This can't be undone.
- Ask a remote node for a fresh position.

## Naapuritieto

Näyttää, mitkä radiot tietty radio voi kuulla suoraan, mikä auttaa ymmärtämään verkon topologiaa. The **Neighbor Info** row appears once that node's neighbor list has reached the app. See [Local Mesh Discovery](discovery#neighbor-info) for turning the module on.

## Isäntälaitteen mittausarvot

Nodes that run Meshtastic on a Linux host, such as a Raspberry Pi, report the host's own health — free memory, free disk space, one-, five-, and fifteen-minute load averages, and how long the host has been up. The **Host Metrics** row is always listed; its chart button appears once a node has reported them.

## Pax mittarit

A node running the PAX counter module reports how many Wi-Fi and Bluetooth devices it saw nearby, as a crowd-size estimate, and charts the two counts alongside their total. The **PAX Metrics** row is always listed; its chart button appears once a node has reported them. The counts are of devices, not people.

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — radioluettelo, suodatus ja lajittelu
- [Telemetria ja anturit](telemetry-and-sensors) — tuetut anturit ja määritykset
- [Signaalimittari](signal-meter) — miten signaalin laatu lasketaan SNR- ja RSSI-arvoista
- [Paikallinen mesh-verkon etsintä](discovery) — reitiselvityksen tiedot ja naapuritiedot
- [Yksiköt ja aluekohtaiset asetukset](units-and-locale) — lämpötilan, etäisyyden ja nopeuden näyttömuodot
