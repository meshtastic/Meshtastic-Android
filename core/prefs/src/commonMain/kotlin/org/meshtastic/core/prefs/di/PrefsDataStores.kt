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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

// One type per preference domain, so the compiler distinguishes them instead of a Koin string qualifier. Each is a
// transparent `DataStore<Preferences>` — inject and use it exactly like one.

interface AnalyticsDataStore : DataStore<Preferences>

/** Presents an existing store as [AnalyticsDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asAnalyticsDataStore(): AnalyticsDataStore =
    object : AnalyticsDataStore, DataStore<Preferences> by this {}

interface AppDataStore : DataStore<Preferences>

/** Presents an existing store as [AppDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asAppDataStore(): AppDataStore = object : AppDataStore, DataStore<Preferences> by this {}

interface CustomEmojiDataStore : DataStore<Preferences>

/** Presents an existing store as [CustomEmojiDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asCustomEmojiDataStore(): CustomEmojiDataStore =
    object : CustomEmojiDataStore, DataStore<Preferences> by this {}

interface FilterDataStore : DataStore<Preferences>

/** Presents an existing store as [FilterDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asFilterDataStore(): FilterDataStore =
    object : FilterDataStore, DataStore<Preferences> by this {}

interface HomoglyphEncodingDataStore : DataStore<Preferences>

/** Presents an existing store as [HomoglyphEncodingDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asHomoglyphEncodingDataStore(): HomoglyphEncodingDataStore =
    object : HomoglyphEncodingDataStore, DataStore<Preferences> by this {}

interface MapConsentDataStore : DataStore<Preferences>

/** Presents an existing store as [MapConsentDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asMapConsentDataStore(): MapConsentDataStore =
    object : MapConsentDataStore, DataStore<Preferences> by this {}

interface MapDataStore : DataStore<Preferences>

/** Presents an existing store as [MapDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asMapDataStore(): MapDataStore = object : MapDataStore, DataStore<Preferences> by this {}

interface MapTileProviderDataStore : DataStore<Preferences>

/** Presents an existing store as [MapTileProviderDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asMapTileProviderDataStore(): MapTileProviderDataStore =
    object : MapTileProviderDataStore, DataStore<Preferences> by this {}

interface MeshDataStore : DataStore<Preferences>

/** Presents an existing store as [MeshDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asMeshDataStore(): MeshDataStore = object : MeshDataStore, DataStore<Preferences> by this {}

interface MeshLogDataStore : DataStore<Preferences>

/** Presents an existing store as [MeshLogDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asMeshLogDataStore(): MeshLogDataStore =
    object : MeshLogDataStore, DataStore<Preferences> by this {}

interface RadioDataStore : DataStore<Preferences>

/** Presents an existing store as [RadioDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asRadioDataStore(): RadioDataStore =
    object : RadioDataStore, DataStore<Preferences> by this {}

interface UiDataStore : DataStore<Preferences>

/** Presents an existing store as [UiDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asUiDataStore(): UiDataStore = object : UiDataStore, DataStore<Preferences> by this {}
