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

import android.icu.math.BigDecimal
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.BidiFormatter
import android.text.TextUtils
import android.util.LayoutDirection
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import java.util.Locale

/** MeasureFormat is immutable and thread-safe, so one instance per locale and digit count serves every row. */
private val measureFormatCache = atomic(mapOf<String, MeasureFormat>())

/**
 * The rendering `Formatter.formatFileSize` does, ICU's short measure format wrapped for bidi in right-to-left locales,
 * but on [Locale.getDefault], the app locale. Formatter reads the Context's resources instead, which on API 26 to 32
 * can still carry the system locale after an in-app language is chosen. Under a plain host test's stub `android.jar`
 * ICU throws or returns null, and either lands the caller on its fallback.
 */
internal actual fun formatByteSizeLocalized(value: Double, unit: ByteUnit, fractionDigits: Int): String? = runCatching {
    val locale = Locale.getDefault()
    val formatted: String? =
        when (unit) {
            // CLDR spells this unit out ("0 byte"); Formatter prints the symbol, and so does this.
            ByteUnit.BYTE -> "${numberFormat(locale, fractionDigits).format(value)} ${unit.symbol}"

            else -> measureFormat(locale, fractionDigits).format(Measure(value, unit.toMeasureUnit()))
        }
    formatted?.takeIf { it.isNotEmpty() }?.let { bidiWrap(locale, it) }
}
    .getOrNull()

private fun bidiWrap(locale: Locale, text: String): String? =
    if (TextUtils.getLayoutDirectionFromLocale(locale) == LayoutDirection.RTL) {
        BidiFormatter.getInstance(true).unicodeWrap(text)
    } else {
        text
    }

private fun measureFormat(locale: Locale, fractionDigits: Int): MeasureFormat {
    val key = "${locale.toLanguageTag()}|$fractionDigits"
    measureFormatCache.value[key]?.let {
        return it
    }
    val built = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, numberFormat(locale, fractionDigits))
    measureFormatCache.update { it + (key to built) }
    return built
}

private fun numberFormat(locale: Locale, fractionDigits: Int): NumberFormat = NumberFormat.getInstance(locale).apply {
    minimumFractionDigits = fractionDigits
    maximumFractionDigits = fractionDigits
    roundingMode = BigDecimal.ROUND_HALF_UP
}

private fun ByteUnit.toMeasureUnit(): MeasureUnit = when (this) {
    ByteUnit.BYTE -> MeasureUnit.BYTE
    ByteUnit.KILOBYTE -> MeasureUnit.KILOBYTE
    ByteUnit.MEGABYTE -> MeasureUnit.MEGABYTE
    ByteUnit.GIGABYTE -> MeasureUnit.GIGABYTE
    ByteUnit.TERABYTE -> MeasureUnit.TERABYTE
}
