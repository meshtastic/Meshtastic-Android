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
package org.meshtastic.core.database

import androidx.room3.RoomDatabase
import okio.FileSystem
import okio.Path

/** Returns a [RoomDatabase.Builder] configured for the current platform with the given [dbName]. */
expect fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase>

/** Returns a [RoomDatabase.Builder] configured for an in-memory database on the current platform. */
expect fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase>

/** Returns the platform-specific directory where database files are stored. */
expect fun getDatabaseDirectory(): Path

/** Deletes the database with the given [dbName] and its associated files (e.g., -wal, -shm). */
expect fun deleteDatabase(dbName: String)

/** Returns the [FileSystem] to use for database file operations. */
expect fun getFileSystem(): FileSystem

// createDatabaseDataStore's expect lives in nonWebMain's DatabaseDataStoreBuilder.kt, not here: DataStore<Preferences>
// has no wasmJs variant (androidx.datastore:datastore-preferences publishes no wasmJs/js target at all), and Kotlin
// only requires an expect to sit in an ancestor of every source set providing an actual — nonWebMain already is one
// for android/jvm/iOS, so this is a source-location move only. wasmJs, not being a descendant of nonWebMain, never
// sees this expect and calls nothing that would need it.
