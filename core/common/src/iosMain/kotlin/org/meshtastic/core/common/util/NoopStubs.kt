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

actual class CommonUri(
    actual val host: String?,
    actual val fragment: String?,
    actual val pathSegments: List<String>
) {
    actual fun getQueryParameter(key: String): String? = null
    actual fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean = defaultValue
    actual override fun toString(): String = ""
    actual companion object {
        actual fun parse(uriString: String): CommonUri = CommonUri(null, null, emptyList())
    }
}

actual fun CommonUri.toPlatformUri(): Any = Any()

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

actual fun String?.isValidAddress(): Boolean = false

actual interface CommonParcelable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class CommonParcelize actual constructor()

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
actual annotation class CommonIgnoredOnParcel actual constructor()

actual interface CommonParceler<T> {
    actual fun create(parcel: CommonParcel): T
    actual fun T.write(parcel: CommonParcel, flags: Int)
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
actual annotation class CommonTypeParceler<T, P : CommonParceler<in T>> actual constructor()

actual class CommonParcel {
    actual fun readString(): String? = null
    actual fun readInt(): Int = 0
    actual fun readLong(): Long = 0L
    actual fun readFloat(): Float = 0.0f
    actual fun createByteArray(): ByteArray? = null
    actual fun writeByteArray(b: ByteArray?) {}
}
