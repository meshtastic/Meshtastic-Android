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
package org.meshtastic.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleScanResultGateTest {
    @Test
    fun first_result_is_delivered() {
        val gate = SingleScanResultGate()
        val results = mutableListOf<String?>()

        val delivered = gate.tryDeliver("qr") { results.add(it) }

        assertTrue(delivered)
        assertEquals(listOf("qr"), results)
    }

    @Test
    fun duplicate_results_are_ignored() {
        val gate = SingleScanResultGate()
        val results = mutableListOf<String?>()

        assertTrue(gate.tryDeliver("first") { results.add(it) })
        assertFalse(gate.tryDeliver("second") { results.add(it) })

        assertEquals(listOf("first"), results)
    }

    @Test
    fun dismiss_blocks_late_scan_result() {
        val gate = SingleScanResultGate()
        val results = mutableListOf<String?>()

        assertTrue(gate.tryDeliver(null) { results.add(it) })
        assertFalse(gate.tryDeliver("late") { results.add(it) })

        assertEquals(listOf(null), results)
    }

    @Test
    fun throwing_callback_consumes_gate() {
        val gate = SingleScanResultGate()
        val secondCallbackCount = AtomicInteger(0)

        try {
            gate.tryDeliver("first") { throw IllegalStateException("boom") }
            throw AssertionError("Expected callback failure")
        } catch (exc: IllegalStateException) {
            assertEquals("boom", exc.message)
        }

        assertFalse(gate.tryDeliver("second") { secondCallbackCount.incrementAndGet() })
        assertEquals(0, secondCallbackCount.get())
    }

    @Test
    fun concurrent_results_deliver_once() {
        val gate = SingleScanResultGate()
        val callbackCount = AtomicInteger(0)
        val threadCount = 64
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            repeat(threadCount) {
                executor.execute {
                    try {
                        startGate.await()
                        gate.tryDeliver("qr") { callbackCount.incrementAndGet() }
                    } finally {
                        doneGate.countDown()
                    }
                }
            }

            startGate.countDown()

            assertTrue("Timed out waiting for concurrent deliveries", doneGate.await(5, TimeUnit.SECONDS))
            assertEquals(1, callbackCount.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
