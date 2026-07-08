---
title: Sõlme mõõdikud
parent: User Guide
nav_order: 5
last_updated: 2026-07-08
description: Telemetry dashboards for each mesh node — device health, environment sensors, air quality, signal quality, power, traceroute, and position history.
aliases:
  - meetriline
  - telemetry
  - sõlme-mõõdikud
  - signal
---

# Sõlme mõõdikud

The node detail screen provides comprehensive telemetry and metrics for each node on your mesh.

## Seadme mõõdikud

Basic operating information reported by each node:

| Meetriline     | Kirjeldus                           |
| -------------- | ----------------------------------- |
| Battery Level  | Current battery percentage          |
| Vool           | Battery voltage reading             |
| Kanali kasutus | Percentage of airtime consumed      |
| Airtime        | Transmission time used by this node |
| Töötamise aeg  | Time since last reboot              |

Seadme mõõdikud kuvatakse individuaalsete kaartidena, millel on trendigraafikud, mis näitavad aku taset, pinget, kanali kasutamist, eetriaega ja tööaega aja jooksul.

> 💡 **Vihje:** Puuduta mis tahes mõõdikukaarti, et laiendada see ajalooliste andmepunktidega täisdiagrammiks. Ajatelje suumimiseks näpista sõrmed kokku.

## Keskkonnamõõdikud

Environmental sensor data (requires compatible hardware):

| Meetriline                           | Sensor Examples       |
| ------------------------------------ | --------------------- |
| Temperatuur                          | BME280, BME680, SHT31 |
| Niiskus                              | BME280, BME680, SHT31 |
| Baromeetri rõhk                      | BME280, BMP280        |
| Gaasi surve                          | BME680                |
| IAQ (Air Quality) | BME680                |

Environment metrics are charted over time for easy trend analysis — temperature, humidity, and pressure each get their own line chart with the measurement unit displayed on the Y axis.

BME680 **IAQ (siseõhu kvaliteet)** indeks on üksik väärtus vahemikus 0–500+, mis on tuletatud gaasitakistusest ja näidatud värvikoodiga skaalal _Suurepärane_ kuni _Ohtlikult saastunud_:

![Siseõhu kvaliteedi indeks-skaala suurepärasest kuni ohtlikult saastunud](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **Vihje:** Keskkonnamõõdikute jaoks on vaja kaugsõlmega ühendatud andurit. Not all nodes report environmental data. Vaata [Telemeetria & Sensorid](telemetry-and-sensors), et näha toetatud andurite täieliku loendit.

## Air Quality Metrics

Air Quality is a dedicated metrics view for nodes equipped with a particulate-matter and/or CO₂ sensor. See on **eraldi BME680 siseõhu kvaliteedi näidust**, mis on loetletud keskkonnamõõdikute all – siseõhu kvaliteet on ühtne gaasitakistusest tuletatud indeks, samas kui õhukvaliteedi vaade diagrammib aluseks olevaid tahkete osakeste ja CO₂ mõõtmisi.

| Meetriline            | Unit       | Kirjeldus                                                                                                                                                                                                                  |
| --------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PM1.0 | µg/m³      | Particulate matter up to 1.0 micron                                                                                                                                                                        |
| PM2,5                 | µg/m³      | Particulate matter up to 2.5 microns                                                                                                                                                                       |
| PM10                  | µg/m³      | Particulate matter up to 10 microns                                                                                                                                                                                        |
| AQI                   | EPA indeks | EPA **NowCast** AQI computed from your recent PM2.5 history, with a color-coded severity label. Shown next to PM2.5 once enough readings have accumulated. |
| CO₂                   | ppm        | Süsinikdioksiidi kontsentratsioon                                                                                                                                                                                          |
| CO₂ temperature       | °C / °F    | Temperature reported by the CO₂ sensor itself (e.g. SCD4x)                                                                                                              |
| CO₂ humidity          | %          | Relative humidity reported by the CO₂ sensor                                                                                                                                                                               |

CO₂ readings are color-coded by severity to make air quality easy to read at a glance:

| Band         | CO₂ Range (ppm) | Värv        |
| ------------ | ---------------------------------- | ----------- |
| Hea          | < 1000    | Roheline    |
| Stuffy       | < 2000    | Merevaik    |
| Kehv         | < 5000    | Oranž       |
| Ebaturvaline | < 30000   | Punane      |
| Evakueeru    | ≥ 30000                            | Tume punane |

![Air quality readings with color-coded CO₂ severity](../../assets/screenshots/node-metrics_air_quality.png)

An air-quality log/metrics button appears on the node detail screen **only when the node has reported air-quality telemetry**. From the Air Quality view you can:

- Select a **time frame** for the charts.
- Filter with **metric chips** — only metrics that have data are shown.
- **Refresh / request** the latest air-quality telemetry.
- **Ekspordi CSV** analüüsimiseks arvutustabeli vaates.

> 💡 **Vihje:** Õhukvaliteedi mõõdikute jaoks on vaja kaugsõlmega ühendatud andurit. If a node has no particulate or CO₂ sensor, the air-quality button won't appear. Vaata [Telemeetria & Sensorid](telemetry-and-sensors) toetatud raudvara kohta.

## Signaali mõõdikud

Radio signal quality information:

| Meetriline      | Kirjeldus                                                                      |
| --------------- | ------------------------------------------------------------------------------ |
| SNR             | Signal-to-Noise Ratio (higher is better)                    |
| RSSI            | Received Signal Strength Indicator (closer to 0 is better)  |
| Noise Floor     | Local background RF noise in dBm (more negative is quieter) |
| Hüppete loendur | Viimase sõnumi kärgvõrgu hüpete arv                                            |

### Signal Quality Reference

Signal quality is rated from **SNR relative to the active LoRa modem preset's demodulation floor**, not from fixed thresholds — a given SNR means different things on different presets (e.g. −15 dB is fine on LongSlow but unusable on ShortFast). RSSI is shown but is not part of the rating. Letting `limit` be the preset's SNR limit:

| Quality  | Criteria                                                         |
| -------- | ---------------------------------------------------------------- |
| Hea      | SNR above the preset's limit                                     |
| Rahuldav | less than 5.5 dB below the limit                 |
| Halb     | 5.5 dB to 7.5 dB below the limit |
| Puudub   | more than 7.5 dB below the limit                 |

See [Understanding the Signal Meter](signal-meter) for the full explanation.

Local Stats from your connected radio are also shown in Signal Quality when available. These logs include noise floor, traffic counters, relay counters, online node counts, and radio uptime. The noise floor chart uses a dashed reference line at -85 dBm to help identify a busy RF environment. Kasuta **Taotle**, et küsida ühendatud raadiost uut kohaliku statistika telemeetriaaruannet, **Tühjenda**, et eemaldada selle sõlme kohaliku statistika logi ja **Salvesta**, et salvestada nähtavat kohaliku statistika ajalugu CSV-failina.

## Võimsusnäitajad

Power management telemetry (requires INA sensor or compatible hardware):

| Meetriline  | Kirjeldus               |
| ----------- | ----------------------- |
| Bus Voltage | Supply voltage          |
| Pinge       | Power draw in milliamps |
| Toide       | Calculated wattage      |

## Marsruudi

Traceroute shows the path a message takes through the mesh:

1. Sõlme üksikasjade ekraanil puuduta **Traceroute**.
2. The app sends a traceroute request to the target node.
3. Tulemused näitavad iga hüpet koos SNR/RSSI väärtustega.

### Reading Traceroute Results

```
You → Node A (SNR: 8.5) → Node B (SNR: 5.2) → Target
```

Iga hüpe esindab vahendussõlme, mis sõnumi edastas.

## Asukoha logi

Historical position data for nodes that share their location:

- GPS coordinates
- Kõrgus
- Speed (if moving)
- Timestamp for each position report

## Naabruse teave

Shows which nodes a given node can directly hear, useful for understanding mesh topology.

## Viewing Metrics

1. Mine **Seadmed**.
2. Puuduta sõlme, mida soovite kontrollida.
3. Select the metric category from the detail tabs.

![Sõlme detailid — kohalik seade](../../assets/screenshots/nodes_detail_local.png)

The position tab shows location data for nodes that share GPS:

![Asukoha tekstisisene sisu](../../assets/screenshots/nodes_position.png)

> ⚠️ **Märkus:** Mõõdikud on saadaval ainult siis, kui need on kaugsõlme poolt esitatud. Mõõdikud värskendatakse iga sõlme telemeetria sätetes seadistatud intervallidega.

## Related Topics

- [Nodes](nodes) — node list, filtering, and sorting
- [Telemeetria & Sensorid](telemetry-and-sensors) — toetatud andurid ja seadistus
- [Signal Meter](signal-meter) — how signal quality is calculated from SNR and RSSI
- [Avasta](Discovery) — traceroute'i üksikasjad ja naabri teave
- [Units & Locale](units-and-locale) — temperature, distance, and speed display formats

---
