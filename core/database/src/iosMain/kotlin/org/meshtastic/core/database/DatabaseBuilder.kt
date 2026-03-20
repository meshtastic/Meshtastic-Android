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
package org.meshtastic.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Returns a [RoomDatabase.Builder] configured for iOS with the given [dbName]. */
@OptIn(ExperimentalForeignApi::class)
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> {
    val dbFilePath = documentDirectory() + "/$dbName.db"
    return Room.databaseBuilder<MeshtasticDatabase>(
        name = dbFilePath,
        factory = { MeshtasticDatabaseConstructor.initialize() }
    ).configureCommon()
}

/** Returns the iOS directory where database files are stored. */
actual fun getDatabaseDirectory(): Path = documentDirectory().toPath()

/** Deletes the database and its Room-associated files on iOS. */
actual fun deleteDatabase(dbName: String) {
    val dir = documentDirectory()
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-wal", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-shm", null)
}

/** Returns the system FileSystem for iOS. */
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

/** Creates an iOS DataStore for database preferences. */
actual fun createDatabaseDataStore(name: String): DataStore<Preferences> {
    val dir = documentDirectory() + "/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return PreferenceDataStoreFactory.create(
        produceFile = { (dir + "/$name.preferences_pb").toPath().toNioPath() }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
