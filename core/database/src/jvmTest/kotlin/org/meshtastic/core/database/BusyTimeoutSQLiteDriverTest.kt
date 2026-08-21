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

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BusyTimeoutSQLiteDriverTest {

    private val dbFile = File.createTempFile("busy-timeout-driver", ".db").apply { delete() }

    @AfterTest
    fun cleanUp() {
        dbFile.delete()
    }

    @Test
    fun appliesBusyTimeoutToEveryOpenedConnection() {
        val driver = BusyTimeoutSQLiteDriver(BundledSQLiteDriver())
        driver.open(dbFile.absolutePath).use { connection ->
            connection.prepare("PRAGMA busy_timeout").use { statement ->
                statement.step()
                assertEquals(BusyTimeoutSQLiteDriver.DEFAULT_BUSY_TIMEOUT_MS, statement.getLong(0))
            }
        }
    }

    /**
     * The field failure shape (2.8.1, 29321949): a second connection to the same file writes while the first holds the
     * write lock. The bundled driver's zero default fails instantly with SQLITE_BUSY; the wrapped driver waits the
     * holder out.
     */
    @Test
    fun waitsOutACompetingWriteLockInsteadOfFailingInstantly() {
        val bare = BundledSQLiteDriver()
        val holder = bare.open(dbFile.absolutePath)
        holder.execSQL("CREATE TABLE t (x INTEGER)")

        // Baseline: with the driver unwrapped (busy_timeout = 0), the competing write fails immediately.
        holder.execSQL("BEGIN IMMEDIATE")
        bare.open(dbFile.absolutePath).use { competitor ->
            assertFailsWith<Exception> { competitor.execSQL("INSERT INTO t VALUES (1)") }
        }

        // Wrapped: the same competing write outlasts a lock held for 200ms.
        val releaser = thread {
            Thread.sleep(200)
            holder.execSQL("COMMIT")
        }
        try {
            BusyTimeoutSQLiteDriver(bare).open(dbFile.absolutePath).use { competitor ->
                competitor.execSQL("INSERT INTO t VALUES (2)")
                competitor.prepare("SELECT COUNT(*) FROM t").use { statement ->
                    statement.step()
                    assertEquals(1L, statement.getLong(0))
                }
            }
        } finally {
            releaser.join()
            holder.close()
        }
    }
}
