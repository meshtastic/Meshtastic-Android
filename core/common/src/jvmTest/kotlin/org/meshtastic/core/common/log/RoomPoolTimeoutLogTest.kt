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
package org.meshtastic.core.common.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomPoolTimeoutLogTest {

    private val logged = mutableListOf<Pair<Severity, String>>()
    private val passedThrough = ByteArrayOutputStream()
    private var now = 0L
    private val tap = RoomPoolTimeoutLog.Tap(PrintStream(passedThrough, true), minIntervalNanos = 60L) { now }
    private lateinit var previousWriters: List<LogWriter>

    @BeforeTest
    fun setUp() {
        previousWriters = Logger.config.logWriterList
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    logged += severity to message
                }
            },
        )
    }

    @AfterTest
    fun tearDown() {
        Logger.setLogWriters(previousWriters)
    }

    @Test
    fun aRoomPoolTimeoutDumpIsForwardedWithoutItsFramesAndStillPrinted() {
        // The shape androidx.sqlite's SQLite.throwSQLiteException(5, …) gives Room's ConnectionPoolImpl.onTimeout dump.
        val timeout = RuntimeException(ROOM_TIMEOUT_MESSAGE)

        timeout.printStackTrace(tap)

        val forwarded = logged.single()
        assertEquals(Severity.Warn, forwarded.first)
        assertTrue(forwarded.second.contains("CoroutineName(org.example.Repo\$insert\$2\$1)"))
        assertFalse(forwarded.second.contains("at androidx.room3"), "stack frames are dropped")
        assertTrue(passedThrough.toString().contains("Writer pool:"), "System.err output must pass through")
    }

    @Test
    fun repeatedTimeoutsAreForwardedOncePerInterval() {
        val timeout = RuntimeException(ROOM_TIMEOUT_MESSAGE)

        timeout.printStackTrace(tap)
        now += 30L
        timeout.printStackTrace(tap)
        now += 30L
        timeout.printStackTrace(tap)

        assertEquals(2, logged.size)
    }

    @Test
    fun theTapDoesNotFlushTheDelegateMidLine() {
        var flushes = 0
        val sink =
            object : OutputStream() {
                override fun write(b: Int) = Unit

                override fun flush() {
                    flushes++
                }
            }
        val lineBuffered = RoomPoolTimeoutLog.Tap(PrintStream(sink, false), minIntervalNanos = 60L) { now }

        lineBuffered.print("prefix ")
        lineBuffered.println("suffix")

        assertEquals(0, flushes, "Android's stderr logs a partial line on every flush")
    }

    @Test
    fun otherErrorOutputIsNotForwarded() {
        RuntimeException("Error code: 5, message: database is locked").printStackTrace(tap)
        tap.println("Error code: 5, message: Timed out attempting to acquire a writer connection.")

        assertTrue(logged.isEmpty())
    }

    private companion object {
        val ROOM_TIMEOUT_MESSAGE =
            """
            Error code: 5, message: Timed out attempting to acquire a writer connection.

            Writer pool:
            Pool (capacity=1, permits=0, queue=(size=1))
            	[1] - ConnectionWrapperImpl
            		Status: Acquired connection
            		Coroutine: [CoroutineName(org.example.Repo${'$'}insert${'$'}2${'$'}1), Dispatchers.IO]
            		Acquired:
            			at androidx.room3.coroutines.Pool.acquire(Pool.kt:60)
            """
                .trimIndent()
    }
}
