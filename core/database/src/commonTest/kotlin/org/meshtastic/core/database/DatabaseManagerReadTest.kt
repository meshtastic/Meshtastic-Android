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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseManagerReadTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun boundedReadUsesCapturedPublishedPoolWithoutWriterAdmissionOrReplay() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var invocationCount = 0

        val result = async {
            manager.withReadDb { database ->
                invocationCount += 1
                assertTrue(database === original)
                assertEquals(0 to 0, manager.debugWriterCounts())
                assertFalse(manager.debugWriterGateArmed())
                started.complete(Unit)
                release.await()
                database
            }
        }

        started.await()
        manager.switchActiveDatabase("addrB")
        val replacement = manager.currentDb.value
        assertTrue(replacement !== original)

        release.complete(Unit)
        assertTrue(result.await() === original)
        assertEquals(1, invocationCount)
        assertTrue(manager.currentDb.value === replacement)
        assertEquals(0 to 0, manager.debugWriterCounts())
        assertFalse(manager.debugWriterGateArmed())
    }

    @Test
    fun boundedReadPropagatesFailureWithoutReplay() = runTest(testDispatcher) {
        var invocationCount = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                manager.withReadDb<Unit> {
                    invocationCount += 1
                    error("read failed")
                }
            }

        assertEquals("read failed", failure.message)
        assertEquals(1, invocationCount)
        assertEquals(0 to 0, manager.debugWriterCounts())
        assertFalse(manager.debugWriterGateArmed())
    }
}
