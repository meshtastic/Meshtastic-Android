---
title: Sõlme mõõdikud
parent: Kasutusjuhend
nav_order: 5
last_updated: 2026-08-27
description: Telemeetria armatuurlauad iga võrgusõlme kohta – seadme tervis, keskkonnaandurid, õhu kvaliteet, signaali kvaliteet, võimsus, marsruut ja asukoha ajalugu.
aliases:
  - meetriline
  - telemeetria
  - sõlme-mõõdikud
  - signaal
---

# Sõlme mõõdikud

Sõlme detailvaates on iga kärgvõrgu sõlme kohta põhjalikud telemeetria ja mõõdikud.

## Seadme mõõdikud

Basic operating information reported by each node:

| Meetriline     | Kirjeldus                                |
| -------------- | ---------------------------------------- |
| Aku tase       | Praegune aku protsent                    |
| Vool           | Aku pinge näit                           |
| Kanali kasutus | Percentage of airtime consumed           |
| Airtime        | Transmission time used by this node      |
| Töötamise aeg  | Viimasest taaskäivitamisest möödunud aeg |

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

Keskkonnanäitajaid joonistatakse aja jooksul lihtsaks trendianalüüsiks – temperatuur, niiskus ja rõhk saavad igaüks oma joondiagrammi, mille mõõtühik kuvatakse Y-teljel.

BME680 **IAQ (siseõhu kvaliteet)** indeks on üksik väärtus vahemikus 0–500+, mis on tuletatud gaasitakistusest ja näidatud värvikoodiga skaalal _Suurepärane_ kuni _Ohtlikult saastunud_:

![Siseõhu kvaliteedi indeks-skaala suurepärasest kuni ohtlikult saastunud](../../assets/screenshots/node-metrics_iaq_scale.png)

> 💡 **Vihje:** Keskkonnamõõdikute jaoks on vaja kaugsõlmega ühendatud andurit. Not all nodes report environmental data. Vaata [Telemeetria & Sensorid](telemetry-and-sensors), et näha toetatud andurite täieliku loendit.

## Air Quality Metrics

Air Quality is a dedicated metrics view for nodes equipped with a particulate-matter and/or CO₂ sensor. See on **eraldi BME680 siseõhu kvaliteedi näidust**, mis on loetletud keskkonnamõõdikute all – siseõhu kvaliteet on ühtne gaasitakistusest tuletatud indeks, samas kui õhukvaliteedi vaade diagrammib aluseks olevaid tahkete osakeste ja CO₂ mõõtmisi.

| Meetriline            | Unit       | Kirjeldus                                                                                                                                                                                                                                             |
| --------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PM1.0 | µg/m³      | Particulate matter up to 1.0 micron                                                                                                                                                                                                   |
| PM2,5                 | µg/m³      | Particulate matter up to 2.5 microns                                                                                                                                                                                                  |
| PM10                  | µg/m³      | Particulate matter up to 10 microns                                                                                                                                                                                                                   |
| AQI                   | EPA indeks | EPA **NowCast** õhukvaliteedi indeks on arvutatud hiljutise PM2.5 ajaloo põhjal ja sellel on värvikoodiga raskusastme silt. Kuvatakse PM2.5 kõrval, kui on kogunenud piisavalt näite. |
| CO₂                   | ppm        | Süsinikdioksiidi kontsentratsioon                                                                                                                                                                                                                     |
| CO₂ temperatuur       | °C / °F    | Temperature reported by the CO₂ sensor itself (e.g. SCD4x)                                                                                                                                         |
| CO₂ niiskus           | %          | Relative humidity reported by the CO₂ sensor                                                                                                                                                                                                          |

CO₂ näidud on vastavalt raskusastmele värvikoodiga kodeeritud, et õhukvaliteeti oleks kiirelt loetav:

| Band         | CO₂ ulatus (ppm) | Värv        |
| ------------ | ----------------------------------- | ----------- |
| Hea          | < 1000     | Roheline    |
| Stuffy       | < 2000     | Merevaik    |
| Kehv         | < 5000     | Oranž       |
| Ebaturvaline | < 30000    | Punane      |
| Evakueeru    | ≥ 30000                             | Tume punane |

![Õhukvaliteedi näidud koos värvikoodiga CO₂ sisalduse raskusastme kohta](../../assets/screenshots/node-metrics_air_quality.png)

An air-quality log/metrics button appears on the node detail screen **only when the node has reported air-quality telemetry**. From the Air Quality view you can:

- Vali diagrammide jaoks **ajaraam**.
- Filtreeri **mõõdikute kiipide** abil — kuvatakse ainult andmeid sisaldavad mõõdikud.
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

Signaali kvaliteeti hinnatakse **SNR-i põhjal, mis on seotud aktiivse LoRa modemi eelseadistuse demodulatsiooni alumise piiriga**, mitte fikseeritud läviväärtuste põhjal – antud SNR tähendab erinevatel eelseadistustel erinevat väärtust (nt −15 dB on LongSlow režiimil hea, kuid ShortFast režiimil mittekasutatav). RSSI on kuvatud, aga see ei ole osa hinnangust. Letting `limit` be the preset's SNR limit:

| Kvaliteet | Kriteerium                                |
| --------- | ----------------------------------------- |
| Hea       | SNR above the preset's limit              |
| Rahuldav  | vähem kui 5,5 dB piirväärtusest allpool   |
| Halb      | 5,5 dB kuni 7,5 dB piirväärtusest allpool |
| Puudub    | üle 7,5 dB piirväärtusest allpool         |

See [Understanding the Signal Meter](signal-meter) for the full explanation.

Kohalik statistika ühendatud raadiost kuvatakse ka signaali kvaliteedi all, kui see on saadaval. Need logid sisaldavad mürataset, liiklusloendureid, edastusloendureid, võrgus olevate sõlmede arvu ja raadio tööaega. The noise floor chart uses a dashed reference line at -85 dBm to help identify a busy RF environment. Kasuta **Taotle**, et küsida ühendatud raadiost uut kohaliku statistika telemeetriaaruannet, **Tühjenda**, et eemaldada selle sõlme kohaliku statistika logi ja **Salvesta**, et salvestada nähtavat kohaliku statistika ajalugu CSV-failina.

## Võimsusnäitajad

Power management telemetry (requires INA sensor or compatible hardware):

| Meetriline | Kirjeldus                      |
| ---------- | ------------------------------ |
| Voltage    | Per-channel voltage reading    |
| Pinge      | Per-channel draw, in milliamps |

Up to three channels (ch1–ch3) are charted, each with a label you can edit. The app does not
derive a wattage figure from them.

## Traceroute

Traceroute shows the path a message takes through the mesh:

1. From the node detail screen, tap **Traceroute**.
2. The app sends a traceroute request to the target node.
3. Results show each hop with SNR/RSSI values.

### Reading Traceroute Results

```
You → Node A (SNR: 8.5) → Node B (SNR: 5.2) → Target
```

Each hop represents a relay node that forwarded the message.

## Position Log

Historical position data for nodes that share their location:

- GPS coordinates
- Altitude
- Speed (if moving)
- Timestamp for each position report

## Neighbor Info

Shows which nodes a given node can directly hear, useful for understanding mesh topology.

## Viewing Metrics

1. Navigate to **Nodes**.
2. Tap the node you want to inspect.
3. Select the metric category from the detail tabs.

![Node detail — local device](../../assets/screenshots/nodes_detail_local.png)

The position tab shows location data for nodes that share GPS:

![Position inline content](../../assets/screenshots/nodes_position.png)

> ℹ️ **Note:** Metrics are only available when they have been reported by the remote node. Metrics update at intervals configured on each node's telemetry settings.

## Related Topics

- [Nodes](nodes) — node list, filtering, and sorting
- [Telemetry & Sensors](telemetry-and-sensors) — supported sensors and configuration
- [Signal Meter](signal-meter) — how signal quality is calculated from SNR and RSSI
- [Discovery](discovery) — traceroute details and neighbor info
- [Units & Locale](units-and-locale) — temperature, distance, and speed display formats

---
