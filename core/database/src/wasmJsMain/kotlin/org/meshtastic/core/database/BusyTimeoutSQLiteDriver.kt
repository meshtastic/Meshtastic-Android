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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL

/**
 * wasmJs counterpart of `nonWebMain`'s `BusyTimeoutSQLiteDriver.kt` — same name, same wrapping behavior, differing only
 * in that `override fun open` must be `suspend` here: `androidx.sqlite.SQLiteDriver.open()` is declared `suspend` on
 * androidx.sqlite's own `webMain` branch (`webMain/androidx/sqlite/SQLiteDriver.web.kt` in
 * `androidx.sqlite:sqlite:2.7.0`'s sources), to support async drivers like `WebWorkerSQLiteDriver`. Not an
 * `expect`/`actual` pair — the suspend modifier difference means a single implementation can't satisfy both branches —
 * so this is an independent declaration visible only to the wasmJs compilation, exactly like `core:ble`'s
 * `BleServiceExtensions.kt` documents for the same shape.
 *
 * Wraps a [SQLiteDriver] so every connection it opens waits up to [busyTimeoutMs] for a competing connection's lock
 * instead of failing immediately with SQLITE_BUSY. `PRAGMA busy_timeout` is standard SQL, so it works the same way over
 * `WebWorkerSQLiteDriver`'s worker-message protocol as it does over a real native connection.
 */
class BusyTimeoutSQLiteDriver(
    private val delegate: SQLiteDriver,
    private val busyTimeoutMs: Long = DEFAULT_BUSY_TIMEOUT_MS,
) : SQLiteDriver {
    init {
        // SQLite disables the busy handler entirely for zero or negative values, which would silently
        // defeat this wrapper's whole purpose.
        require(busyTimeoutMs > 0) { "busyTimeoutMs must be positive, was $busyTimeoutMs" }
    }

    override suspend fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        try {
            connection.execSQL("PRAGMA busy_timeout = $busyTimeoutMs")
        } catch (@Suppress("TooGenericExceptionCaught") setupFailure: Throwable) {
            // Close the live connection before rethrowing so a failed setup never leaks it.
            connection.close()
            throw setupFailure
        }
        return connection
    }

    companion object {
        /**
         * Long enough to ride out the typical abandoned-writer overlap, short enough that a truly stuck holder still
         * surfaces as an error. Same value as `nonWebMain`'s copy — see its KDoc for the field-measured rationale.
         */
        const val DEFAULT_BUSY_TIMEOUT_MS = 10_000L
    }
}
