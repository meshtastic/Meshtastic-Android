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

import kotlin.math.abs

/** Decimal, as every byte figure in the app is: 1 MB is 1,000,000 bytes, never 1,048,576. */
const val BYTES_PER_MEGABYTE = 1_000_000.0

/** A decimal byte unit. [symbol] is the fallback label where the platform has no localized one. */
internal enum class ByteUnit(val symbol: String) {
    BYTE("B"),
    KILOBYTE("kB"),
    MEGABYTE("MB"),
    GIGABYTE("GB"),
    TERABYTE("TB"),
}

/**
 * Formats a byte count for display with the thresholds and digits of Android's `Formatter.formatFileSize`: decimal
 * units, the next unit once the value passes 900, two fraction digits below 100 and none from 100 up. So 1,500 bytes is
 * "1.50 kB" and 16 GiB is "17.18 GB". The number follows the locale; so does the unit label where the platform can
 * localize it.
 */
fun formatByteSize(bytes: Long): String {
    var value = abs(bytes.toDouble())
    var unit = ByteUnit.BYTE
    // Stops at TB, where Formatter goes on to PB: ICU's PETABYTE needs API 30.
    while (value > PROMOTE_ABOVE && unit != ByteUnit.TERABYTE) {
        value /= BYTES_PER_KILOBYTE
        unit = ByteUnit.entries[unit.ordinal + 1]
    }
    val fractionDigits = if (unit == ByteUnit.BYTE || value >= WHOLE_FROM) 0 else FRACTION_DIGITS
    return formatInByteUnit(if (bytes < 0) -value else value, unit, fractionDigits)
}

/**
 * Formats a value already in megabytes, in megabytes whatever its size. For places that must not change unit per value,
 * such as the ticks of one chart axis.
 */
fun formatMegabytes(megabytes: Double, fractionDigits: Int): String =
    formatInByteUnit(megabytes, ByteUnit.MEGABYTE, fractionDigits)

private fun formatInByteUnit(value: Double, unit: ByteUnit, fractionDigits: Int): String {
    if (value.isNaN() || value.isInfinite()) return NumberFormatter.format(value, fractionDigits)
    return formatByteSizeLocalized(value, unit, fractionDigits)
        ?: "${formatDecimalLocalized(value, fractionDigits)} ${unit.symbol}"
}

/**
 * Renders [value] in [unit] with the platform's localized unit label, or null where the platform has none and the
 * caller renders the fixed [ByteUnit.symbol] instead.
 */
internal expect fun formatByteSizeLocalized(value: Double, unit: ByteUnit, fractionDigits: Int): String?

private const val BYTES_PER_KILOBYTE = 1000.0
private const val PROMOTE_ABOVE = 900.0
private const val WHOLE_FROM = 100.0
private const val FRACTION_DIGITS = 2
