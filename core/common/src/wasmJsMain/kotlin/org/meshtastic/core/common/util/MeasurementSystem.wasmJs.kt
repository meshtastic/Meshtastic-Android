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

// No browser API for a measurement-system override (unlike Android 14+); region-based inference is the
// best available signal, same fallback the JVM actual uses below Android P.
actual fun getSystemMeasurementSystem(): MeasurementSystem = measurementSystemForRegion(currentRegionCode())

// No browser API for this either; CLDR's region list is reused verbatim from the JVM actual.
actual fun getSystemTemperatureUnit(): TemperatureUnit = when (currentRegionCode()) {
    "US",
    "BS",
    "BZ",
    "KY",
    "PR",
    "PW",
    -> TemperatureUnit.FAHRENHEIT

    else -> TemperatureUnit.CELSIUS
}

actual fun currentLocaleCode(): String = parsedBrowserLocale().language

actual fun currentRegionCode(): String = parsedBrowserLocale().region

actual fun currentLocaleQualifier(): String {
    val locale = parsedBrowserLocale()
    return if (locale.region.isNotEmpty()) "${locale.language}-r${locale.region}" else locale.language
}

private data class ParsedLocale(val language: String, val region: String)

/** Parses `navigator.language` (e.g. `"en-US"`) into language + region by hand, no `Intl.Locale`. */
private fun parsedBrowserLocale(): ParsedLocale {
    val tag = browserLanguage()
    if (tag.isBlank()) return ParsedLocale(DEFAULT_LANGUAGE, "")

    val subtags = tag.split('-')
    val language = subtags.firstOrNull()?.lowercase().orEmpty().ifEmpty { DEFAULT_LANGUAGE }
    val region =
        subtags
            .drop(1)
            .firstOrNull { it.length == REGION_SUBTAG_LENGTH && it.all(Char::isLetter) }
            ?.uppercase()
            .orEmpty()

    return ParsedLocale(language, region)
}

private const val DEFAULT_LANGUAGE = "en"
private const val REGION_SUBTAG_LENGTH = 2
