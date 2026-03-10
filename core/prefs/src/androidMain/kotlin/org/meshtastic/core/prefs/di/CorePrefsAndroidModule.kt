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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Suppress("TooManyFunctions")
@Module
class CorePrefsAndroidModule {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Single
    @Named("AnalyticsDataStore")
    fun provideAnalyticsDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "analytics-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("analytics_ds") },
    )

    @Single
    @Named("HomoglyphEncodingDataStore")
    fun provideHomoglyphEncodingDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "homoglyph-encoding-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("homoglyph_encoding_ds") },
    )

    @Single
    @Named("AppDataStore")
    fun provideAppDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("app_ds") },
    )

    @Single
    @Named("CustomEmojiDataStore")
    fun provideCustomEmojiDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "org.geeksville.emoji.prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("custom_emoji_ds") },
    )

    @Single
    @Named("MapDataStore")
    fun provideMapDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "map_prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("map_ds") },
    )

    @Single
    @Named("MapConsentDataStore")
    fun provideMapConsentDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "map_consent_preferences")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("map_consent_ds") },
    )

    @Single
    @Named("MapTileProviderDataStore")
    fun provideMapTileProviderDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "map_tile_provider_prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("map_tile_provider_ds") },
    )

    @Single
    @Named("MeshDataStore")
    fun provideMeshDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "mesh-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("mesh_ds") },
    )

    @Single
    @Named("RadioDataStore")
    fun provideRadioDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "radio-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("radio_ds") },
    )

    @Single
    @Named("UiDataStore")
    fun provideUiDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "ui-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("ui_ds") },
    )

    @Single
    @Named("MeshLogDataStore")
    fun provideMeshLogDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "meshlog-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("meshlog_ds") },
    )

    @Single
    @Named("FilterDataStore")
    fun provideFilterDataStore(context: Context): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "filter-prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("filter_ds") },
    )
}
