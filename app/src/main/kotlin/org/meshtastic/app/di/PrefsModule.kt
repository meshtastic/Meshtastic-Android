/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.prefs.analytics.AnalyticsPrefsImpl
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
import org.meshtastic.core.prefs.di.NodeDisplayNameDataStore
import org.meshtastic.core.prefs.di.RadioDataStore
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.prefs.emoji.CustomEmojiPrefsImpl
import org.meshtastic.core.prefs.filter.FilterPrefsImpl
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefsImpl
import org.meshtastic.core.prefs.map.MapConsentPrefsImpl
import org.meshtastic.core.prefs.map.MapPrefsImpl
import org.meshtastic.core.prefs.map.MapTileProviderPrefsImpl
import org.meshtastic.core.prefs.mesh.MeshPrefsImpl
import org.meshtastic.core.prefs.meshlog.MeshLogPrefsImpl
import org.meshtastic.core.prefs.nodedisplay.NodeDisplayNamePrefsImpl
import org.meshtastic.core.prefs.radio.RadioPrefsImpl
import org.meshtastic.core.prefs.ui.UiPrefsImpl
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.MapTileProviderPrefs
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.NodeDisplayNamePrefs
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.UiPrefs
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AnalyticsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class HomoglyphEncodingDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AppDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CustomEmojiDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapConsentDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapTileProviderDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MeshDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RadioDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class UiDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MeshLogDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class FilterDataStore

@Suppress("TooManyFunctions")
@InstallIn(SingletonComponent::class)
@Module
interface PrefsModule {

    @Binds fun bindAnalyticsPrefs(analyticsPrefsImpl: AnalyticsPrefsImpl): AnalyticsPrefs

    @Binds fun bindHomoglyphEncodingPrefs(homoglyphEncodingPrefsImpl: HomoglyphPrefsImpl): HomoglyphPrefs

    @Binds fun bindCustomEmojiPrefs(customEmojiPrefsImpl: CustomEmojiPrefsImpl): CustomEmojiPrefs

    @Binds fun bindMapConsentPrefs(mapConsentPrefsImpl: MapConsentPrefsImpl): MapConsentPrefs

    @Binds fun bindMapPrefs(mapPrefsImpl: MapPrefsImpl): MapPrefs

    @Binds fun bindMapTileProviderPrefs(mapTileProviderPrefsImpl: MapTileProviderPrefsImpl): MapTileProviderPrefs

    @Binds fun bindMeshPrefs(meshPrefsImpl: MeshPrefsImpl): MeshPrefs

    @Binds fun bindMeshLogPrefs(meshLogPrefsImpl: MeshLogPrefsImpl): MeshLogPrefs

    @Binds fun bindRadioPrefs(radioPrefsImpl: RadioPrefsImpl): RadioPrefs

    @Binds fun bindNodeDisplayNamePrefs(impl: NodeDisplayNamePrefsImpl): NodeDisplayNamePrefs

    @Binds fun bindUiPrefs(uiPrefsImpl: UiPrefsImpl): UiPrefs

    @Binds fun bindFilterPrefs(filterPrefsImpl: FilterPrefsImpl): FilterPrefs

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @Provides
        @Singleton
        @AnalyticsDataStore
        fun provideAnalyticsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "analytics-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("analytics_ds") },
            )

        @Provides
        @Singleton
        @HomoglyphEncodingDataStore
        fun provideHomoglyphEncodingDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "homoglyph-encoding-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("homoglyph_encoding_ds") },
            )

        @Provides
        @Singleton
        @AppDataStore
        fun provideAppDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("app_ds") },
            )

        @Provides
        @Singleton
        @CustomEmojiDataStore
        fun provideCustomEmojiDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "org.geeksville.emoji.prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("custom_emoji_ds") },
            )

        @Provides
        @Singleton
        @MapDataStore
        fun provideMapDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "map_prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("map_ds") },
            )

        @Provides
        @Singleton
        @MapConsentDataStore
        fun provideMapConsentDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "map_consent_preferences")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("map_consent_ds") },
            )

        @Provides
        @Singleton
        @MapTileProviderDataStore
        fun provideMapTileProviderDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "map_tile_provider_prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("map_tile_provider_ds") },
            )

        @Provides
        @Singleton
        @MeshDataStore
        fun provideMeshDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "mesh-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("mesh_ds") },
            )

        @Provides
        @Singleton
        @RadioDataStore
        fun provideRadioDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "radio-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("radio_ds") },
            )

        @Provides
        @Singleton
        @UiDataStore
        fun provideUiDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "ui-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("ui_ds") },
            )

        @Provides
        @Singleton
        @MeshLogDataStore
        fun provideMeshLogDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "meshlog-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("meshlog_ds") },
            )

        @Provides
        @Singleton
        @FilterDataStore
        fun provideFilterDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "filter-prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("filter_ds") },
            )

        @Provides
        @Singleton
        @NodeDisplayNameDataStore
        fun provideNodeDisplayNameDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, "node_display_names_prefs")),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile("node_display_names_ds") },
            )
    }
}
