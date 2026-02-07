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
package org.meshtastic.core.prefs.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import org.meshtastic.core.prefs.analytics.AnalyticsPrefsImpl
import org.meshtastic.core.prefs.emoji.CustomEmojiPrefs
import org.meshtastic.core.prefs.emoji.CustomEmojiPrefsImpl
import org.meshtastic.core.prefs.filter.FilterPrefs
import org.meshtastic.core.prefs.filter.FilterPrefsImpl
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefsImpl
import org.meshtastic.core.prefs.map.MapConsentPrefs
import org.meshtastic.core.prefs.map.MapConsentPrefsImpl
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.prefs.map.MapPrefsImpl
import org.meshtastic.core.prefs.map.MapTileProviderPrefs
import org.meshtastic.core.prefs.map.MapTileProviderPrefsImpl
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.prefs.mesh.MeshPrefsImpl
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.core.prefs.meshlog.MeshLogPrefsImpl
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.RadioPrefsImpl
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.prefs.ui.UiPrefsImpl
import javax.inject.Qualifier
import javax.inject.Singleton

// These pref store qualifiers are internal to prevent prefs stores from being injected directly.
// Consuming code should always inject one of the prefs repositories.

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AnalyticsSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class HomoglyphEncodingSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AppSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CustomEmojiSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapConsentSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MapTileProviderSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MeshSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RadioSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class UiSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MeshLogSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class FilterSharedPreferences

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

    @Binds fun bindUiPrefs(uiPrefsImpl: UiPrefsImpl): UiPrefs

    @Binds fun bindFilterPrefs(filterPrefsImpl: FilterPrefsImpl): FilterPrefs

    companion object {

        @Provides
        @Singleton
        @AnalyticsSharedPreferences
        fun provideAnalyticsSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("analytics-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @HomoglyphEncodingSharedPreferences
        fun provideHomoglyphEncodingSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("homoglyph-encoding-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @AppSharedPreferences
        fun provideAppSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @CustomEmojiSharedPreferences
        fun provideCustomEmojiSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("org.geeksville.emoji.prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @MapSharedPreferences
        fun provideMapSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @MapConsentSharedPreferences
        fun provideMapConsentSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("map_consent_preferences", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @MapTileProviderSharedPreferences
        fun provideMapTileProviderSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("map_tile_provider_prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @MeshSharedPreferences
        fun provideMeshSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("mesh-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @RadioSharedPreferences
        fun provideRadioSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("radio-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @UiSharedPreferences
        fun provideUiSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @MeshLogSharedPreferences
        fun provideMeshLogSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("meshlog-prefs", Context.MODE_PRIVATE)

        @Provides
        @Singleton
        @FilterSharedPreferences
        fun provideFilterSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(FilterPrefs.FILTER_PREFS_NAME, Context.MODE_PRIVATE)
    }
}
