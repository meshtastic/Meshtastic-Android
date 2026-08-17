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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransportLifecycleGateTest {

    @Test
    fun `close rejects new work drains admitted work and tears down exactly once`() = runTest {
        val gate = TransportLifecycleGate("Test")
        val operationLease = checkNotNull(gate.tryAcquire())
        var teardowns = 0

        val firstClose = async { gate.close { teardowns++ } }
        runCurrent()
        val secondClose = async { gate.close { teardowns++ } }
        runCurrent()

        assertTrue(gate.isClosed)
        assertNull(gate.runIfOpen { Unit }, "close must reject operations before waiting for the admitted one")
        assertNull(gate.tryAcquire(), "close must stop issuing operation leases")
        assertFalse(firstClose.isCompleted)
        assertFalse(secondClose.isCompleted)

        operationLease.release()
        runCurrent()
        firstClose.await()
        secondClose.await()

        assertEquals(1, teardowns)
        assertNull(gate.runIfOpen { Unit }, "completed close must remain terminal")
        assertNull(gate.tryAcquire(), "completed close must remain terminal for leases")
    }

    @Test
    fun `teardown failure leaves the gate terminal and is not retried`() = runTest {
        val gate = TransportLifecycleGate("Test")
        val expected = IllegalStateException("teardown failed")
        var teardownAttempts = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                gate.close {
                    teardownAttempts++
                    throw expected
                }
            }

        assertIs<IllegalStateException>(failure)
        assertEquals(expected.message, failure.message)
        assertTrue(gate.isClosed)

        val repeatedFailure = assertFailsWith<IllegalStateException> { gate.close { teardownAttempts++ } }
        assertEquals(expected.message, repeatedFailure.message)
        assertEquals(1, teardownAttempts)
        assertNull(gate.runIfOpen { Unit })
    }

    @Test
    fun `throwing admitted block releases its operation lease`() = runTest {
        val gate = TransportLifecycleGate("Test")
        var teardowns = 0

        assertFailsWith<IllegalStateException> { gate.runIfOpen { throw IllegalStateException("operation failed") } }
        assertTrue(gate.close { teardowns++ }, "a released lease must let close drain without timing out")

        assertEquals(1, teardowns)
    }

    @Test
    fun `close bounds a leaked operation before teardown`() = runTest {
        val gate = TransportLifecycleGate("Test")
        val lease = checkNotNull(gate.tryAcquire())
        var teardowns = 0

        val closeJob = async { gate.close { teardowns++ } }
        runCurrent()
        assertFalse(closeJob.isCompleted)

        advanceTimeBy(TransportLifecycleGate.OPERATION_DRAIN_TIMEOUT.inWholeMilliseconds)
        runCurrent()
        assertFalse(closeJob.await(), "a leaked admitted operation must be reported as an incomplete close")

        assertEquals(1, teardowns)
        lease.release()
    }

    @Test
    fun `close bounds preparation separately before teardown`() = runTest {
        val gate = TransportLifecycleGate("Test")
        var teardowns = 0

        val closeJob = async { gate.close(beforeDrain = { awaitCancellation() }, teardown = { teardowns++ }) }
        runCurrent()
        assertFalse(closeJob.isCompleted)

        advanceTimeBy(TransportLifecycleGate.OPERATION_DRAIN_TIMEOUT.inWholeMilliseconds)
        runCurrent()

        assertFalse(closeJob.await(), "a timed-out preparation must report an incomplete close")
        assertEquals(1, teardowns, "teardown must still run after preparation times out")
    }

    @Test
    fun `close reports a throwing preparation without skipping teardown`() = runTest {
        val gate = TransportLifecycleGate("Test")
        val expected = IllegalStateException("preparation failed")
        var teardowns = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                gate.close(beforeDrain = { throw expected }, teardown = { teardowns++ })
            }

        assertEquals(expected.message, failure.message)
        assertEquals(1, teardowns, "teardown must still release the resource after preparation fails")
        val repeated = assertFailsWith<IllegalStateException> { gate.close() }
        assertEquals(expected.message, repeated.message, "concurrent or later closers must observe the shared failure")
    }

    @Test
    fun `close bounds cooperative suspended teardown and releases every waiter`() = runTest {
        val gate = TransportLifecycleGate("test")
        var teardownCalls = 0

        val first = async {
            gate.close {
                teardownCalls++
                awaitCancellation()
            }
        }
        testScheduler.runCurrent()
        val second = async { gate.close() }
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(TransportLifecycleGate.TEARDOWN_TIMEOUT.inWholeMilliseconds)
        testScheduler.runCurrent()
        assertFalse(first.await(), "a timed-out teardown must be reported to the owning closer")
        assertFalse(second.await(), "every concurrent closer must observe the same timed-out result")

        assertEquals(1, teardownCalls)
        assertTrue(gate.isClosed)
    }

    @Test
    fun `releasing a lease twice does not unblock close early`() = runTest {
        val gate = TransportLifecycleGate("Test")
        val first = checkNotNull(gate.tryAcquire())
        val second = checkNotNull(gate.tryAcquire())
        var teardowns = 0

        first.release()
        first.release()

        val closeJob = async { gate.close { teardowns++ } }
        runCurrent()
        assertFalse(closeJob.isCompleted, "a repeated release must not drop the second lease")

        second.release()
        runCurrent()
        assertTrue(closeJob.await())
        assertEquals(1, teardowns)
    }
}
