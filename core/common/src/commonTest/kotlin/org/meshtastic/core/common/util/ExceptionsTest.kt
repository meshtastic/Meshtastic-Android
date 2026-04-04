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
package org.meshtastic.core.common.util

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExceptionsTest {

    @AfterTest
    fun tearDown() {
        Exceptions.reporter = null
    }

    // ---------- Exceptions.report ----------

    @Test
    fun `report invokes configured reporter with all arguments`() {
        var captured: Triple<Throwable, String?, String?>? = null
        Exceptions.reporter = { ex, tag, msg -> captured = Triple(ex, tag, msg) }

        val error = RuntimeException("boom")
        Exceptions.report(error, tag = "MyTag", message = "context")

        assertEquals(error, captured?.first)
        assertEquals("MyTag", captured?.second)
        assertEquals("context", captured?.third)
    }

    @Test
    fun `report works with null tag and message`() {
        var captured: Triple<Throwable, String?, String?>? = null
        Exceptions.reporter = { ex, tag, msg -> captured = Triple(ex, tag, msg) }

        Exceptions.report(RuntimeException("x"))

        assertNull(captured?.second)
        assertNull(captured?.third)
    }

    @Test
    fun `report does not crash when no reporter is configured`() {
        Exceptions.reporter = null
        // Should not throw
        Exceptions.report(RuntimeException("no reporter"))
    }

    // ---------- ignoreException ----------

    @Test
    fun `ignoreException swallows exceptions from inner block`() {
        var reached = false
        ignoreException { throw IllegalStateException("expected") }
        reached = true
        assertTrue(reached)
    }

    @Test
    fun `ignoreException does not swallow when inner succeeds`() {
        var executed = false
        ignoreException { executed = true }
        assertTrue(executed)
    }

    @Test
    fun `ignoreException silent mode suppresses logging`() {
        // Should not crash even in silent mode
        ignoreException(silent = true) { throw RuntimeException("silent") }
    }

    @Test
    fun `ignoreException non-silent mode logs but does not crash`() {
        ignoreException(silent = false) { throw RuntimeException("logged") }
    }

    // ---------- ignoreExceptionSuspend ----------

    @Test
    fun `ignoreExceptionSuspend swallows exceptions`() = runTest {
        var reached = false
        ignoreExceptionSuspend { throw IllegalArgumentException("async boom") }
        reached = true
        assertTrue(reached)
    }

    @Test
    fun `ignoreExceptionSuspend silent mode suppresses logging`() = runTest {
        ignoreExceptionSuspend(silent = true) { throw RuntimeException("silent async") }
    }

    @Test
    fun `ignoreExceptionSuspend executes block normally when no exception`() = runTest {
        var executed = false
        ignoreExceptionSuspend { executed = true }
        assertTrue(executed)
    }

    // ---------- exceptionReporter ----------

    @Test
    fun `exceptionReporter reports exceptions to configured reporter`() {
        var reportCalled = false
        Exceptions.reporter = { _, _, _ -> reportCalled = true }

        exceptionReporter { throw RuntimeException("reported") }

        assertTrue(reportCalled)
    }

    @Test
    fun `exceptionReporter does not invoke reporter when block succeeds`() {
        var reportCalled = false
        Exceptions.reporter = { _, _, _ -> reportCalled = true }

        exceptionReporter {
            // no exception
        }

        assertFalse(reportCalled)
    }

    @Test
    fun `exceptionReporter works without configured reporter`() {
        Exceptions.reporter = null
        // Should not crash
        exceptionReporter { throw RuntimeException("no reporter configured") }
    }
}
