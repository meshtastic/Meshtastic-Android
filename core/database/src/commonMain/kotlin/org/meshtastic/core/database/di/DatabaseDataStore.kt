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
package org.meshtastic.core.database.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Store holding which device database is currently active. A type rather than a Koin qualifier so the compiler tells it
 * apart from every other `DataStore<Preferences>`.
 */
interface DatabaseDataStore : DataStore<Preferences>

/** Presents an existing store as [DatabaseDataStore]; the wrapper adds nothing but identity. */
fun DataStore<Preferences>.asDatabaseDataStore(): DatabaseDataStore =
    object : DatabaseDataStore, DataStore<Preferences> by this {}
