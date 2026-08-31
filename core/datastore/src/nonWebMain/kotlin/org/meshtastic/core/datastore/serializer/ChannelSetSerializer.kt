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
package org.meshtastic.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException
import org.meshtastic.proto.ChannelSet

/** Serializer for the [ChannelSet] object defined in apponly.proto. */
object ChannelSetSerializer : OkioSerializer<ChannelSet> {
    override val defaultValue: ChannelSet = ChannelSet()

    override suspend fun readFrom(source: BufferedSource): ChannelSet {
        try {
            return ChannelSet.ADAPTER.decode(source)
        } catch (exception: IOException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: ChannelSet, sink: BufferedSink) {
        ChannelSet.ADAPTER.encode(sink, t)
    }
}
