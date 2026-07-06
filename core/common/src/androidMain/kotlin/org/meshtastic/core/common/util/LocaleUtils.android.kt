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

import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

actual fun currentLocaleCode(): String = Locale.getDefault().language

actual fun currentRegionCode(): String = Locale.getDefault().country

actual fun currentLocaleQualifier(): String {
    val locale = Locale.getDefault()
    val country = locale.country
    return if (country.isNotEmpty()) "${locale.language}-r$country" else locale.language
}

@Suppress("MagicNumber")
actual fun getSystemMeasurementSystem(): MeasurementSystem {
    val locale = Locale.getDefault()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
            LocaleData.MeasurementSystem.SI -> MeasurementSystem.METRIC
            else -> MeasurementSystem.IMPERIAL
        }
    } else {
        when (locale.country.uppercase(locale)) {
            "US",
            "LR",
            "MM",
            "GB",
            -> MeasurementSystem.IMPERIAL

            else -> MeasurementSystem.METRIC
        }
    }
}
