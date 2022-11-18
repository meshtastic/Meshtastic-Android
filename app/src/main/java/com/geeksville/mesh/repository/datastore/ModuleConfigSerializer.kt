package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for the [LocalModuleConfig] object defined in localonly.proto.
 */
@Suppress("BlockingMethodInNonBlockingContext")
object ModuleConfigSerializer : Serializer<LocalModuleConfig> {
    override val defaultValue: LocalModuleConfig = LocalModuleConfig.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LocalModuleConfig {
        try {
            return LocalModuleConfig.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: LocalModuleConfig, output: OutputStream) = t.writeTo(output)
}
