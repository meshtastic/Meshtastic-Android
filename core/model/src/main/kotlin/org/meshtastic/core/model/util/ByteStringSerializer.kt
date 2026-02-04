/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Serializer for Okio [ByteString] using kotlinx.serialization */
object ByteStringSerializer : KSerializer<ByteString> {
    private val byteArraySerializer = ByteArraySerializer()

    override val descriptor: SerialDescriptor = byteArraySerializer.descriptor

    override fun serialize(encoder: Encoder, value: ByteString) {
        byteArraySerializer.serialize(encoder, value.toByteArray())
    }

    override fun deserialize(decoder: Decoder): ByteString = byteArraySerializer.deserialize(decoder).toByteString()
}

/** Parceler for Okio [ByteString] for Android Parcelable support */
object ByteStringParceler : Parceler<ByteString?> {
    override fun create(parcel: Parcel): ByteString? = parcel.createByteArray()?.toByteString()

    override fun ByteString?.write(parcel: Parcel, flags: Int) {
        parcel.writeByteArray(this?.toByteArray())
    }
}
