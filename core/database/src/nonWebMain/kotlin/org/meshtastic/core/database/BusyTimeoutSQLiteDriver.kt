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
 * android/jvm/iOS-only: `androidx.sqlite.SQLiteDriver.open()` is declared non-`suspend` on this "nonWeb" branch of
 * androidx.sqlite's own source (its `webMain` branch declares `suspend fun open`, to support async drivers like
 * `WebWorkerSQLiteDriver`) — confirmed from `androidx.sqlite:sqlite:2.7.0`'s own sources
 * (`nonWebMain/androidx/sqlite/SQLiteDriver.nonWeb.kt` vs `webMain/androidx/sqlite/SQLiteDriver.web.kt`). So this class
 * cannot be a single commonMain implementation as originally written; wasmJs has its own copy — same name, same shape,
 * `suspend fun open` — in `wasmJsMain`'s `BusyTimeoutSQLiteDriver.kt`. Not an `expect`/`actual` pair (the suspend
 * modifier itself differs, which `actual` can't paper over); each source set simply declares its own class, exactly
 * like `core:ble`'s `BleServiceExtensions.kt` documents for the same "two independent declarations, disjoint
 * compilations" shape.
 *
 * Wraps a [SQLiteDriver] so every connection it opens waits up to [busyTimeoutMs] for a competing connection's lock
 * instead of failing immediately with SQLITE_BUSY ("Error code: 5, message: database is locked").
 *
 * Each database normally holds a single connection (see `configureCommon`), but `DatabaseManager`'s wedge recovery
 * deliberately abandons a stalled connection and opens a replacement pool against the same file (see
 * `abandonWedgedDbBlock`). Until the abandoned callback finishes, both connections are live — and the bundled driver's
 * default busy timeout is zero, so any write on the replacement fails the moment the abandoned connection holds the
 * write lock. In the field (2.8.1, build 29321949) that surfaced as a fatal uncaught SQLITE_BUSY from Room's own
 * invalidation-tracker housekeeping (`TriggerBasedInvalidationTracker.syncTriggers`), which the app cannot catch. A
 * busy timeout makes the replacement connection wait out the overlap instead.
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

    override fun open(fileName: String): SQLiteConnection {
        val connection = delegate.open(fileName)
        try {
            connection.execSQL("PRAGMA busy_timeout = $busyTimeoutMs")
        } catch (@Suppress("TooGenericExceptionCaught") setupFailure: Throwable) {
            // The platforms throw different exception types here (android.database.SQLException on
            // Android, androidx.sqlite.SQLiteException elsewhere); close the live native connection
            // before rethrowing so a failed setup never leaks it.
            connection.close()
            throw setupFailure
        }
        return connection
    }

    companion object {
        /**
         * Long enough to ride out the typical abandoned-writer overlap (slow MeshLog cleanups and node-heavy packet
         * transactions observed in the field run 1-30s), short enough that a truly stuck holder still surfaces as an
         * error rather than an ANR-adjacent stall. Waiting happens on Room's I/O dispatcher, never the main thread.
         */
        const val DEFAULT_BUSY_TIMEOUT_MS = 10_000L
    }
}
