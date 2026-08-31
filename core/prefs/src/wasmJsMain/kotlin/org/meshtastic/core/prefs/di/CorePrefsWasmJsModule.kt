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

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.prefs.store.LocalStoragePrefsStore

/**
 * Koin module providing wasmJs [org.meshtastic.core.prefs.store.PrefsStore] instances for each preference domain,
 * backed by the browser's `localStorage`. Mirrors `CorePrefsAndroidModule`/desktopApp's `DesktopPlatformModule` — one
 * singleton store per domain, same `_ds`-suffixed names — minus the `SharedPreferencesMigration` (nothing to migrate
 * from on web) and the `CoroutineScope`/dispatcher plumbing (localStorage reads and writes are synchronous).
 *
 * Not yet registered anywhere: like core:database's `SingleDatabaseProvider`, nothing on wasmJs composes a Koin graph
 * yet (no `webApp` module exists in this repo pass) — a future one wires this in explicitly, the same way
 * `CorePrefsAndroidModule` is listed by name in androidApp's `AppKoinModule` despite already sitting inside
 * `CorePrefsModule`'s `@ComponentScan("org.meshtastic.core.prefs")`.
 */
@Suppress("TooManyFunctions")
@Module
class CorePrefsWasmJsModule {
    @Single
    fun provideAnalyticsDataStore(): AnalyticsDataStore = LocalStoragePrefsStore("analytics_ds").asAnalyticsDataStore()

    @Single
    fun provideHomoglyphEncodingDataStore(): HomoglyphEncodingDataStore =
        LocalStoragePrefsStore("homoglyph_encoding_ds").asHomoglyphEncodingDataStore()

    @Single fun provideAppDataStore(): AppDataStore = LocalStoragePrefsStore("app_ds").asAppDataStore()

    @Single
    fun provideCustomEmojiDataStore(): CustomEmojiDataStore =
        LocalStoragePrefsStore("custom_emoji_ds").asCustomEmojiDataStore()

    @Single fun provideMapDataStore(): MapDataStore = LocalStoragePrefsStore("map_ds").asMapDataStore()

    @Single
    fun provideMapConsentDataStore(): MapConsentDataStore =
        LocalStoragePrefsStore("map_consent_ds").asMapConsentDataStore()

    @Single
    fun provideMapTileProviderDataStore(): MapTileProviderDataStore =
        LocalStoragePrefsStore("map_tile_provider_ds").asMapTileProviderDataStore()

    @Single fun provideMeshDataStore(): MeshDataStore = LocalStoragePrefsStore("mesh_ds").asMeshDataStore()

    @Single fun provideRadioDataStore(): RadioDataStore = LocalStoragePrefsStore("radio_ds").asRadioDataStore()

    @Single fun provideUiDataStore(): UiDataStore = LocalStoragePrefsStore("ui_ds").asUiDataStore()

    @Single fun provideMeshLogDataStore(): MeshLogDataStore = LocalStoragePrefsStore("meshlog_ds").asMeshLogDataStore()

    @Single fun provideFilterDataStore(): FilterDataStore = LocalStoragePrefsStore("filter_ds").asFilterDataStore()
}
