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

/**
 * Returns a [RoomDatabase.Builder] configured for wasmJs with the given [dbName], persisting via OPFS through a
 * [WebWorkerSQLiteDriver] talking to `worker/worker.js` (real SQLite compiled to WASM, `@sqlite.org/sqlite-wasm`) over
 * a dedicated Web Worker. Same shape as the reference proven end-to-end (insert survives a full page reload) in
 * github.com/danysantiago/room-web-demo. [BusyTimeoutSQLiteDriver] wraps it the same way every other platform's driver
 * is wrapped — `PRAGMA busy_timeout` is standard SQL, so it works the same way over the worker protocol.
 */
actual fun getDatabaseBuilder(dbName: String): RoomDatabase.Builder<MeshtasticDatabase> =
    Room.databaseBuilder<MeshtasticDatabase>(
        name = "$dbName.db",
        factory = { MeshtasticDatabaseConstructor.initialize() },
    )
        .configureCommon()
        .setDriver(BusyTimeoutSQLiteDriver(WebWorkerSQLiteDriver(sqliteWasmWorker())))

/**
 * No wasmJs caller — [SingleDatabaseProvider] only opens the one named, persistent database — and no honest
 * implementation exists: every [WebWorkerSQLiteDriver] connection opens a real OPFS file (there is no true in-memory
 * mode over the worker protocol), so silently backing an "in-memory" builder with a named OPFS file would be wrong
 * behavior hiding behind a name that promises otherwise. Fails loudly instead, matching
 * [getDatabaseDirectory]/[deleteDatabase]/[getFileSystem] below.
 */
actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<MeshtasticDatabase> =
    error("getInMemoryDatabaseBuilder() is not supported on wasmJs")

/**
 * OPFS has no directory concept the way a real filesystem does, and [getDatabaseDirectory] has no wasmJs caller: its
 * only consumer, `DatabaseManager` (legacy-DB cleanup, LRU eviction), lives in `nonWebMain` and is out of scope for
 * this platform's single, non-switching [SingleDatabaseProvider]. Best-effort placeholder, not load-bearing.
 */
actual fun getDatabaseDirectory(): Path = "/".toPath()

/**
 * No wasmJs caller: [deleteDatabase]'s only consumer, `DatabaseManager`, lives in `nonWebMain`. Deleting an OPFS-backed
 * database for real would mean messaging the worker to `sqlite3_close`+remove the file it created via
 * `sqlite3.oo1.OpfsDb`, or calling `navigator.storage.getDirectory()` / `removeEntry` directly — deferred until
 * something on web actually needs to delete a database.
 */
actual fun deleteDatabase(dbName: String): Unit = error("deleteDatabase() is not supported on wasmJs")

/**
 * No wasmJs caller: [getFileSystem]'s only consumer, `DatabaseManager`, lives in `nonWebMain`, and there is no
 * browser-native [FileSystem] implementation for OPFS in this Okio version. Fails loudly rather than silently returning
 * a filesystem that can't see OPFS-backed files, matching this codebase's existing "fail loudly, don't silently return
 * wrong data" precedent (see `core:resources`' wasmJs `getString()` actual).
 */
actual fun getFileSystem(): FileSystem = error("getFileSystem() is not supported on wasmJs")

/**
 * Spins up the `sqlite-wasm-worker` npm dependency's `worker.js` as a dedicated Web Worker — same invocation as the
 * proven-working reference (`new Worker(new URL(...))`, no options; Kotlin's webpack-based bundling handles the worker
 * script's own `import` statement). `kotlinx-browser` supplies [Worker] here — Kotlin/Wasm's own stdlib has no
 * `org.w3c.dom` bindings in this Kotlin version, unlike the reference project (older Kotlin, built-in bindings), which
 * is why this module depends on `kotlinx-browser` (see `build.gradle.kts`) the same way `core:ble` already does for its
 * own DOM/JS interop.
 */
private fun sqliteWasmWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
