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
package org.meshtastic.core.datastore.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.store.LocalStorageStore
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/** Proto stores backed by the browser's `localStorage`, one per payload type. */
@Module
class CoreDatastoreWasmJsModule {
    @Single
    fun provideLocalConfigDataStore(): CoreLocalConfigDataStore = LocalStorageStore(
        storageKey = "local_config_ds",
        defaultValue = LocalConfig.Builder().build(),
        decode = LocalConfig.ADAPTER::decode,
        encode = { value, sink -> LocalConfig.ADAPTER.encode(sink, value) },
    )
        .asCoreLocalConfigDataStore()

    @Single
    fun provideLocalStatsDataStore(): CoreLocalStatsDataStore = LocalStorageStore(
        storageKey = "local_stats_ds",
        defaultValue = LocalStats.Builder().build(),
        decode = LocalStats.ADAPTER::decode,
        encode = { value, sink -> LocalStats.ADAPTER.encode(sink, value) },
    )
        .asCoreLocalStatsDataStore()

    @Single
    fun provideModuleConfigDataStore(): CoreModuleConfigDataStore = LocalStorageStore(
        storageKey = "module_config_ds",
        defaultValue = LocalModuleConfig.Builder().build(),
        decode = LocalModuleConfig.ADAPTER::decode,
        encode = { value, sink -> LocalModuleConfig.ADAPTER.encode(sink, value) },
    )
        .asCoreModuleConfigDataStore()
}
