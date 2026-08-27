/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.common.util

/** Represents the system's preferred measurement system. */
enum class MeasurementSystem {
    METRIC,
    IMPERIAL,
}

/** returns the system's preferred measurement system. */
expect fun getSystemMeasurementSystem(): MeasurementSystem

/**
 * The system's preferred temperature unit. Deliberately decoupled from [MeasurementSystem]: some locales mix systems
 * (the UK measures road distance in miles but temperature in Celsius), so temperature must never be derived from the
 * distance unit.
 */
enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
}

/**
 * Returns the temperature unit preferred by the OS locale, honoring the user's regional-preference override where the
 * platform supports one (Android 14+ Settings > System > Languages > Regional preferences).
 */
expect fun getSystemTemperatureUnit(): TemperatureUnit

/** Returns the device's current locale as a 2-letter ISO 639-1 language code (e.g. "en", "es", "fr"). */
expect fun currentLocaleCode(): String

/**
 * Returns the device's current region as a 2-letter ISO 3166-1 alpha-2 country code (e.g. "US", "DE"), or an empty
 * string when the region is unknown. Used to region-filter marketplace links.
 */
expect fun currentRegionCode(): String

/**
 * Returns the device locale as a CMP resource qualifier string. Examples: "pt-rBR", "zh-rCN", "fr" (no region when not
 * specified). Use this to construct locale-qualified file resource paths like "files-$qualifier/docs/...".
 */
expect fun currentLocaleQualifier(): String

/**
 * The Unicode locale extension key carrying the user's measurement-system override.
 *
 * Android 16 exposes it as Settings > System > Language & region > Measurement system, which appends `-u-ms-…` to the
 * locale.
 */
internal const val MEASUREMENT_SYSTEM_EXTENSION = "ms"

/**
 * Maps the `ms` Unicode extension to a [MeasurementSystem], or null when the user set no override.
 *
 * Read this before any region lookup. ICU parses the extension into the locale but resolves the measurement system from
 * region data alone, so the override is silently dropped unless it is honored here — the user turns their phone to
 * metric and the app keeps printing feet.
 */
internal fun measurementSystemOverride(extensionValue: String?): MeasurementSystem? = when (extensionValue) {
    "metric" -> MeasurementSystem.METRIC

    "ussystem",
    "uksystem",
    -> MeasurementSystem.IMPERIAL

    else -> null
}

/**
 * Maps a region code to its measurement system, for platforms with no ICU measurement data.
 *
 * Metric is the fallback: it is what all but a handful of regions use, and an unrecognized or absent region must never
 * silently become imperial.
 */
internal fun measurementSystemForRegion(region: String): MeasurementSystem = when (region.uppercase()) {
    // Liberia and Myanmar sit alongside the US in CLDR's measurementData; the UK is its own
    // system there, but it measures road distance in miles, so it groups with imperial for
    // distance. Temperature is decoupled — see TemperatureUnit.
    "US",
    "LR",
    "MM",
    "GB",
    -> MeasurementSystem.IMPERIAL

    else -> MeasurementSystem.METRIC
}

/**
 * The user's in-app units choice. [SYSTEM] follows the OS locale; the other two force one system everywhere.
 *
 * This exists because following the OS is not reachable for everyone: One UI ships no regional-preferences page, some
 * OEM builds offer no region choice at all (#6840), the UK's CLDR data is imperial for every length usage even though
 * altitude there is conventionally metres, and Android 16's Measurement system setting never reaches devices that
 * stopped at 15. The override is the escape hatch those users cannot get from the platform.
 */
enum class UnitsOverride(val value: Int) {
    SYSTEM(0),
    METRIC(1),
    IMPERIAL(2),
    ;

    companion object {
        fun fromValue(value: Int): UnitsOverride = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** Where the user's units choice is read from; implemented over the UI preferences store. */
interface UnitsOverrideSource {
    val override: kotlinx.coroutines.flow.StateFlow<UnitsOverride>
}
