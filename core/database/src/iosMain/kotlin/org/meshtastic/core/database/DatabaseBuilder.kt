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
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import okio.BufferedSink
import okio.BufferedSource
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
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(BundledSQLiteDriver())
}

/** Returns a [RoomDatabase.Builder] configured for an in-memory iOS database. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(factory = { MeshtasticDatabaseConstructor.initialize() })
        .configureCommon()
        .setDriver(BundledSQLiteDriver())

/** Returns the iOS directory where database files are stored. */
actual fun getDatabaseDirectory(): Path = documentDirectory().toPath()

/** Deletes the database and its Room-associated files on iOS. */
@OptIn(ExperimentalForeignApi::class)
actual fun deleteDatabase(dbName: String) {
    val dir = documentDirectory()
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-wal", null)
    NSFileManager.defaultManager.removeItemAtPath(dir + "/$dbName.db-shm", null)
}

/** Returns the system FileSystem for iOS. */
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

private object PreferencesSerializer : OkioSerializer<Preferences> {
    override val defaultValue: Preferences
        get() = emptyPreferences()

    override suspend fun readFrom(source: BufferedSource): Preferences {
        // iOS stub: return an empty Preferences instance instead of crashing.
        return emptyPreferences()
    }

    override suspend fun writeTo(t: Preferences, sink: BufferedSink) {
        // iOS stub: no-op to avoid crashing on write.
    }
}

/** Creates an iOS DataStore for database preferences. */
@OptIn(ExperimentalForeignApi::class)
actual fun createDatabaseDataStore(name: String): DataStore<Preferences> {
    val dir = documentDirectory() + "/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return DataStoreFactory.create(
        storage =
        OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            producePath = { (dir + "/$name.preferences_pb").toPath() },
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(documentDirectory?.path)
}
