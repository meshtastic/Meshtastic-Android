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

// There is no browser API for a measurement-system *override* the way Android 14+'s regional preferences expose
// one, so honoring MEASUREMENT_SYSTEM_EXTENSION (see MeasurementSystem.kt) is not possible here -- this is an
// accepted, honest platform gap, not an oversight. Region-based inference via the module's own
// measurementSystemForRegion is the best signal available, the same fallback the JVM desktop actual uses below
// Android P.
actual fun getSystemMeasurementSystem(): MeasurementSystem = measurementSystemForRegion(currentRegionCode())

// Likewise, there is no browser API for the OS regional-preferences temperature unit. CLDR's unitPreferenceData
// region list is small and static, so it is reused verbatim from the JVM actual rather than treated as
// JVM-specific logic.
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

/**
 * Parses `navigator.language` (a BCP-47 tag like `"en-US"`, or `"zh-Hans-CN"`) into a language and region, by hand
 * rather than via the `Intl.Locale` API's `.maximize()` -- that would infer a region for a language-only tag (an
 * ICU-quality nicety), but a plain split is enough to answer these three functions honestly, and keeps this file free
 * of another `js()` surface.
 */
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
