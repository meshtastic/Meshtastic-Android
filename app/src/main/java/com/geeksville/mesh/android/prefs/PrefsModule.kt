/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.android.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

// These pref store qualifiers are private to prevent prefs stores from being injected directly.
// Consuming code should always inject one of the prefs repositories.

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AnalyticsSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AppSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class CustomEmojiSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class MapConsentSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class MapTileProviderSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class MeshSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class RadioSharedPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class UiSharedPreferences

@Suppress("TooManyFunctions")
@InstallIn(SingletonComponent::class)
@Module
object PrefsModule {

    @Provides
    @Singleton
    @AnalyticsSharedPreferences
    fun provideAnalyticsSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("analytics-prefs", Context.MODE_PRIVATE)

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
    fun provideAnalyticsPrefs(
        @AnalyticsSharedPreferences analyticsPreferences: SharedPreferences,
        @AppSharedPreferences appPreferences: SharedPreferences,
    ): AnalyticsPrefs = AnalyticsPrefsImpl(analyticsPreferences, appPreferences)

    @Provides
    @Singleton
    fun provideCustomEmojiPrefs(@CustomEmojiSharedPreferences sharedPreferences: SharedPreferences): CustomEmojiPrefs =
        CustomEmojiPrefsImpl(sharedPreferences)

    @Provides
    @Singleton
    fun provideMapConsentPrefs(@MapConsentSharedPreferences sharedPreferences: SharedPreferences): MapConsentPrefs =
        MapConsentPrefsImpl(sharedPreferences)

    @Provides
    @Singleton
    fun provideMapTileProviderPrefs(
        @MapTileProviderSharedPreferences sharedPreferences: SharedPreferences,
    ): MapTileProviderPrefs = MapTileProviderPrefsImpl(sharedPreferences)

    @Provides
    @Singleton
    fun provideMeshPrefs(@MeshSharedPreferences sharedPreferences: SharedPreferences): MeshPrefs =
        MeshPrefsImpl(sharedPreferences)

    @Provides
    @Singleton
    fun provideRadioPrefs(@RadioSharedPreferences sharedPreferences: SharedPreferences): RadioPrefs =
        RadioPrefsImpl(sharedPreferences)

    @Provides
    @Singleton
    fun provideUiPrefs(@UiSharedPreferences sharedPreferences: SharedPreferences): UiPrefs =
        UiPrefsImpl(sharedPreferences)
}
