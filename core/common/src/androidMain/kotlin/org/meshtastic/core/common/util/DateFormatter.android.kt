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

import android.text.format.DateUtils
import org.meshtastic.core.common.ContextServices
import java.text.DateFormat

actual object DateFormatter {
    actual fun formatRelativeTime(timestampMillis: Long): String = DateUtils.getRelativeTimeSpanString(
        timestampMillis,
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    )
        .toString()

    actual fun formatDateTime(timestampMillis: Long): String = DateUtils.formatDateTime(
        ContextServices.app,
        timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    )

    actual fun formatShortDate(timestampMillis: Long): String {
        val now = nowMillis
        val isWithin24Hours = (now - timestampMillis) <= DateUtils.DAY_IN_MILLIS

        return if (isWithin24Hours) {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(timestampMillis)
        } else {
            DateFormat.getDateInstance(DateFormat.SHORT).format(timestampMillis)
        }
    }

    actual fun formatTime(timestampMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(timestampMillis)

    actual fun formatTimeWithSeconds(timestampMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(timestampMillis)

    actual fun formatDate(timestampMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT).format(timestampMillis)

    actual fun formatDateTimeShort(timestampMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(timestampMillis)
}
