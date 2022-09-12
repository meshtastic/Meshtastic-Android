package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for the [ChannelSet] object defined in apponly.proto.
 */
@Suppress("BlockingMethodInNonBlockingContext")
object ChannelSetSerializer : Serializer<ChannelSet> {
    override val defaultValue: ChannelSet = ChannelSet.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ChannelSet {
        try {
            return ChannelSet.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: ChannelSet, output: OutputStream) = t.writeTo(output)
}
