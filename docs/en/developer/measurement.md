---
title: Measurement & Formatting
parent: Developer Guide
nav_order: 9
last_updated: 2026-09-19
description: How MetricFormatter and NumberFormatter format measurements, and how the app resolves metric or imperial units from locale and user preference.
aliases:
  - measurement
  - metric-formatter
  - number-formatter
---

# Measurement & Formatting

How the Meshtastic Android/KMP app formats numbers, units, and locale-sensitive values.

## Overview

All measurement data transmitted by Meshtastic radios uses **metric units** (meters, °C, hPa, m/s, etc.). The app converts and formats these values for display using two core utilities:

| Utility | Location | Purpose |
|---|---|---|
| `MetricFormatter` | `core/common/.../util/MetricFormatter.kt` | Converts and formats physical measurements (temperature, pressure, speed, etc.) |
| `NumberFormatter` | `core/common/.../util/NumberFormatter.kt` | Decimal formatting: `format` follows the OS locale, `formatInvariant` keeps a fixed dot separator |
| `MeasureFormatting` | `core/common/.../util/MeasureFormatting.kt` | Pairs a fixed English unit symbol with a locale-formatted number, for the units that convert |

Both live in `org.meshtastic.core.common.util` and are available to all KMP targets (Android, Desktop, iOS).

## MetricFormatter API

`MetricFormatter` is a Kotlin `object` with pure functions for each measurement type:

```kotlin
object MetricFormatter {
    fun temperature(celsius: Float, isFahrenheit: Boolean): String
    fun voltage(volts: Float, decimalPlaces: Int = 2): String
    fun current(milliAmps: Float, decimalPlaces: Int = 1): String
    fun percent(value: Float, decimalPlaces: Int = 1): String
    fun humidity(value: Float): String
    fun pressure(hPa: Float, decimalPlaces: Int = 1): String
    fun snr(value: Float?, decimalPlaces: Int = 1): String
    fun rssi(value: Int?): String
    fun degreeSymbol(isFahrenheit: Boolean): String
    fun percent(value: Int): String
    fun windSpeed(metersPerSecond: Float, isImperial: Boolean, decimalPlaces: Int = 1): String
    fun rainfall(millimeters: Float, isImperial: Boolean, decimalPlaces: Int = 1): String
    fun weight(kilograms: Float, isImperial: Boolean, decimalPlaces: Int = 2): String
}

`snr` and `rssi` take a nullable reading and render the `—` placeholder when it is absent.
```

### Usage

```kotlin
// Temperature — Fahrenheit conversion is handled automatically
MetricFormatter.temperature(22.5f, isFahrenheit = true)  // "72.5°F"
MetricFormatter.temperature(22.5f, isFahrenheit = false)  // "22.5°C"

// Signal metrics
MetricFormatter.snr(-5.2f)    // "-5.2 dB"
MetricFormatter.rssi(-97)     // "-97 dBm"

// Environment
MetricFormatter.pressure(1013.25f)  // "1013.3 hPa"
MetricFormatter.humidity(65.0f)     // "65%"
MetricFormatter.windSpeed(3.7f, isImperial = false)  // "13.3 km/h"
MetricFormatter.windSpeed(3.7f, isImperial = true)   // "8.3 mph"
MetricFormatter.rainfall(12.3f, isImperial = false)  // "12.3 mm"
MetricFormatter.rainfall(12.3f, isImperial = true)   // "0.5 in"

// Power
MetricFormatter.voltage(3.95f)      // "3.95 V"
MetricFormatter.current(125.0f)     // "125.0 mA"
```

## NumberFormatter

`NumberFormatter` has two halves, and picking the wrong one is the mistake to avoid:

```kotlin
object NumberFormatter {
    fun format(value: Double, decimalPlaces: Int): String        // follows the OS locale
    fun format(value: Float, decimalPlaces: Int): String         // follows the OS locale
    fun formatInvariant(value: Double, decimalPlaces: Int): String  // fixed dot separator
}
```

**Use `format` for anything a person reads.** It delegates to the platform's own number formatter
through an `expect`/`actual` (`java.text.NumberFormat` on JVM and Android), so a number reads the
way that user writes numbers. `NaN` and infinity render as `—`.

**Use `formatInvariant` for anything a machine parses** — a CoT payload another client reads, or a
value written out and read back. Localizing those turns `1.5` into `1,5` and breaks the reader.
It rounds half away from zero to match the locale path, which matters because firmware reports SNR
in quarter-dB steps and ties are routine.

## Unit Conversion

Four measurements convert away from metric for display, each gated by a boolean flag sourced from the device locale or preferences:

| Measurement | Flag | Source | Conversion |
|---|---|---|---|
| `temperature` | `isFahrenheit` | `getSystemTemperatureUnit()` | `°F = °C × 1.8 + 32` |
| `windSpeed` | `isImperial` | `getSystemMeasurementSystem()` | m/s × 3.6 → km/h, or × 2.23694 → mph |
| `rainfall` | `isImperial` | `getSystemMeasurementSystem()` | mm ÷ 25.4 → in |
| `weight` | `isImperial` | `getSystemMeasurementSystem()` | kg × 2.20462 → lb |

The two source functions (in `core/common/.../util/MeasurementSystem.kt`) are deliberately separate: some locales mix systems (the UK uses miles for distance but Celsius for temperature), so temperature must never be derived from the distance unit. On Android, `getSystemTemperatureUnit()` delegates to `androidx.core.text.util.LocalePreferences`, which resolves CLDR locale data and honors the Android 14+ Regional preferences temperature override.

The in-app **Units** choice (`UnitsOverride`, stored in `UiPrefs`) is folded in by `LocaleUnitsProvider`, which is the only place display code takes units from. A Konsist rule (`MeasurementSystemSourceTest`) keeps direct reads of the OS resolution out of the rest of the codebase, because a direct read follows the locale but ignores the setting. A forced system carries its temperature with it (metric → °C, imperial → °F), overriding even an explicit OS regional temperature preference.

`getSystemMeasurementSystem()` resolves the locale in this order (temperature is separate: as described above, `getSystemTemperatureUnit()` reads the regional temperature preference via `LocalePreferences`, shares only the region backfill, and falls back to Celsius):

1. The `ms` Unicode extension (the Android 16+ Measurement system preference) wins outright.
2. A locale with no region — the in-app language picker offers bare tags like `en` — takes its region from the system configuration rather than letting ICU guess one from the language.
3. Anything still unclassified falls back to metric, never imperial.

The Android and Desktop implementations share the region table and the override reader in `commonMain`, so the two clients cannot disagree about the same locale.

Everything else (voltage, current, pressure, SNR, RSSI, humidity, percent) displays in its native metric units. The user-facing [Units & Locale](../user/units-and-locale) page explains what end users see.

## Adding a New Measurement Type

To add a new measurement formatter:

1. **Add a function to `MetricFormatter`** in `core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/MetricFormatter.kt`:

   ```kotlin
   fun radiation(microSieverts: Float, decimalPlaces: Int = 2): String =
       "${NumberFormatter.format(microSieverts, decimalPlaces)} μSv/h"
   ```

2. **Add tests** in `core/common/src/commonTest/`:

   ```kotlin
   @Test
   fun radiationFormatting() {
       assertEquals("0.15 μSv/h", MetricFormatter.radiation(0.15f))
       assertEquals("1.23 μSv/h", MetricFormatter.radiation(1.234f))
   }
   ```

3. **Use in UI** — call from any `commonMain` composable or ViewModel:

   ```kotlin
   Text(text = MetricFormatter.radiation(node.radiationLevel))
   ```

4. **Run verification**:
   ```bash
   ./gradlew :core:common:allTests
   ```

## DateFormatter

Date and time formatting uses the `DateFormatter` `expect object` with platform-specific `actual` implementations:

| Function | Output Example |
|---|---|
| `formatRelativeTime()` | "5 min ago" |
| `formatDateTime()` | "May 13, 2026 2:30 PM" |
| `formatShortDate()` | "May 13" |
| `formatTime()` | "2:30 PM" |
| `formatTimeWithSeconds()` | "2:30:45 PM" |
| `formatDate()` | "2026-05-13" |
| `formatDateTimeShort()` | "5/13/26 2:30 PM" |

Unlike `MetricFormatter`, `DateFormatter` is declared with `expect`/`actual` (an `expect object` in `commonMain`, an `actual object` per platform) because date formatting inherently depends on platform locale APIs.

## Design Decisions

| Decision | Rationale |
|---|---|
| Display formatting follows the OS locale | A number shown to a user should read the way that user writes numbers |
| `formatInvariant` keeps a fixed `.` for machine-read values | Interop payloads and re-parsed values break if localized |
| Only temperature, wind speed, rainfall and weight convert | The remaining metric units are universally understood in their native form |
| `object` singleton pattern | Stateless utility — no instance management needed |

## Related

- **User-facing docs**: [Units & Locale](../user/units-and-locale) explains what end users see
- **Source code**: `core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/MetricFormatter.kt`
- **Tests**: `core/common/src/commonTest/kotlin/org/meshtastic/core/common/util/MetricFormatterTest.kt`
