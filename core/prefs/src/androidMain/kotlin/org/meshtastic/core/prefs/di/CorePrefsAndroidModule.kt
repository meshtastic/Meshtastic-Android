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
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

/**
 * Koin module providing Android [DataStore] instances for each preference domain.
 *
 * Each DataStore is a singleton backed by its own [CoroutineScope] using the injected
 * [CoroutineDispatchers.io] dispatcher, and includes a [SharedPreferencesMigration] to
 * migrate legacy SharedPreferences data on first access.
 */
@Suppress("TooManyFunctions")
@Module
class CorePrefsAndroidModule {

    @Single
    @Named("AnalyticsDataStore")
    fun provideAnalyticsDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "analytics-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("analytics_ds") },
        )

    @Single
    @Named("HomoglyphEncodingDataStore")
    fun provideHomoglyphEncodingDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "homoglyph-encoding-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("homoglyph_encoding_ds") },
        )

    @Single
    @Named("AppDataStore")
    fun provideAppDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("app_ds") },
        )

    @Single
    @Named("CustomEmojiDataStore")
    fun provideCustomEmojiDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "org.geeksville.emoji.prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("custom_emoji_ds") },
        )

    @Single
    @Named("MapDataStore")
    fun provideMapDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "map_prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("map_ds") },
        )

    @Single
    @Named("MapConsentDataStore")
    fun provideMapConsentDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "map_consent_preferences")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("map_consent_ds") },
        )

    @Single
    @Named("MapTileProviderDataStore")
    fun provideMapTileProviderDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "map_tile_provider_prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("map_tile_provider_ds") },
        )

    @Single
    @Named("MeshDataStore")
    fun provideMeshDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "mesh-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("mesh_ds") },
        )

    @Single
    @Named("RadioDataStore")
    fun provideRadioDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "radio-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("radio_ds") },
        )

    @Single
    @Named("UiDataStore")
    fun provideUiDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "ui-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("ui_ds") },
        )

    @Single
    @Named("MeshLogDataStore")
    fun provideMeshLogDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "meshlog-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("meshlog_ds") },
        )

    @Single
    @Named("FilterDataStore")
    fun provideFilterDataStore(context: Context, dispatchers: CoroutineDispatchers): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, "filter-prefs")),
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("filter_ds") },
        )
}
