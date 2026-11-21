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
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import java.io.File

/**
 * Resolves the desktop data directory for persistent storage (DataStore files, Room database). Defaults to
 * `~/.meshtastic/`. Override via `MESHTASTIC_DATA_DIR` environment variable.
 *
 * Shared between `core:database` and `desktop` module to ensure all persistent data is co-located.
 */
fun desktopDataDir(): String = resolvedDataDir

/**
 * The default `~/.meshtastic/` is restricted to the current user the first time it is resolved, including when it
 * already exists. An explicit `MESHTASTIC_DATA_DIR` is left as the user set it; the app restricts its own
 * subdirectories and files inside it.
 */
private val resolvedDataDir: String by lazy {
    prepareDataDir(System.getenv("MESHTASTIC_DATA_DIR"), System.getProperty("user.home"))
}

internal fun prepareDataDir(override: String?, home: String): String {
    if (!override.isNullOrBlank()) return override
    return "$home/.meshtastic".also { createOwnerOnlyDirectories(File(it)) }
}

/** The database file for [dbName] in [dataDir], created owner-only. A data directory that exists is left as it is. */
internal fun prepareDatabaseFile(dataDir: String, dbName: String): File {
    val dbFile = File(dataDir, "$dbName.db")
    dbFile.parentFile?.let(::createMissingOwnerOnlyDirectories)
    // SQLite gives the -wal and -shm files the main file's permissions.
    ensureOwnerOnlyFile(dbFile)
    return dbFile
}

/** Returns a [RoomDatabase.Builder] configured for JVM/Desktop with the given [dbName]. */
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> {
    val dbFile = prepareDatabaseFile(desktopDataDir(), dbName)
    return Room.databaseBuilder<MeshtasticDatabase>(
        name = dbFile.absolutePath,
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(BusyTimeoutSQLiteDriver(BundledSQLiteDriver()))
}

/** Returns a [RoomDatabase.Builder] configured for an in-memory JVM database. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(factory = { MeshtasticDatabaseConstructor.initialize() })
        .configureCommon()
        .setDriver(BusyTimeoutSQLiteDriver(BundledSQLiteDriver()))

/** Returns the JVM/Desktop directory where database files are stored. */
actual fun getDatabaseDirectory(): Path = desktopDataDir().toPath()

/** Returns the JVM/Desktop path of the main database file for [dbName]. */
actual fun getDatabaseFile(dbName: String): Path = File(desktopDataDir(), "$dbName.db").absolutePath.toPath()

/** Deletes the database and its Room-associated files on JVM. */
actual fun deleteDatabase(dbName: String) {
    val dir = desktopDataDir()
    File(dir, "$dbName.db").delete()
    File(dir, "$dbName.db-wal").delete()
    File(dir, "$dbName.db-shm").delete()
}

/** Returns the system FileSystem for JVM. */
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM

/** Creates a JVM DataStore for database preferences in the data directory. */
actual fun createDatabaseDataStore(name: String): DataStore<Preferences> {
    val dir = desktopDataDir() + "/datastore"
    createOwnerOnlyDirectories(File(dir))
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
        produceFile = { File(dir, "$name.preferences_pb") },
    )
}
