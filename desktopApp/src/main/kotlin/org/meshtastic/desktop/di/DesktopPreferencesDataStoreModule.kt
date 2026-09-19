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
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.datastore.di.CorePreferencesDataStore
import org.meshtastic.core.datastore.di.DataStoreScope
import org.meshtastic.core.datastore.di.asCorePreferencesDataStore
import org.meshtastic.core.prefs.di.AnalyticsDataStore
import org.meshtastic.core.prefs.di.AppDataStore
import org.meshtastic.core.prefs.di.CustomEmojiDataStore
import org.meshtastic.core.prefs.di.FilterDataStore
import org.meshtastic.core.prefs.di.HomoglyphEncodingDataStore
import org.meshtastic.core.prefs.di.MapConsentDataStore
import org.meshtastic.core.prefs.di.MapDataStore
import org.meshtastic.core.prefs.di.MapTileProviderDataStore
import org.meshtastic.core.prefs.di.MeshDataStore
import org.meshtastic.core.prefs.di.MeshLogDataStore
import org.meshtastic.core.prefs.di.RadioDataStore
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.prefs.di.asAnalyticsDataStore
import org.meshtastic.core.prefs.di.asAppDataStore
import org.meshtastic.core.prefs.di.asCustomEmojiDataStore
import org.meshtastic.core.prefs.di.asFilterDataStore
import org.meshtastic.core.prefs.di.asHomoglyphEncodingDataStore
import org.meshtastic.core.prefs.di.asMapConsentDataStore
import org.meshtastic.core.prefs.di.asMapDataStore
import org.meshtastic.core.prefs.di.asMapTileProviderDataStore
import org.meshtastic.core.prefs.di.asMeshDataStore
import org.meshtastic.core.prefs.di.asMeshLogDataStore
import org.meshtastic.core.prefs.di.asRadioDataStore
import org.meshtastic.core.prefs.di.asUiDataStore

/** Typed preference-datastore singletons for each preference domain, one file each under the data directory. */
@Suppress("TooManyFunctions")
@Module
class DesktopPreferencesDataStoreModule {

    @Single
    fun analyticsDataStore(scope: DataStoreScope): AnalyticsDataStore =
        prefsStore("analytics", scope).asAnalyticsDataStore()

    @Single
    fun homoglyphEncodingDataStore(scope: DataStoreScope): HomoglyphEncodingDataStore =
        prefsStore("homoglyph_encoding", scope).asHomoglyphEncodingDataStore()

    @Single fun appDataStore(scope: DataStoreScope): AppDataStore = prefsStore("app", scope).asAppDataStore()

    @Single
    fun customEmojiDataStore(scope: DataStoreScope): CustomEmojiDataStore =
        prefsStore("custom_emoji", scope).asCustomEmojiDataStore()

    @Single fun mapDataStore(scope: DataStoreScope): MapDataStore = prefsStore("map", scope).asMapDataStore()

    @Single
    fun mapConsentDataStore(scope: DataStoreScope): MapConsentDataStore =
        prefsStore("map_consent", scope).asMapConsentDataStore()

    @Single
    fun mapTileProviderDataStore(scope: DataStoreScope): MapTileProviderDataStore =
        prefsStore("map_tile_provider", scope).asMapTileProviderDataStore()

    @Single fun meshDataStore(scope: DataStoreScope): MeshDataStore = prefsStore("mesh", scope).asMeshDataStore()

    @Single fun radioDataStore(scope: DataStoreScope): RadioDataStore = prefsStore("radio", scope).asRadioDataStore()

    @Single fun uiDataStore(scope: DataStoreScope): UiDataStore = prefsStore("ui", scope).asUiDataStore()

    @Single
    fun meshLogDataStore(scope: DataStoreScope): MeshLogDataStore = prefsStore("meshlog", scope).asMeshLogDataStore()

    @Single
    fun filterDataStore(scope: DataStoreScope): FilterDataStore = prefsStore("filter", scope).asFilterDataStore()

    @Single
    fun corePreferencesDataStore(scope: DataStoreScope): CorePreferencesDataStore =
        prefsStore("core_preferences", scope).asCorePreferencesDataStore()
}

/** [name] is an on-disk identity — changing it orphans existing user data. */
private fun prefsStore(name: String, scope: DataStoreScope): DataStore<Preferences> {
    val dir = desktopDataDir() + "/datastore"
    FileSystem.SYSTEM.createDirectories(dir.toPath())
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        scope = scope,
        produceFile = { "$dir/$name.preferences_pb".toPath() },
    )
}
