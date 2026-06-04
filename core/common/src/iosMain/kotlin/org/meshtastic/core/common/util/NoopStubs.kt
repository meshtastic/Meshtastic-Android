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

/** No-op stubs for iOS target in core:common. */
actual object BuildUtils {
    actual val isEmulator: Boolean = false
    actual val sdkInt: Int = 0
}

actual object DateFormatter {
    actual fun formatRelativeTime(timestampMillis: Long): String = ""

    actual fun formatDateTime(timestampMillis: Long): String = ""

    actual fun formatShortDate(timestampMillis: Long): String = ""

    actual fun formatTime(timestampMillis: Long): String = ""

    actual fun formatTimeWithSeconds(timestampMillis: Long): String = ""

    actual fun formatDate(timestampMillis: Long): String = ""

    actual fun formatDateTimeShort(timestampMillis: Long): String = ""
}

actual fun getSystemMeasurementSystem(): MeasurementSystem = MeasurementSystem.METRIC

actual fun currentLocaleCode(): String = "en"

actual fun currentRegionCode(): String = ""

actual fun currentLocaleQualifier(): String = "en"

actual fun String?.isValidAddress(): Boolean = false
