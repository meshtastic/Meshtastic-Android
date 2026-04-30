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

import java.net.InetAddress
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

actual object BuildUtils {
    actual val isEmulator: Boolean = false

    actual val sdkInt: Int = 0
}

actual object DateFormatter {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val mediumTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    private val shortDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM)

    actual fun formatRelativeTime(timestampMillis: Long): String {
        val deltaMillis = nowMillis - timestampMillis
        val absDeltaMillis = abs(deltaMillis)
        val suffix = if (deltaMillis >= 0) "ago" else "from now"

        return when {
            absDeltaMillis < MINUTE_MILLIS -> if (deltaMillis >= 0) "just now" else "in a moment"
            absDeltaMillis < HOUR_MILLIS -> "${absDeltaMillis / MINUTE_MILLIS}m $suffix"
            absDeltaMillis < DAY_MILLIS -> "${absDeltaMillis / HOUR_MILLIS}h $suffix"
            else -> "${absDeltaMillis / DAY_MILLIS}d $suffix"
        }
    }

    actual fun formatDateTime(timestampMillis: Long): String =
        shortDateTimeFormatter.format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId))

    actual fun formatShortDate(timestampMillis: Long): String {
        val isWithin24Hours = (nowMillis - timestampMillis) <= DAY_MILLIS
        val zonedDateTime = java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId)
        return if (isWithin24Hours) {
            shortTimeFormatter.format(zonedDateTime)
        } else {
            shortDateFormatter.format(zonedDateTime)
        }
    }

    actual fun formatTime(timestampMillis: Long): String =
        shortTimeFormatter.format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId))

    actual fun formatTimeWithSeconds(timestampMillis: Long): String =
        mediumTimeFormatter.format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId))

    actual fun formatDate(timestampMillis: Long): String =
        shortDateFormatter.format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId))

    actual fun formatDateTimeShort(timestampMillis: Long): String =
        shortDateTimeFormatter.format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(zoneId))
}

@Suppress("MagicNumber")
actual fun getSystemMeasurementSystem(): MeasurementSystem =
    when (Locale.getDefault().country.uppercase(Locale.getDefault())) {
        "US",
        "LR",
        "MM",
        "GB",
        -> MeasurementSystem.IMPERIAL

        else -> MeasurementSystem.METRIC
    }

actual fun String?.isValidAddress(): Boolean {
    val value = this?.trim()
    return when {
        value.isNullOrEmpty() -> false
        value == LOCALHOST -> true
        IPV4_PATTERN.matches(value) -> value.split('.').all { segment -> segment.toIntOrNull() in 0..MAX_IPV4_SEGMENT }
        value.contains(':') -> runCatching { InetAddress.getByName(value) }.isSuccess
        else -> DOMAIN_PATTERN.matches(value)
    }
}

private val IPV4_PATTERN = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}${'$'}")
private val DOMAIN_PATTERN = Regex("^(?=.{1,253}${'$'})(?:(?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,63}${'$'}")

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L
private const val MAX_IPV4_SEGMENT = 255
private const val LOCALHOST = "localhost"
