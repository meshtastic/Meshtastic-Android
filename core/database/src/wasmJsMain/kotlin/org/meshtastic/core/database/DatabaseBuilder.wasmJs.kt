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

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon

/** Persists to OPFS through the `opfs-sahpool` VFS, by way of the tab's one sqlite-wasm worker. */
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.databaseBuilder<MeshtasticDatabase>(
        name = "$dbName.db",
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(webSqliteDriver())

/** The worker opens `:memory:` as a plain in-memory database, outside the pool. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(factory = { MeshtasticDatabaseConstructor.initialize() })
        .configureCommon()
        .setDriver(webSqliteDriver())

/** The pool keeps flat absolute names; the root stands in for a directory. */
actual fun getDatabaseDirectory(): Path = "/".toPath()

/** The name the worker gives [dbName] inside the pool. */
actual fun getDatabaseFile(dbName: String): Path = "/$dbName.db".toPath()

/**
 * Deletes [dbName] and its journal from the pool once the worker gets to it; the worker refuses while the database is
 * open. The signature is synchronous, so a failure is only logged.
 */
actual fun deleteDatabase(dbName: String) = SqliteWasmWorker.deleteFileDetached(getDatabaseFile(dbName).toString())

/** Okio's FileSystem is synchronous, and the pool answers only asynchronously from its worker. */
actual fun getFileSystem(): FileSystem = error("getFileSystem() is not supported on wasmJs")

// No busy_timeout wrapper: the worker allows one connection per database, so nothing ever contends for its lock.
private fun webSqliteDriver(): SQLiteDriver = SqliteWasmWorker.driver
