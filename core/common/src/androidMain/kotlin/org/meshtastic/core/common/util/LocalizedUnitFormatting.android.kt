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

import android.icu.number.LocalizedNumberFormatter
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.icu.util.MeasureUnit
import android.os.Build
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import java.util.Locale

/**
 * Formatters are immutable and reusable but not free to build; the working set is tiny (locale × system × usage), so a
 * map keyed by all three amortizes construction across every node card on a scrolling list. Copy-on-write over an
 * atomic reference: a race can build the same formatter twice, and either result is equivalent.
 */
private val formatterCache = atomic(mapOf<String, LocalizedNumberFormatter>())

private inline fun cachedFormatter(key: String, build: () -> LocalizedNumberFormatter): LocalizedNumberFormatter {
    formatterCache.value[key]?.let {
        return it
    }
    val built = build()
    formatterCache.update { it + (key to built) }
    return built
}

/**
 * ICU units this engine renders. `android.icu` classes are touched only behind the SDK guard and inside `runCatching` —
 * under a plain host-test JVM the stub `android.jar` leaves ICU statics null and its methods throwing, and either must
 * land the caller on the fallback, never crash.
 */
private enum class IcuUnit {
    METER,
    FOOT,
    METER_PER_SECOND,
    MILLIMETER,
    ;

    fun toMeasureUnit(): MeasureUnit = when (this) {
        METER -> MeasureUnit.METER
        FOOT -> MeasureUnit.FOOT
        METER_PER_SECOND -> MeasureUnit.METER_PER_SECOND
        MILLIMETER -> MeasureUnit.MILLIMETER
    }
}

actual fun formatLengthLocalized(meters: Double, system: MeasurementSystem): String? =
    formatWithUsage(meters, system, IcuUnit.METER, usage = "default")

actual fun formatElevationLocalized(meters: Double, system: MeasurementSystem): String? {
    // Precision.integer() is API 33 alongside usage(); below, the fallback is the engine.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !meters.isFinite()) return null

    @Suppress("MagicNumber")
    val value = if (system == MeasurementSystem.METRIC) meters else meters * 3.28084
    val unit = if (system == MeasurementSystem.METRIC) IcuUnit.METER else IcuUnit.FOOT

    return runCatching {
        val locale = unitsLocale(system)
        val formatter =
            cachedFormatter("${locale.toLanguageTag()}|elevation|$unit") {
                NumberFormatter.with().unit(unit.toMeasureUnit()).precision(Precision.integer()).locale(locale)
            }
        formatter.format(value).toString()
    }
        .getOrNull()
}

actual fun formatSpeedLocalized(metersPerSecond: Double, system: MeasurementSystem): String? =
    formatWithUsage(metersPerSecond, system, IcuUnit.METER_PER_SECOND, usage = "default")

actual fun formatRainfallLocalized(millimeters: Double, system: MeasurementSystem): String? =
    formatWithUsage(millimeters, system, IcuUnit.MILLIMETER, usage = "rainfall")

private fun formatWithUsage(value: Double, system: MeasurementSystem, unit: IcuUnit, usage: String): String? {
    // usage() is API 33; below that the caller's hand-rolled fallback is the engine.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !value.isFinite()) return null

    return runCatching {
        val locale = unitsLocale(system)
        val formatter =
            cachedFormatter("${locale.toLanguageTag()}|$usage|$unit") {
                NumberFormatter.with().usage(usage).unit(unit.toMeasureUnit()).locale(locale)
            }
        formatter.format(value).toString()
    }
        .getOrNull()
}

/**
 * The formatting locale: the device region fills a region-less app language (see [getSystemMeasurementSystem]), and the
 * resolved [system] rides the `ms` Unicode keyword so CLDR's unit choice always agrees with the provider — including
 * the user's in-app override.
 */
private fun unitsLocale(system: MeasurementSystem): Locale {
    val base = Locale.getDefault().withSystemRegionIfMissing()
    val keyword =
        when (system) {
            MeasurementSystem.METRIC -> "metric"
            MeasurementSystem.IMPERIAL -> "ussystem"
        }
    return Locale.Builder().setLocale(base).setUnicodeLocaleKeyword(MEASUREMENT_SYSTEM_EXTENSION, keyword).build()
}
