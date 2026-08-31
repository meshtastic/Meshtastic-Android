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

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.datastore.store.Store
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

// One type per store, so the compiler distinguishes them instead of a Koin string qualifier. Each is a transparent
// `Store` of its payload — inject and use it exactly like one. `CorePreferencesDataStore` (DataStore<Preferences>)
// is not here: `androidx.datastore.preferences`'s `Preferences` type has no wasmJs variant, so it can't be ported
// behind `Store<T>` at all — see nonWebMain's `CorePreferencesDataStore.kt`.

/**
 * Application-lifetime scope shared by every real `DataStore` (android/jvm/iOS only). Per the DataStore docs this must
 * not be cancelled by UI lifecycle events: `DataStore` has no `close()`, so its in-memory cache is released only when
 * this job ends. wasmJs's `LocalStorageStore` needs no scope — `localStorage` access is synchronous.
 */
interface DataStoreScope : CoroutineScope

/** Presents an existing scope as [DataStoreScope]; the wrapper adds nothing but identity. */
fun CoroutineScope.asDataStoreScope(): DataStoreScope = object : DataStoreScope, CoroutineScope by this {}

interface CoreChannelSetDataStore : Store<ChannelSet>

/** Presents an existing store as [CoreChannelSetDataStore]; the wrapper adds nothing but identity. */
fun Store<ChannelSet>.asCoreChannelSetDataStore(): CoreChannelSetDataStore =
    object : CoreChannelSetDataStore, Store<ChannelSet> by this {}

interface CoreLocalConfigDataStore : Store<LocalConfig>

/** Presents an existing store as [CoreLocalConfigDataStore]; the wrapper adds nothing but identity. */
fun Store<LocalConfig>.asCoreLocalConfigDataStore(): CoreLocalConfigDataStore =
    object : CoreLocalConfigDataStore, Store<LocalConfig> by this {}

interface CoreLocalStatsDataStore : Store<LocalStats>

/** Presents an existing store as [CoreLocalStatsDataStore]; the wrapper adds nothing but identity. */
fun Store<LocalStats>.asCoreLocalStatsDataStore(): CoreLocalStatsDataStore =
    object : CoreLocalStatsDataStore, Store<LocalStats> by this {}

interface CoreModuleConfigDataStore : Store<LocalModuleConfig>

/** Presents an existing store as [CoreModuleConfigDataStore]; the wrapper adds nothing but identity. */
fun Store<LocalModuleConfig>.asCoreModuleConfigDataStore(): CoreModuleConfigDataStore =
    object : CoreModuleConfigDataStore, Store<LocalModuleConfig> by this {}
