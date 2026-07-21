---
title: Ühikud, mõõtühikud ja lokaat
parent: User Guide
nav_order: 16
last_updated: 2026-07-08
description: Kuidas rakendus vormindab temperatuuri, vahemaad, kiirust ja muid mõõtmisi vastavalt seadme lokaadile.
---

# Ühikud, mõõtühikud ja lokaat

Meshtastic rakendus kuvab automaatselt temperatuure, vahemaid, kiirusi ja aegu ühikutes, milleks sinu seade on seadistatud – rakenduses pole vaja sätteid muuta.

---

## How It Works

Meshtastic radios always transmit data in **metric units** (meters, °C, m/s, hPa, etc.). Kui rakendus need andmed vastu võtab, teisendab ja kuvab see väärtused seadme lokaadi määratud ühikutes.

On Android, your measurement preferences are determined by your system **Language & Region** settings. Töölaual (JVM) kasutab rakendus JVM-i vaikesätet „lokaat”.

> 💡 **Tip:** You never need to toggle units inside the app. Muuda oma süsteemi mõõtmiste eelistusi ja kõik Meshtasticu ekraanid värskendatakse automaatselt – sõlmede üksikasjad, telemeetriadiagrammid, ilm, kõrgus ja palju muud.

---

## Temperatuur

Temperature values from environment sensors are transmitted as **°C** and displayed based on your device's temperature unit preference.

![Environment metrics with temperature](../../assets/screenshots/nodes_environment_metrics.png)

| Sinu sätted | Teadmiseks |
| ----------- | ---------- |
| Celsius     | 22°C       |
| Fahrenheit  | 72°F       |

This affects all temperature displays throughout the app: node environment telemetry, soil temperature, dew point, and telemetry chart axes.

## Distance & Altitude

Distances between nodes and GPS altitudes are transmitted as **meters** and automatically scaled and converted.

![Distance info display](../../assets/screenshots/nodes_distance_info.png)

| Sinu sätted                      | Small Distance | Large Distance         | Kõrgus   |
| -------------------------------- | -------------- | ---------------------- | -------- |
| Meetriline                       | 350 m          | 2.5 km | 1,200 m  |
| Imperial (US) | 1,148 ft       | 1.6 mi | 3,937 ft |

The app uses natural scaling — short distances stay in meters or feet, while longer distances switch to kilometres or miles automatically.

### Where these appear

- **Node list** — distance and bearing to each node
- **Node detail** — altitude, distance from your position
- **Kaart** — teekonnapunktide vahemaad, traceroute'i hüppevahemaad
- **Compass** — distance to selected node

## Kiirus

GPSi maapealne kiirus kuvatakse lokaadi eelistatud kiiruseühikus.

| Sinu sätted                      | Teadmiseks |
| -------------------------------- | ---------- |
| Meetriline                       | 12 km/h    |
| Imperial (US) | 7 mph      |

## Tuul

Wind speed and gust data from environment sensors are transmitted as **m/s** and converted for display.

| Sinu sätted                      | Teadmiseks |
| -------------------------------- | ---------- |
| Meetriline                       | 5 m/s      |
| Imperial (US) | 11 mph     |

Wind readings appear in the **Node Detail** environment section and the **Environment Telemetry** charts.

## Rainfall

Rainfall measurements (1-hour and 24-hour totals) are transmitted as **mm** and converted for display.

| Sinu sätted                      | Teadmiseks |
| -------------------------------- | ---------- |
| Meetriline                       | 12 mm      |
| Imperial (US) | 0,5 in     |

## Units That Never Change

Mõned ühikud on rahvusvahelised standardid ja neid kuvatakse ühtemoodi olenemata lokaat:

| Measurement                 | Unit                           | Why                                   |
| --------------------------- | ------------------------------ | ------------------------------------- |
| Baromeetrii rõhk            | hPa                            | International meteorological standard |
| Heading / bearing           | ° (degrees) | Universal navigation convention       |
| Radiatsioon                 | μR/hr                          | Standard dosimetry unit               |
| GPS coordinates             | decimal degrees                | Universal geographic standard         |
| Niiskus, aku, mulla niiskus | %                              | Universal                             |

## Date & Time

All timestamps throughout the app — last heard, message times, telemetry logs, chart axes — follow your device's date and time preferences.

| Sätted               | What It Controls | Example                                          |
| -------------------- | ---------------- | ------------------------------------------------ |
| **24-Hour Time**     | Kella vorming    | 14:30 vs 2:30 PM |
| **Kuupäeva vorming** | Date ordering    | 09/05/2026 vs 05/09/2026                         |

The app also uses **relative time** where it makes sense — for example, "5 min ago" or "2 hours ago" in the node list — which is automatically localised into your device language.

## Changing Your Measurement System (Android)

On Android, your measurement system (metric vs imperial) is tied to your region setting:

1. Open **Android Settings → System → Language & Region**
2. Change your **Region** or **Measurement units** preference
3. Tagasi Meshtastic juurde — väärtused värskendatakse kohe

> 💡 **Vihje:** Kogu mõõtühikute vormindamine toimub tsentraalselt ja arvestab platvormi lokaaduga, seega püsivad ühikud kogu rakenduses ühtsed.

## Related Topics

- [Node Metrics](node-metrics) — where temperature, distance, and sensor values are displayed
- [Telemeetia & Sensorid](telemetry-and-sensors) — andurid, mis neid mõõtmisi teevad
- [Measurement & Formatting](../developer/measurement) — developer reference for the formatting utilities
- [Settings — Radio & User](settings-radio-user) — region setting that drives unit selection

---

