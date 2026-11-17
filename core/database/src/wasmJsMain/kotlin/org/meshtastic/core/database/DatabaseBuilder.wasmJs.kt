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
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.w3c.dom.Worker

/** Persists to OPFS through a [WebWorkerSQLiteDriver] running sqlite-wasm in `worker/worker.js`. */
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.databaseBuilder<MeshtasticDatabase>(
        name = "$dbName.db",
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(BusyTimeoutSQLiteDriver(WebWorkerSQLiteDriver(sqliteWasmWorker())))

/** Every worker connection opens a real OPFS file; there is no in-memory mode to offer. */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    error("getInMemoryDatabaseBuilder() is not supported on wasmJs")

/** OPFS has no directory to report; only the nonWeb `DatabaseManager` reads this. */
actual fun getDatabaseDirectory(): Path = "/".toPath()

/** OPFS paths are private to the worker; only the nonWeb `DatabaseManager` and backup read this. */
actual fun getDatabaseFile(dbName: String): Path = "/$dbName.db".toPath()

/** Only the nonWeb `DatabaseManager` deletes databases. */
actual fun deleteDatabase(dbName: String): Unit = error("deleteDatabase() is not supported on wasmJs")

/** Okio has no OPFS [FileSystem]; only the nonWeb `DatabaseManager` reads this. */
actual fun getFileSystem(): FileSystem = error("getFileSystem() is not supported on wasmJs")

/** Starts the `sqlite-wasm-worker` npm package's worker; webpack resolves the URL. */
private fun sqliteWasmWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
