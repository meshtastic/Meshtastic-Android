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
package org.meshtastic.app.map.prefs.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Google Maps preferences, google flavor only. A type rather than a Koin qualifier so the compiler tells it apart from
 * every other `DataStore<Preferences>`.
 */
interface GoogleMapsDataStore : DataStore<Preferences>

/** Presents an existing store as [GoogleMapsDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asGoogleMapsDataStore(): GoogleMapsDataStore =
    object : GoogleMapsDataStore, DataStore<Preferences> by this {}
