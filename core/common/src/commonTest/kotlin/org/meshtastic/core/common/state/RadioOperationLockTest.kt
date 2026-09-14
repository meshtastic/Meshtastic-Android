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
package org.meshtastic.core.common.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioOperationLockTest {

    @Test
    fun startsIdle() {
        val lock = RadioOperationLock()
        assertFalse(lock.isActive)
        assertFalse(lock.suppressesTransport)
        assertEquals(emptySet(), lock.active.value)
    }

    @Test
    fun acquireIsIdempotent() {
        val lock = RadioOperationLock()
        lock.acquire(RadioOperation.DiscoveryScan)
        lock.acquire(RadioOperation.DiscoveryScan)
        assertEquals(setOf(RadioOperation.DiscoveryScan), lock.active.value)

        lock.release(RadioOperation.DiscoveryScan)
        assertFalse(lock.isActive, "one release must clear a re-taken operation, as the old lock did")
    }

    @Test
    fun releasingAnUnheldOperationIsSafe() {
        val lock = RadioOperationLock()
        lock.release(RadioOperation.FirmwareUpdate)
        assertFalse(lock.isActive)
    }

    @Test
    fun operationsDoNotClobberEachOther() {
        val lock = RadioOperationLock()
        lock.acquire(RadioOperation.FirmwareUpdate)
        lock.acquire(RadioOperation.DiscoveryScan)

        lock.release(RadioOperation.FirmwareUpdate)

        assertTrue(lock.isActive, "releasing one operation must not release the other")
        assertEquals(setOf(RadioOperation.DiscoveryScan), lock.active.value)
    }

    @Test
    fun onlyMaintenanceSuppressesTheTransport() {
        val lock = RadioOperationLock()

        lock.acquire(RadioOperation.DiscoveryScan)
        assertFalse(lock.suppressesTransport, "a scan needs the transport running")

        lock.acquire(RadioOperation.FirmwareUpdate)
        assertFalse(lock.suppressesTransport, "an OTA frees the transport by deselecting, not by suppression")

        lock.acquire(RadioOperation.FirmwareMaintenance)
        assertTrue(lock.suppressesTransport)

        lock.release(RadioOperation.FirmwareMaintenance)
        assertFalse(lock.suppressesTransport, "suppression must end with the maintenance pass")
    }

    @Test
    fun withOperationReleasesOnSuccess() = runTest {
        val lock = RadioOperationLock()
        val result = lock.withOperation(RadioOperation.FirmwareUpdate) { "done" }
        assertEquals("done", result)
        assertFalse(lock.isActive)
    }

    @Test
    fun withOperationReleasesOnFailure() = runTest {
        val lock = RadioOperationLock()
        assertFailsWith<IllegalStateException> {
            lock.withOperation(RadioOperation.FirmwareUpdate) { error("flash failed") }
        }
        assertFalse(lock.isActive, "a failed operation must not leak the lock")
    }

    @Test
    fun withOperationReleasesOnCancellation() = runTest {
        val lock = RadioOperationLock()
        assertFailsWith<CancellationException> {
            lock.withOperation(RadioOperation.DiscoveryScan) { throw CancellationException("stopped") }
        }
        assertFalse(lock.isActive, "a cancelled operation must not leak the lock")
    }

    @Test
    fun activeFlowTracksAcquireAndRelease() = runTest {
        val lock = RadioOperationLock()
        assertEquals(emptySet(), lock.active.value)

        lock.withOperation(RadioOperation.DiscoveryScan) {
            assertEquals(setOf(RadioOperation.DiscoveryScan), lock.active.value)
        }

        assertEquals(emptySet(), lock.active.value)
    }
}
