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

actual interface CommonParcelable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class CommonParcelize

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
actual annotation class CommonIgnoredOnParcel

actual interface CommonParceler<T> {
    actual fun create(parcel: CommonParcel): T

    actual fun T.write(parcel: CommonParcel, flags: Int)
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
actual annotation class CommonTypeParceler<T, P : CommonParceler<in T>>

actual class CommonParcel {
    actual fun readString(): String? = unsupportedParcelOperation()

    actual fun readInt(): Int = unsupportedParcelOperation()

    actual fun readLong(): Long = unsupportedParcelOperation()

    actual fun readFloat(): Float = unsupportedParcelOperation()

    actual fun createByteArray(): ByteArray? = unsupportedParcelOperation()

    actual fun writeByteArray(b: ByteArray?) = unsupportedParcelOperation<Unit>()
}

private fun <T> unsupportedParcelOperation(): T =
    error("CommonParcel is unavailable on JVM smoke targets. Manual parcel operations remain Android-only.")
