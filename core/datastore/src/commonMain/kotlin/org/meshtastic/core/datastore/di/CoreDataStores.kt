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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats

// One type per store, so the compiler distinguishes them instead of a Koin string qualifier. Each is a transparent
// `DataStore` of its payload — inject and use it exactly like one.

/**
 * Application-lifetime scope shared by every [DataStore]. Per the DataStore docs this must not be cancelled by UI
 * lifecycle events: `DataStore` has no `close()`, so its in-memory cache is released only when this job ends.
 */
interface DataStoreScope : CoroutineScope

/** Presents an existing scope as [DataStoreScope]; the wrapper adds nothing but identity. */
fun CoroutineScope.asDataStoreScope(): DataStoreScope = object : DataStoreScope, CoroutineScope by this {}

interface CorePreferencesDataStore : DataStore<Preferences>

/** Presents an existing store as [CorePreferencesDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asCorePreferencesDataStore(): CorePreferencesDataStore =
    object : CorePreferencesDataStore, DataStore<Preferences> by this {}

interface CoreChannelSetDataStore : DataStore<ChannelSet>

/** Presents an existing store as [CoreChannelSetDataStore]; the wrapper adds nothing but identity. */
fun DataStore<ChannelSet>.asCoreChannelSetDataStore(): CoreChannelSetDataStore =
    object : CoreChannelSetDataStore, DataStore<ChannelSet> by this {}

interface CoreLocalConfigDataStore : DataStore<LocalConfig>

/** Presents an existing store as [CoreLocalConfigDataStore]; the wrapper adds nothing but identity. */
fun DataStore<LocalConfig>.asCoreLocalConfigDataStore(): CoreLocalConfigDataStore =
    object : CoreLocalConfigDataStore, DataStore<LocalConfig> by this {}

interface CoreLocalStatsDataStore : DataStore<LocalStats>

/** Presents an existing store as [CoreLocalStatsDataStore]; the wrapper adds nothing but identity. */
fun DataStore<LocalStats>.asCoreLocalStatsDataStore(): CoreLocalStatsDataStore =
    object : CoreLocalStatsDataStore, DataStore<LocalStats> by this {}

interface CoreModuleConfigDataStore : DataStore<LocalModuleConfig>

/** Presents an existing store as [CoreModuleConfigDataStore]; the wrapper adds nothing but identity. */
fun DataStore<LocalModuleConfig>.asCoreModuleConfigDataStore(): CoreModuleConfigDataStore =
    object : CoreModuleConfigDataStore, DataStore<LocalModuleConfig> by this {}
