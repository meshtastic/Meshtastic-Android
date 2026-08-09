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
package org.meshtastic.core.prefs.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

/**
 * Koin module providing Android [DataStore] instances for each preference domain.
 *
 * Each DataStore is a singleton backed by its own [CoroutineScope] using the injected [CoroutineDispatchers.io]
 * dispatcher, and includes a [SharedPreferencesMigration] to migrate legacy SharedPreferences data on first access.
 */
@Suppress("TooManyFunctions")
@Module
class CorePrefsAndroidModule {

    @Single
    fun provideAnalyticsDataStore(context: Context, dispatchers: CoroutineDispatchers): AnalyticsDataStore =
        store(context, dispatchers, legacyName = "analytics-prefs", fileName = "analytics_ds").asAnalyticsDataStore()

    @Single
    fun provideHomoglyphEncodingDataStore(
        context: Context,
        dispatchers: CoroutineDispatchers,
    ): HomoglyphEncodingDataStore =
        store(context, dispatchers, legacyName = "homoglyph-encoding-prefs", fileName = "homoglyph_encoding_ds")
            .asHomoglyphEncodingDataStore()

    @Single
    fun provideAppDataStore(context: Context, dispatchers: CoroutineDispatchers): AppDataStore =
        store(context, dispatchers, legacyName = "prefs", fileName = "app_ds").asAppDataStore()

    @Single
    fun provideCustomEmojiDataStore(context: Context, dispatchers: CoroutineDispatchers): CustomEmojiDataStore =
        store(context, dispatchers, legacyName = "org.geeksville.emoji.prefs", fileName = "custom_emoji_ds")
            .asCustomEmojiDataStore()

    @Single
    fun provideMapDataStore(context: Context, dispatchers: CoroutineDispatchers): MapDataStore =
        store(context, dispatchers, legacyName = "map_prefs", fileName = "map_ds").asMapDataStore()

    @Single
    fun provideMapConsentDataStore(context: Context, dispatchers: CoroutineDispatchers): MapConsentDataStore =
        store(context, dispatchers, legacyName = "map_consent_preferences", fileName = "map_consent_ds")
            .asMapConsentDataStore()

    @Single
    fun provideMapTileProviderDataStore(context: Context, dispatchers: CoroutineDispatchers): MapTileProviderDataStore =
        store(context, dispatchers, legacyName = "map_tile_provider_prefs", fileName = "map_tile_provider_ds")
            .asMapTileProviderDataStore()

    @Single
    fun provideMeshDataStore(context: Context, dispatchers: CoroutineDispatchers): MeshDataStore =
        store(context, dispatchers, legacyName = "mesh-prefs", fileName = "mesh_ds").asMeshDataStore()

    @Single
    fun provideRadioDataStore(context: Context, dispatchers: CoroutineDispatchers): RadioDataStore =
        store(context, dispatchers, legacyName = "radio-prefs", fileName = "radio_ds").asRadioDataStore()

    @Single
    fun provideUiDataStore(context: Context, dispatchers: CoroutineDispatchers): UiDataStore =
        store(context, dispatchers, legacyName = "ui-prefs", fileName = "ui_ds").asUiDataStore()

    @Single
    fun provideMeshLogDataStore(context: Context, dispatchers: CoroutineDispatchers): MeshLogDataStore =
        store(context, dispatchers, legacyName = "meshlog-prefs", fileName = "meshlog_ds").asMeshLogDataStore()

    @Single
    fun provideFilterDataStore(context: Context, dispatchers: CoroutineDispatchers): FilterDataStore =
        store(context, dispatchers, legacyName = "filter-prefs", fileName = "filter_ds").asFilterDataStore()
}

/**
 * [legacyName] is the SharedPreferences file this domain migrates from, [fileName] the DataStore file it lives in now.
 * Both are on-disk identities — changing either orphans existing user data.
 */
private fun store(
    context: Context,
    dispatchers: CoroutineDispatchers,
    legacyName: String,
    fileName: String,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    migrations = listOf(SharedPreferencesMigration(context, legacyName)),
    scope = CoroutineScope(dispatchers.io + SupervisorJob()),
    produceFile = { context.preferencesDataStoreFile(fileName) },
)
