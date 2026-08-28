---
title: Telemeetria & Sensorid
parent: Kasutusjuhend
nav_order: 9
last_updated: 2026-08-27
description: Kärgvõrgu andurite andmed — toetatud keskkonna-, õhukvaliteedi- ja võimsusandurid ning konfiguratsiooni- ja vaatamisjuhendid.
aliases:
  - sensorid
  - environment
  - ilm
  - power-metrics
---

# Telemetria & sensorid

Meshtastic sõlmed saavad koguda ja jagada andurite andmeid kärgvõrgu kaudu.

## Ülevaade

Telemeetria võimaldab anduritega varustatud sõlmedel levitada keskkonna-, energiatarbimise ja seadme terviseteavet. This data is visible on the node detail screen and can be logged over time.

## Device Telemetry

Kõik Meshtastic sõlmed edastavad seadme põhitelemeetriat:

| Meetriline       | Kirjeldus                              | Tüüpiline ulatus                   |
| ---------------- | -------------------------------------- | ---------------------------------- |
| Aku tase         | Charge percentage                      | 0–100%                             |
| Vool             | Aku pinge                              | 3,0–4,2V (LiPo) |
| Kanali kasutus   | Kohalikult kasutatud eetriaja %        | 0–100%                             |
| Eetri kasutus TX | Selle sõlme poolt kasutatud eetriaja % | 0–100%                             |
| Töötamise aeg    | Seconds since last boot                | Varies                             |

## Environment Sensors

Supported environmental sensors:

### Temperature & Humidity

| Andur   | Temperatuur | Niiskus | Õhurõhk | Sõnumid                 |
| ------- | ----------- | ------- | ------- | ----------------------- |
| BME280  | ✓           | ✓       | ✓       | Recommended all-in-one  |
| BME680  | ✓           | ✓       | ✓       | Adds gas resistance/IAQ |
| SHT31   | ✓           | ✓       | —       | High accuracy           |
| MCP9808 | ✓           | —       | —       | Precision temperature   |
| LPS22   | —           | —       | ✓       | Pressure only           |

### Air Quality

| Andur    | Meetriline           | Sõnumid                                                                                                                                   |
| -------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| BME680   | Gas Resistance / IAQ | Volatile organic compounds                                                                                                                |
| PMSA003I | PM1,0, PM2,5, PM10   | Particulate matter                                                                                                                        |
| SEN55    | PM, Temp, Humidity   | Multi-sensor. Its NOx and VOC indices are recorded and included in a CSV export, but are not yet shown as cards or charts |

### Soil

| Meetriline          | Ühik    | Sõnumid                                         |
| ------------------- | ------- | ----------------------------------------------- |
| Pinnase temperatuur | °C / °F | Reported alongside soil moisture by soil probes |
| Pinnase niiskus     | %       | Volumetric water content                        |

Both appear as info cards on the node detail screen, next to the other environment readings.

### Valgus & UV

| Andur    | Meetriline                              |
| -------- | --------------------------------------- |
| OPT3001  | Ambient valgus (lux) |
| VEML7700 | Ambient valgus (lux) |
| LTR390   | UV indeks                               |

## Võimsusnäitajad

Nodes with INA-series power sensors can report:

| Meetriline | Kirjeldus                       |
| ---------- | ------------------------------- |
| Vool       | Per-channel voltage reading     |
| Pinge      | Per-channel current draw, in mA |

Up to three channels are reported (ch1–ch3), and each can be given its own label — Solar or Battery, say — from the node detail screen. There is no separate wattage reading; the app charts voltage and current, and does not compute power from them.

Kasulik päikesepaneelide laadimise või aku seisundi jälgimiseks kaugsõlmedes.

## Configuring Telemetry

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → Telemeetria**.
2. Each metric group has its own enable toggle and its own interval:

   - **Device Metrics** — battery, channel and airtime utilisation
   - **Environment Metrics** — temperature, humidity, pressure and the other sensor readings
   - **Air Quality Metrics** — particulate and CO₂ readings
   - **Power Metrics** — the per-channel voltage and current readings

   Environment metrics additionally have an on-screen toggle and a Fahrenheit toggle for the
   device's own display.

### Choosing an Interval

> 💡 **Tip:** These are nominal values, not hard schedules. On a congested mesh the firmware
> automatically backs off to longer intervals based on how many nodes are online, so you do not
> need to hand-tune them for mesh size. Lengthen them deliberately only to save battery.

## Air Quality Metrics

Nodes with particulate matter or CO₂ sensors report air quality data:

| Meetriline            | Ühik  | Kirjeldus                         |
| --------------------- | ----- | --------------------------------- |
| PM1.0 | µg/m³ | Ultrafine particulate matter      |
| PM2.5 | µg/m³ | Fine particulate matter           |
| PM10                  | µg/m³ | Coarse particulate matter         |
| CO₂                   | ppm   | Süsinikdioksiidi kontsentratsioon |

CO₂ sensors such as the SCD4x also report their own temperature and humidity, which appear alongside the readings above. From PM2.5 history the app additionally derives an **EPA NowCast AQI** value.

CO₂ näit on värvikoodiga märgitud vastavalt raskusastmele (Hea → Umbne → Halb → Ohtlik → Evakueeruda). Täpsete ppm-vahemike, värvide ja õhukvaliteedi indeksite üksikasjade saamiseks vaata [Sõlme mõõdikud — õhukvaliteet](node-metrics#air-quality-metrics).

Õhukvaliteedi andmeid saab vaadata infokaartidena sõlme detailvaates, aja jooksul graafikule lisada ja CSV-vormingusse salvestada.

## Viewing Telemetry

1. Mine **Seadmed** ja vali seade.
2. Telemeetria jaotised kuvatakse detailvaates:
   - Device Metrics (always available)
   - Environment Metrics (if sensors present)
   - Power Metrics (if INA sensor present)
   - Air Quality Metrics (if PM/CO₂ sensor present)
3. Ajaloolised graafikud näitavad aja jooksul trende.

![Telemeetria toimingud](../../assets/screenshots/node-metrics_telemetric_actions.png)

## Veaotsing

- **Keskkonnaandmeid ei kuvata?** Kaugühenduse jaoks on vaja ühendada füüsiline andur (nt BME280 I2C-l). Seadme telemeetria (aku, tööaeg) on ​​alati saadaval, kuid keskkonnamõõdikute jaoks on vaja riistvara.
- **Vananenud näidud?** Kontrolli aruandlusintervalli – väga pikad intervallid (7200+ sekundit) tähendavad harva andmete uuendamist. Samuti veendu, et kaugsõlm on endiselt võrgus.
- **Sensor conflict on I2C bus?** Some sensors share I2C addresses. Kui samal siinil on mitu andurit, kontrolli raadio jadapordi arendajaväljundis aadresside kokkupõrkeid.

## Seotud teemad

- [Node Metrics](node-metrics) — view telemetry data on the node detail screen
- [Seaded — Moodulid ja administreerimine](settings-module-admin) — telemeetriamooduli konfiguratsioon
- [Ühikud ja lokaat](units-and-locale) — temperatuuri ja rõhu kuvamise ühikud

---

