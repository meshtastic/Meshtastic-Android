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

import android.content.res.Resources
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import androidx.core.text.util.LocalePreferences
import java.util.Locale

actual fun currentLocaleCode(): String = Locale.getDefault().language

actual fun currentRegionCode(): String = Locale.getDefault().country

actual fun currentLocaleQualifier(): String {
    val locale = Locale.getDefault()
    val country = locale.country
    return if (country.isNotEmpty()) "${locale.language}-r$country" else locale.language
}

actual fun getSystemMeasurementSystem(): MeasurementSystem {
    val locale = Locale.getDefault()

    val override = measurementSystemOverride(locale.getUnicodeLocaleType(MEASUREMENT_SYSTEM_EXTENSION))
    if (override != null) return override

    val resolved = locale.withSystemRegionIfMissing()

    // getMeasurementSystem is API 28+; below that the region table is the only source available.
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        measurementSystemForRegion(resolved.country)
    } else {
        when (LocaleData.getMeasurementSystem(ULocale.forLocale(resolved))) {
            LocaleData.MeasurementSystem.SI -> MeasurementSystem.METRIC

            LocaleData.MeasurementSystem.US -> MeasurementSystem.IMPERIAL

            // ICU's third bucket. The UK measures road distance in miles, so it groups with
            // imperial for distance; its Celsius temperatures come from getSystemTemperatureUnit().
            LocaleData.MeasurementSystem.UK -> MeasurementSystem.IMPERIAL

            // Anything ICU cannot classify is metric. Never fall through to imperial: that is what
            // made a region-less locale print feet for most of the world.
            else -> MeasurementSystem.METRIC
        }
    }
}

/**
 * Returns this locale with the device's region filled in when it carries none.
 *
 * [Locale.getDefault] is the *app* locale, and the in-app language picker offers bare tags like `en`. ICU completes a
 * missing region from likely-subtags, which turns `en` into `en_US` — so choosing English in Settings silently switched
 * a user in a metric country to feet. Picking a language must not pick a measurement system, so the region comes from
 * the system configuration, which no per-app locale overrides.
 */
internal fun Locale.withSystemRegionIfMissing(): Locale {
    if (country.isNotEmpty()) return this

    val systemLocales = Resources.getSystem().configuration.locales
    val region =
        (0 until systemLocales.size()).asSequence().map { systemLocales[it].country }.firstOrNull { it.isNotEmpty() }

    // Builder rejects locales carrying legacy or ill-formed fields, and a system locale can lack a
    // region of its own; the unmodified locale is the safe answer in both cases.
    return region?.let { code -> runCatching { Locale.Builder().setLocale(this).setRegion(code).build() }.getOrNull() }
        ?: this
}

// LocalePreferences resolves from CLDR data and, on Android 14+, the user's Regional preferences
// override. Kelvin (a valid regional preference) falls back to Celsius, which the app can display.
// The same region fill as distance, so a language-only app locale cannot split the two systems.
actual fun getSystemTemperatureUnit(): TemperatureUnit =
    when (LocalePreferences.getTemperatureUnit(Locale.getDefault().withSystemRegionIfMissing())) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> TemperatureUnit.FAHRENHEIT
        else -> TemperatureUnit.CELSIUS
    }
