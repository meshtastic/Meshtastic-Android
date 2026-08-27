---
title: Units, Measurement & Locale
parent: User Guide
nav_order: 16
last_updated: 2026-08-27
description: How the app formats temperature, distance, speed, and other measurements based on your device locale.
---

# Units, Measurement & Locale

The Meshtastic app automatically displays temperatures, distances, speeds, and times in the units your device is configured to use. If your device's settings can't express the units you want, an in-app **Units** setting overrides them.

---

## How It Works

Meshtastic radios always transmit data in **metric units** (meters, °C, m/s, hPa, etc.). When the app receives this data, it converts and displays values in whatever unit system your device's locale specifies.

On Android, your measurement preferences are determined by your system **Language & Region** settings. On Desktop (JVM), the app uses the JVM's default `Locale`.

Units follow your device's **region**, not the display language. Choosing a plain language — like **English** in the app's own Language setting or Android's per-app language — keeps the region your device is set to; only a choice that names a region of its own (like **English (Canada)**) brings that region's units with it. On Android 16+, the system-wide **Measurement system** preference overrides the region entirely.

> 💡 **Tip:** By default there is nothing to configure — change your system measurement preferences and every screen in Meshtastic updates automatically. If your device offers no working region or measurement setting (some manufacturer builds don't), set **Settings → Units** in the app instead.

---

## The Radio's Own Screen Is Separate

**Device → Display → Units** configures the screen on the radio, not the app. So do **Use 12-Hour Clock** and **Always Point North** — all three apply to the node's display only. Temperature on that screen has its own setting, [**Telemetry → Display Fahrenheit**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

If your node list shows miles while the radio's screen shows kilometres, this is why: the two are set in different places. Changing the device setting will never alter what the app displays. See the [Display Config](https://meshtastic.org/docs/configuration/radio/display) guide on meshtastic.org for the device-side options.

## 온도

Temperature values from environment sensors are transmitted as **°C** and displayed based on your device's temperature unit preference.

![Environment metrics with temperature](../../assets/screenshots/nodes_environment_metrics.png)

| Your Setting | You See |
| ------------ | ------- |
| Celsius      | 22°C    |
| Fahrenheit   | 72°F    |

This affects all temperature displays throughout the app: node environment telemetry, soil temperature, dew point, and telemetry chart axes.

Temperature follows your locale's **temperature preference**, independent of the distance system. Locales that mix systems work correctly — a UK phone shows miles for distance but **°C** for temperature. On Android 14+, the **Temperature** regional preference (Settings → System → Languages → Regional preferences) overrides the locale default.

## Distance & Altitude

Distances between nodes and GPS altitudes are transmitted as **meters** and automatically scaled and converted.

![Distance info display](../../assets/screenshots/nodes_distance_info.png)

| Your Setting                     | Small Distance | Large Distance         | Altitude |
| -------------------------------- | -------------- | ---------------------- | -------- |
| Metric                           | 350 m          | 2.5 km | 1,200 m  |
| Imperial (US) | 1,148 ft       | 1.6 mi | 3,937 ft |

The app uses natural scaling — short distances stay in meters or feet, while longer distances switch to kilometres or miles automatically.

### Where these appear

- **Node list** — distance and bearing to each node
- **Node detail** — altitude, distance from your position
- **Map** — waypoint distances, traceroute hop distances
- **Compass** — distance to selected node

## Speed

GPS ground speed is displayed in your locale's preferred speed unit.

| Your Setting                     | You See |
| -------------------------------- | ------- |
| Metric                           | 12 km/h |
| Imperial (US) | 7 mph   |

## 바람

Wind speed and gust data from environment sensors are transmitted as **m/s** and converted for display.

| Your Setting                     | You See |
| -------------------------------- | ------- |
| Metric                           | 5 m/s   |
| Imperial (US) | 11 mph  |

Wind readings appear in the **Node Detail** environment section and the **Environment Telemetry** charts.

## Rainfall

Rainfall measurements (1-hour and 24-hour totals) are transmitted as **mm** and converted for display.

| Your Setting                     | You See                |
| -------------------------------- | ---------------------- |
| Metric                           | 12 mm                  |
| Imperial (US) | 0.5 in |

## Units That Never Change

Some units are international standards and are displayed the same way regardless of your locale:

| Measurement                      | Unit                           | Why                                   |
| -------------------------------- | ------------------------------ | ------------------------------------- |
| Barometric pressure              | hPa                            | International meteorological standard |
| Heading / bearing                | ° (degrees) | Universal navigation convention       |
| 복사                               | μR/hr                          | Standard dosimetry unit               |
| GPS coordinates                  | decimal degrees                | Universal geographic standard         |
| Humidity, battery, soil moisture | %                              | Universal                             |

## Date & Time

All timestamps throughout the app — last heard, message times, telemetry logs, chart axes — follow your device's date and time preferences.

| Setting          | What It Controls | Example                                          |
| ---------------- | ---------------- | ------------------------------------------------ |
| **24-Hour Time** | Clock format     | 14:30 vs 2:30 PM |
| **Date Format**  | Date ordering    | 09/05/2026 vs 05/09/2026                         |

The app also uses **relative time** where it makes sense — for example, "5 min ago" or "2 hours ago" in the node list — which is automatically localised into your device language.

## Changing Your Measurement System

By default the app follows your device, and your measurement system (metric vs imperial) is tied to your region setting:

1. Open **Android Settings → System → Language & Region**
2. Change your **Region**
3. On Android 16+, **Measurement system** overrides the region for every measurement
4. On Android 14+, temperature can be overridden on its own under **Regional preferences → Temperature**
5. Return to Meshtastic — values update immediately

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

> 💡 **Tip:** All measurement formatting is handled centrally and respects your platform's locale, so units stay consistent everywhere in the app.

## Related Topics

- [Node Metrics](node-metrics) — where temperature, distance, and sensor values are displayed
- [Telemetry & Sensors](telemetry-and-sensors) — the sensors that produce these measurements
- [Measurement & Formatting](../developer/measurement) — developer reference for the formatting utilities
- [Settings — Radio & User](settings-radio-user) — region setting that drives unit selection
- [Display Config](https://meshtastic.org/docs/configuration/radio/display) — units, clock, and compass settings for the radio's own screen, on meshtastic.org

---

