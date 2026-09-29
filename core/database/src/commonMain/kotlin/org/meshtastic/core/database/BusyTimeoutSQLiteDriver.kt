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
 * Wraps a [SQLiteDriver] so every connection it opens waits up to [busyTimeoutMs] for a competing connection's lock
 * instead of failing immediately with SQLITE_BUSY ("Error code: 5, message: database is locked").
 *
 * Each database normally holds a single connection (see `configureCommon`), but [DatabaseManager]'s wedge recovery can
 * leave an abandoned callback's connection open beside a replacement pool on the same file (see
 * `abandonWedgedDbBlock`). Recovery never publishes a replacement while another connection holds the write lock, so
 * this timeout only covers overlaps that begin afterwards. Room raises any timeout below 3s on open, and runs its
 * invalidation-tracker trigger sync (`TriggerBasedInvalidationTracker.syncTriggers`) under `BEGIN IMMEDIATE` with its
 * SQLITE_BUSY uncaught, so a lock held past this timeout is fatal.
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
         * Long enough to ride out a short write on another connection, short enough that a truly stuck holder still
         * surfaces as an error rather than an ANR-adjacent stall. Waiting happens on Room's I/O dispatcher, never the
         * main thread.
         */
        const val DEFAULT_BUSY_TIMEOUT_MS = 10_000L
    }
}
