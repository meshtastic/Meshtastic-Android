---
title: Sõlme mõõdikud
parent: Kasutusjuhend
nav_order: 5
last_updated: 2026-08-29
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
3. Vali detailvaadete vahekaartidelt mõõdiku kategooria.

![Sõlme detailid — kohalik seade](../../assets/screenshots/nodes_detail_local.png)

Asukoha vahekaart kuvab GPS-i jagavate sõlmede asukohaandmeid:

![Asukoha tekstisisene sisu](../../assets/screenshots/nodes_position.png)

> ℹ️ **Note:** Metrics are only available when they have been reported by the remote node. Mõõdikud värskendatakse iga sõlme telemeetria sätetes seadistatud intervallidega.

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

An air-quality log/metrics button appears on the node detail screen **only when the node has reported air-quality telemetry**. From the Air Quality view you can:

- Vali diagrammide jaoks **ajaraam**.
- Filtreeri **mõõdikute kiipide** abil — kuvatakse ainult andmeid sisaldavad mõõdikud.
- **Refresh / request** the latest air-quality telemetry.
- **Ekspordi CSV** analüüsimiseks arvutustabeli vaates.

> 💡 **Vihje:** Õhukvaliteedi mõõdikute jaoks on vaja kaugsõlmega ühendatud andurit. Vaata [Telemeetria & Sensorid](telemetry-and-sensors) toetatud raudvara kohta.

## Signaali mõõdikud

Radio signal quality information:

| Meetriline      | Kirjeldus                                                                      |
| --------------- | ------------------------------------------------------------------------------ |
| SNR             | Signal-to-Noise Ratio (higher is better)                    |
| RSSI            | Received Signal Strength Indicator (closer to 0 is better)  |
| Noise Floor     | Local background RF noise in dBm (more negative is quieter) |
| Hüppete loendur | Viimase sõnumi kärgvõrgu hüpete arv                                            |

### Signal Quality Reference

Signaali kvaliteeti hinnatakse **SNR-i põhjal, mis on seotud aktiivse LoRa modemi eelseadistuse demodulatsiooni alumise piiriga**, mitte fikseeritud läviväärtuste põhjal – antud SNR tähendab erinevatel eelseadistustel erinevat väärtust (nt −15 dB on LongSlow režiimil hea, kuid ShortFast režiimil mittekasutatav). RSSI on kuvatud, aga see ei ole osa hinnangust. In the table, _limit_ is the preset's SNR limit.

| Kvaliteet | Kriteerium                                |
| --------- | ----------------------------------------- |
| Hea       | SNR above the preset's limit              |
| Rahuldav  | vähem kui 5,5 dB piirväärtusest allpool   |
| Halb      | 5,5 dB kuni 7,5 dB piirväärtusest allpool |
| Puudub    | üle 7,5 dB piirväärtusest allpool         |

See [Understanding the Signal Meter](signal-meter) for the full explanation.

Local Stats from your connected radio are also shown in Signal Metrics when available. Need logid sisaldavad mürataset, liiklusloendureid, edastusloendureid, võrgus olevate sõlmede arvu ja raadio tööaega. The noise floor chart uses a dashed reference line at -85 dBm to help identify a busy RF environment.

- **Request** — ask the connected radio for a fresh Local Stats telemetry report
- **Clear** — remove Local Stats logs for that node
- **Save** — export the visible Local Stats history as CSV

## Võimsusnäitajad

Power management telemetry (requires INA sensor or compatible hardware):

| Meetriline | Kirjeldus                      |
| ---------- | ------------------------------ |
| Vool       | Per-channel voltage reading    |
| Pinge      | Per-channel draw, in milliamps |

Up to three sensor channels (ch1–ch3) are charted, each with a label you can edit. The app does not
derive a wattage figure from them.

## Marsruudi

Traceroute näitab sõnumi teed läbi kärgvõrgu:

1. Sõlme üksikasjade ekraanil puuduta **Traceroute**.
2. Rakendus saadab sihtsõlmele traceroute-päringu.
3. Tulemused näitavad iga hüpet koos SNR/RSSI väärtustega.

### Traceroute'i tulemuste lugemine

```
Sina → seade A (SNR: 8,5) → seade B (SNR: 5,2) → sihtkoht
```

Iga hüpe esindab vahendussõlme, mis sõnumi edastas.

## Asukoha logi

Asukohta jagavate sõlmede ajaloolised asukohaandmed:

- GPS koordinaadid
- Kõrgus
- Kiirus (kui liigub)
- Timestamp for each position report

## Naabruse teave

Näitab, milliseid sõlmi antud sõlm otse kuuleb, kasulik kärgvõrgu topoloogia mõistmiseks.

## Seotud teemad

- [Nodes](nodes) — node list, filtering, and sorting
- [Telemeetria & Sensorid](telemetry-and-sensors) — toetatud andurid ja seadistus
- [Signal Meter](signal-meter) — how signal quality is calculated from SNR and RSSI
- [Local Mesh Discovery](discovery) — traceroute details and neighbor info
- [Ühikud ja lokaat](units-and-locale) — temperatuuri, kauguse ja kiiruse kuvamise ühikud
