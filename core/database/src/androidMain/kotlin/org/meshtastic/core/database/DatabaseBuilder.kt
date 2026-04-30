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

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.room3.RoomDatabase
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon

/** Returns a [RoomDatabase.Builder] configured for Android with the given [dbName]. */
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> {
    val app = ContextServices.app
    val dbFile = app.getDatabasePath(dbName)
    return Room.databaseBuilder<MeshtasticDatabase>(
        context = app.applicationContext,
        name = dbFile.absolutePath,
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
}

/** Returns a [RoomDatabase.Builder] configured for an in-memory Android database. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(
        context = ContextServices.app.applicationContext,
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()

/** Returns the Android directory where database files are stored. */
actual fun getDatabaseDirectory(): Path {
    val app = ContextServices.app
    return app.getDatabasePath("dummy.db").parentFile!!.absolutePath.toPath()
}

/** Deletes the Android database using the platform-specific deleteDatabase helper. */
actual fun deleteDatabase(dbName: String) {
    ContextServices.app.deleteDatabase(dbName)
}

/** Returns the system FileSystem for Android. */
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

/** Creates an Android DataStore for database preferences. */
actual fun createDatabaseDataStore(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
    produceFile = { ContextServices.app.preferencesDataStoreFile(name) },
)
