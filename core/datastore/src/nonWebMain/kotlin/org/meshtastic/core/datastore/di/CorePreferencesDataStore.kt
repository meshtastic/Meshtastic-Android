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
package org.meshtastic.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Typed against `DataStore<Preferences>`, whose payload type has no wasmJs variant, so it cannot sit behind `Store`.
 */
interface CorePreferencesDataStore : DataStore<Preferences>

/** Presents an existing store as [CorePreferencesDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asCorePreferencesDataStore(): CorePreferencesDataStore =
    object : CorePreferencesDataStore, DataStore<Preferences> by this {}
