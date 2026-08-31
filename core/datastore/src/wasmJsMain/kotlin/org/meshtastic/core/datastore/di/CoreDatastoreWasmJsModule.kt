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
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

/**
 * Koin module providing wasmJs [org.meshtastic.core.datastore.store.Store]-backed proto stores, one per payload type,
 * backed by the browser's `localStorage`. Mirrors `CoreDatastoreAndroidModule`/desktopApp's
 * `desktopProtoDataStoreModule` — one singleton store per proto payload, minus the `DataStoreScope`/on-disk-path
 * plumbing (`localStorage` access is synchronous, no scope or file path needed).
 *
 * `CorePreferencesDataStore` has no counterpart here: see its own KDoc in nonWebMain for why (the `Preferences` type
 * itself has no wasmJs variant), which is also why `RecentAddressesDataSource`/`BootloaderWarningDataSource`/
 * `FirmwareRecoveryDataSource` have no web binding this pass.
 *
 * Not yet registered anywhere: like `CorePrefsWasmJsModule`/core:database's `SingleDatabaseProvider`, nothing on wasmJs
 * composes a Koin graph yet (no `webApp` module exists in this repo pass).
 */
@Module
class CoreDatastoreWasmJsModule {
    @Single
    fun provideChannelSetDataStore(): CoreChannelSetDataStore = LocalStorageStore(
        storageKey = "channel_set_ds",
        defaultValue = ChannelSet(),
        decode = ChannelSet.ADAPTER::decode,
        encode = { value, sink -> ChannelSet.ADAPTER.encode(sink, value) },
    )
        .asCoreChannelSetDataStore()

    @Single
    fun provideLocalConfigDataStore(): CoreLocalConfigDataStore = LocalStorageStore(
        storageKey = "local_config_ds",
        defaultValue = LocalConfig(),
        decode = LocalConfig.ADAPTER::decode,
        encode = { value, sink -> LocalConfig.ADAPTER.encode(sink, value) },
    )
        .asCoreLocalConfigDataStore()

    @Single
    fun provideLocalStatsDataStore(): CoreLocalStatsDataStore = LocalStorageStore(
        storageKey = "local_stats_ds",
        defaultValue = LocalStats(),
        decode = LocalStats.ADAPTER::decode,
        encode = { value, sink -> LocalStats.ADAPTER.encode(sink, value) },
    )
        .asCoreLocalStatsDataStore()

    @Single
    fun provideModuleConfigDataStore(): CoreModuleConfigDataStore = LocalStorageStore(
        storageKey = "module_config_ds",
        defaultValue = LocalModuleConfig(),
        decode = LocalModuleConfig.ADAPTER::decode,
        encode = { value, sink -> LocalModuleConfig.ADAPTER.encode(sink, value) },
    )
        .asCoreModuleConfigDataStore()
}
