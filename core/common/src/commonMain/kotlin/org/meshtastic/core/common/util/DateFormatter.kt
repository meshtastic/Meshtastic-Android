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

/** Platform-agnostic Date formatter utility. */
expect object DateFormatter {
    /** Formats a timestamp into a relative "time ago" string. */
    fun formatRelativeTime(timestampMillis: Long): String

    /** Formats a timestamp into a localized date and time string. */
    fun formatDateTime(timestampMillis: Long): String

    /**
     * Formats a timestamp into a short date or time string.
     *
     * Typically shows time if within the last 24 hours, otherwise the date.
     */
    fun formatShortDate(timestampMillis: Long): String

    /** Formats a timestamp into a localized time string (HH:mm). */
    fun formatTime(timestampMillis: Long): String

    /** Formats a timestamp into a localized time string with seconds (HH:mm:ss). */
    fun formatTimeWithSeconds(timestampMillis: Long): String

    /** Formats a timestamp into a localized date string. */
    fun formatDate(timestampMillis: Long): String

    /** Formats a timestamp into a localized short date and medium time string. */
    fun formatDateTimeShort(timestampMillis: Long): String
}
