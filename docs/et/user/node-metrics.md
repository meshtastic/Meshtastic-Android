---
title: Sõlme mõõdikud
nav_order: 5
last_updated: 2026-10-21
description: Telemeetria armatuurlauad iga võrgusõlme kohta – seadme tervis, keskkonnaandurid, õhu kvaliteet, signaali kvaliteet, võimsus, marsruut ja asukoha ajalugu.
aliases:
  - meetriline
  - telemeetria
  - sõlme-mõõdikud
  - signaal
---

# Sõlme mõõdikud

Sõlme detailvaates on iga kärgvõrgu sõlme kohta põhjalikud telemeetria ja mõõdikud.

## Viewing Metrics

1. Mine **Seadmed**.
2. Puuduta sõlme, mida soovite kontrollida.
3. Scroll to the **Telemetry** section and find the category you want — **Signal Quality**, **Device Metrics**, **Environment Metrics**, **Air-Quality Metrics**, **Power Metrics**, **Position**, and the rest.
4. Tap the refresh button on a row to ask the node for a fresh reading. The chart button beside it opens that category's history, and appears once the node has reported that kind of telemetry.

![Sõlme detailid — kohalik seade](../../assets/screenshots/nodes_detail_local.png)

The **Position** row expands to show location data for nodes that share GPS:

![Asukoha tekstisisene sisu](../../assets/screenshots/nodes_position.png)

> ℹ️ **Note:** Metrics are only available when they have been reported by the remote node. Mõõdikud värskendatakse iga sõlme telemeetria sätetes seadistatud intervallidega.

## Seadme mõõdikud

Basic operating information reported by each node:

| Meetriline     | Kirjeldus                                                |
| -------------- | -------------------------------------------------------- |
| Aku tase       | Praegune aku protsent                                    |
| Vool           | Aku pinge näit                                           |
| Kanali kasutus | Percentage of local airtime in use                       |
| Saate kasutus  | Percentage of the last hour this node spent transmitting |
| Töötamise aeg  | Viimasest taaskäivitamisest möödunud aeg                 |

Device Metrics has no cards on the node detail screen. Use the chart button on its row to open the Device Metrics screen, where battery level, voltage, ChUtil, and AirUtil are plotted over time and every reading — uptime included — is listed with its timestamp underneath. Pick a time frame at the top of the screen, and use the save icon in the app bar to export the visible history as CSV.

> 💡 **Tip:** Where a category does show cards — Environment, Air Quality, and Power — touch & hold a card to copy its value to the clipboard. On a chart screen, pinch to zoom the time axis.

## Keskkonnamõõdikud

Environmental sensor data (requires compatible hardware):

| Meetriline                           | Sensor Examples       |
| ------------------------------------ | --------------------- |
| Temperatuur                          | BME280, BME680, SHT31 |
| Niiskus                              | BME280, BME680, SHT31 |
| Baromeetri rõhk                      | BME280, BMP280        |
| Gaasi surve                          | BME680                |
| IAQ (Air Quality) | BME680                |

Environment metrics are charted over time — temperature, humidity, and pressure each get their own line chart with the measurement unit displayed on the Y axis.

BME680 **IAQ (siseõhu kvaliteet)** indeks on üksik väärtus vahemikus 0–500+, mis on tuletatud gaasitakistusest ja näidatud värvikoodiga skaalal _Suurepärane_ kuni _Ohtlikult saastunud_:

![Siseõhu kvaliteedi indeks-skaala suurepärasest kuni ohtlikult saastunud](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **Vihje:** Keskkonnamõõdikute jaoks on vaja kaugsõlmega ühendatud andurit. Not all nodes report environmental data. Vaata [Telemeetria & Sensorid](telemetry-and-sensors), et näha toetatud andurite täieliku loendit.

## Air Quality Metrics

Air Quality is a dedicated metrics view for nodes equipped with a particulate-matter and/or CO₂ sensor. See on **eraldi BME680 siseõhu kvaliteedi näidust**, mis on loetletud keskkonnamõõdikute all – siseõhu kvaliteet on ühtne gaasitakistusest tuletatud indeks, samas kui õhukvaliteedi vaade diagrammib aluseks olevaid tahkete osakeste ja CO₂ mõõtmisi.

| Meetriline            | Unit       | Kirjeldus                                                                                                                                                                                                                       |
| --------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PM1.0 | µg/m³      | Particulate matter up to 1.0 micron                                                                                                                                                                             |
| PM2,5                 | µg/m³      | Particulate matter up to 2.5 microns                                                                                                                                                                            |
| PM10                  | µg/m³      | Particulate matter up to 10 microns                                                                                                                                                                                             |
| AQI                   | EPA indeks | EPA **NowCast** AQI computed from the node's recent PM2.5 history, with a color-coded severity label. Kuvatakse PM2.5 kõrval, kui on kogunenud piisavalt näite. |
| CO₂                   | ppm        | Süsinikdioksiidi kontsentratsioon                                                                                                                                                                                               |
| CO₂ temperatuur       | °C / °F    | Temperature reported by the CO₂ sensor itself (e.g. SCD4x)                                                                                                                   |
| CO₂ niiskus           | %          | Relative humidity reported by the CO₂ sensor                                                                                                                                                                                    |

CO₂ readings are color-coded by severity so you can read air quality at a glance:

| Band         | CO₂ ulatus (ppm) | Värv        |
| ------------ | ----------------------------------- | ----------- |
| Hea          | < 1000     | Roheline    |
| Stuffy       | < 2000     | Merevaik    |
| Kehv         | < 5000     | Oranž       |
| Ebaturvaline | < 30000    | Punane      |
| Evakueeru    | ≥ 30000                             | Tume punane |

![Õhukvaliteedi näidud koos värvikoodiga CO₂ sisalduse raskusastme kohta](../../assets/screenshots/node-metrics_air_quality.png)

The **Air-Quality Metrics** row is always listed on the node detail screen; its chart button appears once the node has reported air-quality telemetry. From the Air Quality view you can:

- Vali diagrammide jaoks **ajaraam**.
- Filtreeri **mõõdikute kiipide** abil — kuvatakse ainult andmeid sisaldavad mõõdikud.
- **Refresh / request** the latest air-quality telemetry.
- **Ekspordi CSV** analüüsimiseks arvutustabeli vaates.

See [Telemetry & Sensors](telemetry-and-sensors) for supported air-quality hardware.

## Levi Kvaliteet

Radio signal quality information:

| Meetriline  | Kirjeldus                                                                      |
| ----------- | ------------------------------------------------------------------------------ |
| SNR         | Signal-to-Noise Ratio (higher is better)                    |
| RSSI        | Received Signal Strength Indicator (closer to 0 is better)  |
| Noise Floor | Local background RF noise in dBm (more negative is quieter) |

### Signal Quality Reference

Signal quality is rated from **SNR relative to the active LoRa modem preset's demodulation floor**, not from fixed thresholds — a given SNR means different things on different presets (for example, −16 dB rates Good on Long Fast but None on Short Fast). When RSSI and a noise-floor reading are both available, the app also rates their difference against the preset limit and uses the worse rating. Otherwise, RSSI is display-only. In the table, _limit_ is the preset's SNR limit.

| Kvaliteet | Kriteerium                                |
| --------- | ----------------------------------------- |
| Hea       | SNR above the preset's limit              |
| Rahuldav  | vähem kui 5,5 dB piirväärtusest allpool   |
| Halb      | 5,5 dB kuni 7,5 dB piirväärtusest allpool |
| Puudub    | üle 7,5 dB piirväärtusest allpool         |

See [Understanding the Signal Meter](signal-meter) for the full explanation.

Local Stats from your connected node are also shown in Signal Quality when available. These logs include noise floor, traffic counters, relay counters, online node counts, and uptime. The noise floor chart uses a dashed reference line at −85 dBm to help identify a busy RF environment.

- **Request** — ask the connected node for a fresh Local Stats telemetry report
- **Clear** — remove Local Stats logs for that node
- **Save** — export the visible Local Stats history as CSV

## Võimsusnäitajad

Power management telemetry (requires INA sensor or compatible hardware):

| Meetriline | Kirjeldus                      |
| ---------- | ------------------------------ |
| Vool       | Per-channel voltage reading    |
| Pinge      | Per-channel draw, in milliamps |

The node detail screen shows cards for channels 1 to 3. Use the chart button on the **Power Metrics** row to open the chart screen, which lists a chip for every channel that reported data — up to eight — and charts the one you select. Use the label field under the chips to give a channel a name of your own, such as Solar or Battery. The app does not derive a wattage figure from voltage and current.

## Marsruudi

Traceroute näitab sõnumi teed läbi kärgvõrgu:

1. From the node detail screen's **Telemetry** section, tap the refresh button on the **Traceroute** row. You cannot traceroute your own node. The app sends at most one traceroute every 30 seconds, to any node, and **Trace Route** in the node list's touch & hold menu shares that cooldown.
2. Rakendus saadab sihtsõlmele traceroute-päringu.
3. Results show each hop with its SNR.

### Traceroute'i tulemuste lugemine

A traceroute is a round trip, so each saved result carries a hop count in each direction, **Forward Hops** and **Return Hops**, plus the **Round Trip** time in seconds. A result marked **Direct** reached the target with no relay in between. Tap a result to read the route traced toward the destination and the route traced back to you, with the SNR of every hop. That view offers **View on map**, which draws the same path, as long as the start and destination nodes have both shared a position.

A relay that didn't record itself in the route shows as **Unknown hop**, and a relay your node has no record of shows as its node ID, such as `!a1b2c3d4`.

The history also keeps traceroutes this app didn't request:

- **Observed** marks a response from this node to a request made elsewhere, such as one started from your node's own screen. It has hop counts but no round-trip time, and its points on the chart are hollow rings.
- **Traced by** _node_ marks a traceroute that node ran to yours. It shows the inbound route with the SNR of each hop, but no return route, no round-trip time, and no map, and it stays off the chart.

A result marked **No Response** means the target never answered. It may be out of range, asleep, or configured not to reply. Wait for the 30-second cooldown to clear and try again; if it keeps failing, send a direct message first to confirm the node is reachable at all.

## Asukoha logi

The chart button on the **Position** row opens the node's position history: its track on a map, and a list of every position report with latitude, longitude, satellites in view, altitude, speed, heading, and timestamp. From the app bar you can:

- **Export GPX** to save the track as a GPX file. Reports without a GPS fix are left out.
- Save the list as CSV.
- **Clear** the recorded track, after a confirmation. This can't be undone.
- Ask a remote node for a fresh position.

## Naabruse teave

Näitab, milliseid sõlmi antud sõlm otse kuuleb, kasulik kärgvõrgu topoloogia mõistmiseks. The **Neighbor Info** row appears once that node's neighbor list has reached the app. See [Local Mesh Discovery](discovery#neighbor-info) for turning the module on.

## Hosti mõõdik

Nodes that run Meshtastic on a Linux host, such as a Raspberry Pi, report the host's own health — free memory, free disk space, one-, five-, and fifteen-minute load averages, and how long the host has been up. The **Host Metrics** row is always listed; its chart button appears once a node has reported them.

## Pax mõõdiku logi

A node running the PAX counter module reports how many Wi-Fi and Bluetooth devices it saw nearby, as a crowd-size estimate, and charts the two counts alongside their total. The **PAX Metrics** row is always listed; its chart button appears once a node has reported them. The counts are of devices, not people.

## Seotud teemad

- [Nodes](nodes) — node list, filtering, and sorting
- [Telemeetria & Sensorid](telemetry-and-sensors) — toetatud andurid ja seadistus
- [Signal Meter](signal-meter) — how signal quality is calculated from SNR and RSSI
- [Local Mesh Discovery](discovery) — traceroute details and neighbor info
- [Ühikud ja lokaat](units-and-locale) — temperatuuri, kauguse ja kiiruse kuvamise ühikud
