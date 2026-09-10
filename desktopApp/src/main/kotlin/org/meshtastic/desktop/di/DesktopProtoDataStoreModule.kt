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
package org.meshtastic.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.datastore.di.CoreChannelSetDataStore
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.CoreLocalStatsDataStore
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.core.datastore.di.DataStoreScope
import org.meshtastic.core.datastore.di.asCoreChannelSetDataStore
import org.meshtastic.core.datastore.di.asCoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.asCoreLocalStatsDataStore
import org.meshtastic.core.datastore.di.asCoreModuleConfigDataStore
import org.meshtastic.core.datastore.serializer.ChannelSetSerializer
import org.meshtastic.core.datastore.serializer.LocalConfigSerializer
import org.meshtastic.core.datastore.serializer.LocalStatsSerializer
import org.meshtastic.core.datastore.serializer.ModuleConfigSerializer
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/** Proto [DataStore] instances (OkioStorage-backed). */
@Module
class DesktopProtoDataStoreModule {

    @Single
    fun localConfigDataStore(scope: DataStoreScope): CoreLocalConfigDataStore =
        protoStore(LocalConfigSerializer, "local_config.pb", { LocalConfig() }, scope).asCoreLocalConfigDataStore()

    @Single
    fun moduleConfigDataStore(scope: DataStoreScope): CoreModuleConfigDataStore =
        protoStore(ModuleConfigSerializer, "module_config.pb", { LocalModuleConfig() }, scope)
            .asCoreModuleConfigDataStore()

    @Single
    fun channelSetDataStore(scope: DataStoreScope): CoreChannelSetDataStore =
        protoStore(ChannelSetSerializer, "channel_set.pb", { ChannelSet() }, scope).asCoreChannelSetDataStore()

    @Single
    fun localStatsDataStore(scope: DataStoreScope): CoreLocalStatsDataStore =
        protoStore(LocalStatsSerializer, "local_stats.pb", { LocalStats() }, scope).asCoreLocalStatsDataStore()
}

/** [fileName] is an on-disk identity — changing it orphans existing user data. */
private fun <T> protoStore(
    serializer: OkioSerializer<T>,
    fileName: String,
    produceNewData: () -> T,
    scope: DataStoreScope,
): DataStore<T> {
    val dir = desktopDataDir() + "/datastore"
    FileSystem.SYSTEM.createDirectories(dir.toPath())
    return DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = serializer,
            producePath = { "$dir/$fileName".toPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { produceNewData() }),
        scope = scope,
    )
}
