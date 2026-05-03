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

/** Platform-agnostic Parcelable interface. */
expect interface CommonParcelable

/** Platform-agnostic Parcelize annotation. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class CommonParcelize()

/** Platform-agnostic IgnoredOnParcel annotation. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
expect annotation class CommonIgnoredOnParcel()

/** Platform-agnostic Parceler interface. */
expect interface CommonParceler<T> {
    fun create(parcel: CommonParcel): T

    fun T.write(parcel: CommonParcel, flags: Int)
}

/** Platform-agnostic TypeParceler annotation. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
expect annotation class CommonTypeParceler<T, P : CommonParceler<in T>>()

/** Platform-agnostic Parcel representation for manual parceling (e.g. AIDL support). */
expect class CommonParcel {
    fun readString(): String?

    fun readInt(): Int

    fun readLong(): Long

    fun readFloat(): Float

    fun createByteArray(): ByteArray?

    fun writeByteArray(b: ByteArray?)
}
