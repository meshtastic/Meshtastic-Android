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

import kotlinx.browser.localStorage
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.store.InMemoryStore
import org.meshtastic.core.datastore.store.LocalStorageStore
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

internal const val LOCAL_CONFIG_STORAGE_KEY = "local_config_ds"
internal const val MODULE_CONFIG_STORAGE_KEY = "module_config_ds"

/**
 * Proto stores for the browser. The radio's config holds its keys, Wi-Fi password and MQTT login, and the radio sends
 * it again on every connect, so those stores live in memory for the page session. Radio stats are not sensitive and
 * stay in `localStorage`.
 */
@Module
class CoreDatastoreWasmJsModule {
    @Single
    fun provideLocalConfigDataStore(): CoreLocalConfigDataStore {
        removeStoredCopy(LOCAL_CONFIG_STORAGE_KEY)
        return InMemoryStore(LocalConfig.Builder().build()).asCoreLocalConfigDataStore()
    }

    @Single
    fun provideLocalStatsDataStore(): CoreLocalStatsDataStore = LocalStorageStore(
        storageKey = "local_stats_ds",
        defaultValue = LocalStats.Builder().build(),
        decode = LocalStats.ADAPTER::decode,
        encode = { value, sink -> LocalStats.ADAPTER.encode(sink, value) },
    )
        .asCoreLocalStatsDataStore()

    @Single
    fun provideModuleConfigDataStore(): CoreModuleConfigDataStore {
        removeStoredCopy(MODULE_CONFIG_STORAGE_KEY)
        return InMemoryStore(LocalModuleConfig.Builder().build()).asCoreModuleConfigDataStore()
    }
}

/** Drops a copy that an earlier version wrote; storage that is off, as in some private windows, has nothing to drop. */
private fun removeStoredCopy(key: String) {
    runCatching { localStorage.removeItem(key) }
}
