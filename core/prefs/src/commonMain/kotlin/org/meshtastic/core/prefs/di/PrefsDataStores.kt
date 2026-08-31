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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.prefs.di

import org.meshtastic.core.prefs.store.PrefsStore

// One type per preference domain, so the compiler distinguishes them instead of a Koin string qualifier. Each is a
// transparent PrefsStore — inject and use it exactly like one. PrefsStore itself (not `DataStore<Preferences>`
// directly — androidx.datastore.preferences has no wasmJs variant) is what makes these usable from every target:
// nonWebMain's DataStorePrefsStore adapts a real DataStore<Preferences>, wasmJsMain's LocalStoragePrefsStore is
// backed by localStorage. See core/prefs/store/PrefsStore.kt.

interface AnalyticsDataStore : PrefsStore

/** Presents an existing store as [AnalyticsDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asAnalyticsDataStore(): AnalyticsDataStore = object : AnalyticsDataStore, PrefsStore by this {}

interface AppDataStore : PrefsStore

/** Presents an existing store as [AppDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asAppDataStore(): AppDataStore = object : AppDataStore, PrefsStore by this {}

interface CustomEmojiDataStore : PrefsStore

/** Presents an existing store as [CustomEmojiDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asCustomEmojiDataStore(): CustomEmojiDataStore = object : CustomEmojiDataStore, PrefsStore by this {}

interface FilterDataStore : PrefsStore

/** Presents an existing store as [FilterDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asFilterDataStore(): FilterDataStore = object : FilterDataStore, PrefsStore by this {}

interface HomoglyphEncodingDataStore : PrefsStore

/** Presents an existing store as [HomoglyphEncodingDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asHomoglyphEncodingDataStore(): HomoglyphEncodingDataStore =
    object : HomoglyphEncodingDataStore, PrefsStore by this {}

interface MapConsentDataStore : PrefsStore

/** Presents an existing store as [MapConsentDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asMapConsentDataStore(): MapConsentDataStore = object : MapConsentDataStore, PrefsStore by this {}

interface MapDataStore : PrefsStore

/** Presents an existing store as [MapDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asMapDataStore(): MapDataStore = object : MapDataStore, PrefsStore by this {}

interface MapTileProviderDataStore : PrefsStore

/** Presents an existing store as [MapTileProviderDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asMapTileProviderDataStore(): MapTileProviderDataStore =
    object : MapTileProviderDataStore, PrefsStore by this {}

interface MeshDataStore : PrefsStore

/** Presents an existing store as [MeshDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asMeshDataStore(): MeshDataStore = object : MeshDataStore, PrefsStore by this {}

interface MeshLogDataStore : PrefsStore

/** Presents an existing store as [MeshLogDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asMeshLogDataStore(): MeshLogDataStore = object : MeshLogDataStore, PrefsStore by this {}

interface RadioDataStore : PrefsStore

/** Presents an existing store as [RadioDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asRadioDataStore(): RadioDataStore = object : RadioDataStore, PrefsStore by this {}

interface UiDataStore : PrefsStore

/** Presents an existing store as [UiDataStore]; the wrapper adds nothing but identity. */
fun PrefsStore.asUiDataStore(): UiDataStore = object : UiDataStore, PrefsStore by this {}
