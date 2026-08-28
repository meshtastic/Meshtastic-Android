---
title: Ühikud, mõõtühikud ja lokaat
parent: Kasutusjuhend
nav_order: 16
last_updated: 2026-08-27
description: Kuidas rakendus vormindab temperatuuri, vahemaad, kiirust ja muid mõõtmisi vastavalt seadme lokaadile.
aliases:
  - measurement
  - units
  - locale
  - metric
  - imperial
---

# Ühikud, mõõtühikud ja lokaat

The Meshtastic app automatically displays temperatures, distances, speeds, and times in the units your device is configured to use. If your device's settings can't express the units you want, an in-app **Units** setting overrides them.

---

## Kuidas see toimib

Meshtastic raadiod edastavad andmeid alati **meetrilistes ühikutes** (meetrites, °C, m/s, hPa jne). Kui rakendus need andmed vastu võtab, teisendab ja kuvab see väärtused seadme lokaadi määratud ühikutes.

Androidis määravad mõõteseaded süsteemi **Keel ja piirkond** seaded. Töölaual (JVM) kasutab rakendus JVM-i vaikesätet „lokaat”.

Units follow your device's **region**, not the display language. Choosing a plain language — like **English** in the app's own Language setting or Android's per-app language — keeps the region your device is set to; only a choice that names a region of its own (like **English (Canada)**) brings that region's units with it. On Android 16+, the system-wide **Measurement system** preference overrides the region entirely.

> 💡 **Tip:** By default there is nothing to configure — change your system measurement preferences and every screen in Meshtastic updates automatically. If your device offers no working region or measurement setting (some manufacturer builds don't), set **Settings → Units** in the app instead.

---

## The Radio's Own Screen Is Separate

**Device → Display → Units** configures the screen on the radio, not the app. So do **Use 12-Hour Clock** and **Always Point North** — all three apply to the node's display only. Temperature on that screen has its own setting, [**Telemetry → Display Fahrenheit**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

If your node list shows miles while the radio's screen shows kilometres, this is why: the two are set in different places. Changing the device setting will never alter what the app displays. See the [Display Config](https://meshtastic.org/docs/configuration/radio/display) guide on meshtastic.org for the device-side options.

## Temperatuur

Temperature values from environment sensors are transmitted as **°C** and displayed based on your device's temperature unit preference.

![Keskkonna mõõdikud koos temperatuuriga](../../assets/screenshots/nodes_environment_metrics.png)

| Sinu sätted | Teadmiseks |
| ----------- | ---------- |
| Celsius     | 22°C       |
| Fahrenheit  | 72°F       |

This affects all temperature displays throughout the app: node environment telemetry, soil temperature, dew point, and telemetry chart axes.

Temperature follows your locale's **temperature preference**, independent of the distance system. Locales that mix systems work correctly — a UK phone shows miles for distance but **°C** for temperature. Android 14+ puhul tühistab **Temperatuuri** piirkondlik eelistus (Seaded → Süsteem → Keeled → Piirkondlikud eelistused) lokaalse vaikesätte.

## Distance & Altitude

Sõlmede vahelised kaugused ja GPS kõrgused edastatakse **meetrites** ning skaleeritakse ja teisendatakse automaatselt.

![Vahemaa info kuvamine](../../assets/screenshots/nodes_distance_info.png)

| Sinu sätted                      | Small Distance | Large Distance         | Kõrgus   |
| -------------------------------- | -------------- | ---------------------- | -------- |
| Meetriline                       | 350 m          | 2.5 km | 1200 m   |
| Imperial (US) | 1,148 ft       | 1.6 mi | 3,937 ft |

Rakendus kasutab loomulikku skaleerimist – lühikesed vahemaad jäävad meetritesse või jalgadesse, pikemad vahemaad aga muutuvad automaatselt kilomeetriteks või miilideks.

### Where these appear

- **Node list** — distance and bearing to each node
- **Sõlme detail** — kõrgus, kaugus asukohast
- **Kaart** — teekonnapunktide vahemaad, traceroute'i hüppevahemaad
- **Kompass** — kaugus valitud sõlmeni

## Kiirus

GPSi maapealne kiirus kuvatakse lokaadi eelistatud kiiruseühikus.

| Sinu sätted                      | Teadmiseks |
| -------------------------------- | ---------- |
| Meetriline                       | 12 km/h    |
| Imperial (US) | 7 mph      |

## Tuul

Wind speed, gust and lull are transmitted by the sensor as **m/s** and converted for display — the app shows the unit weather forecasts use in your region, not the raw sensor unit.

| Sinu sätted                      | Teadmiseks                |
| -------------------------------- | ------------------------- |
| Meetriline                       | 18.0 km/h |
| Imperial (US) | 11.2 mph  |

All three read in the same unit wherever they appear: the Node Detail environment section, the Environment Telemetry log, and the charts.

## Weight

Readings from a connected scale are transmitted in **kg** and converted for display.

| Sinu sätted                      | Teadmiseks              |
| -------------------------------- | ----------------------- |
| Meetriline                       | 1.50 kg |
| Imperial (US) | 3.31 lb |

## Rainfall

Rainfall measurements (1-hour and 24-hour totals) are transmitted as **mm** and converted for display.

| Your Setting                     | You See                 |
| -------------------------------- | ----------------------- |
| Metric                           | 12.0 mm |
| Imperial (US) | 0.47 in |

## Units That Never Change

Mõned ühikud on rahvusvahelised standardid ja neid kuvatakse ühtemoodi olenemata lokaat:

| Measurement                 | Unit                           | Why                                   |
| --------------------------- | ------------------------------ | ------------------------------------- |
| Baromeetrii rõhk            | hPa                            | International meteorological standard |
| Heading / bearing           | ° (degrees) | Universal navigation convention       |
| Radiatsioon                 | µR/h                           | Standard dosimetry unit               |
| GPS koordinaadid            | decimal degrees                | Universal geographic standard         |
| Niiskus, aku, mulla niiskus | %                              | Universal                             |

## Date & Time

All timestamps throughout the app — last heard, message times, telemetry logs, chart axes — follow your device's date and time preferences.

| Sätted               | What It Controls | Example                                           |
| -------------------- | ---------------- | ------------------------------------------------- |
| **24 tunnine aeg**   | Kella vorming    | 14:30 või 2:30 PM |
| **Kuupäeva vorming** | Date ordering    | 09/05/2026 või 05/09/2026                         |

Rakendus kasutab ka **suhtelist aega**, kui see on loogiline – näiteks sõlmede loendis „5 minutit tagasi” või „2 tundi tagasi” –, mis lokaliseeritakse automaatselt sinu seadme keelde.

## Changing Your Measurement System

By default the app follows your device, and your measurement system (metric vs imperial) is tied to your region setting:

1. Ava **Androidi seaded → Süsteem → Keel ja piirkond**
2. Change your **Region**
3. On Android 16+, **Measurement system** overrides the region for every measurement
4. On Android 14+, temperature can be overridden on its own under **Regional preferences → Temperature**
5. Tagasi Meshtastic juurde — väärtused värskendatakse kohe

Not every English region is fully metric. **English (United Kingdom)** uses miles and feet for distance, so the node list shows miles and altitude in feet. For metric distances, set the app's **Units** setting to Metric (below), or choose a fully metric region such as English (Canada), English (Ireland), or English (New Zealand).

Some phones do not offer the **Regional preferences** menu at all and list only English (United States). On those devices, use the app's **Units** setting below.

### Overriding the units in the app

Not every device can express every preference — some manufacturer builds ship no regional preferences at all, some
offer only one English variant, and UK regions are imperial for distance even if you'd rather read altitude in
metres. For those cases the app has its own switch:

1. Open **Meshtastic Settings → Units**
2. Choose **System default**, **Metric**, or **Imperial**
3. Every screen updates immediately — no restart needed

**System default** follows your device as described above. Forcing **Metric** or **Imperial** applies to
everything, temperature included (metric → °C, imperial → °F), even where the device's own regional preferences say
otherwise. The setting exists on Android and Desktop alike.

> 💡 **Vihje:** Kogu mõõtühikute vormindamine toimub tsentraalselt ja arvestab platvormi lokaaduga, seega püsivad ühikud kogu rakenduses ühtsed.

## Seotud teemad

- [Node Metrics](node-metrics) — where temperature, distance, and sensor values are displayed
- [Telemeetia & Sensorid](telemetry-and-sensors) — andurid, mis neid mõõtmisi teevad
- [Measurement & Formatting](../developer/measurement) — developer reference for the formatting utilities
- [Seaded — Raadio ja kasutaja](settings-radio-user) — piirkonna säte, mis määrab üksuse valiku
- [Display Config](https://meshtastic.org/docs/configuration/radio/display) — units, clock, and compass settings for the radio's own screen, on meshtastic.org

---

