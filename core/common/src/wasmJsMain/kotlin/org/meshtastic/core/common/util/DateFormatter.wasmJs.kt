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
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.core.common.util

import kotlin.math.abs

/**
 * `Intl.DateTimeFormat`'s `dateStyle`/`timeStyle` map directly onto the JVM desktop actual's `FormatStyle.SHORT` /
 * `FormatStyle.MEDIUM` (`"short"`/`"medium"` respectively), so the per-function style mapping here mirrors
 * `JvmPlatformUtils.kt`'s `DateFormatter` object function-for-function rather than Android's more elaborate
 * `DateUtils`-based one.
 */
actual object DateFormatter {

    actual fun formatRelativeTime(timestampMillis: Long): String {
        val deltaMillis = nowMillis - timestampMillis
        val absDeltaMillis = abs(deltaMillis)
        // Intl.RelativeTimeFormat.format(value, unit): a negative value means the past ("x ago"); positive means
        // the future ("in x"). deltaMillis >= 0 means timestampMillis is in the past.
        val sign = if (deltaMillis >= 0) -1.0 else 1.0
        val formatter = relativeTimeFormatter(browserLanguage())

        return when {
            absDeltaMillis < MINUTE_MILLIS -> formatter.format(0.0, "second")
            absDeltaMillis < HOUR_MILLIS -> formatter.format(sign * (absDeltaMillis / MINUTE_MILLIS), "minute")
            absDeltaMillis < DAY_MILLIS -> formatter.format(sign * (absDeltaMillis / HOUR_MILLIS), "hour")
            else -> formatter.format(sign * (absDeltaMillis / DAY_MILLIS), "day")
        }
    }

    actual fun formatDateTime(timestampMillis: Long): String =
        dateTimeFormatter(browserLanguage(), "short", "medium").format(timestampMillis.toDouble())

    actual fun formatShortDate(timestampMillis: Long): String {
        val isWithin24Hours = (nowMillis - timestampMillis) <= DAY_MILLIS
        val locale = browserLanguage()
        return if (isWithin24Hours) {
            timeOnlyFormatter(locale, "short").format(timestampMillis.toDouble())
        } else {
            dateOnlyFormatter(locale, "short").format(timestampMillis.toDouble())
        }
    }

    actual fun formatTime(timestampMillis: Long): String =
        timeOnlyFormatter(browserLanguage(), "short").format(timestampMillis.toDouble())

    actual fun formatTimeWithSeconds(timestampMillis: Long): String =
        timeOnlyFormatter(browserLanguage(), "medium").format(timestampMillis.toDouble())

    actual fun formatDate(timestampMillis: Long): String =
        dateOnlyFormatter(browserLanguage(), "short").format(timestampMillis.toDouble())

    actual fun formatDateTimeShort(timestampMillis: Long): String =
        dateTimeFormatter(browserLanguage(), "short", "medium").format(timestampMillis.toDouble())
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L
