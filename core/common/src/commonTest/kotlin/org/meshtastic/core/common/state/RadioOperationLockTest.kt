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
        assertEquals(emptySet(), lock.activeOperations)
    }

    @Test
    fun releasingAnUnheldLeaseIsSafe() {
        val lock = RadioOperationLock()
        lock.release(null)
        val lease = lock.acquire(RadioOperation.FirmwareUpdate)
        lock.release(lease)
        lock.release(lease)
        assertFalse(lock.isActive)
    }

    @Test
    fun twoHoldersOfTheSameOperationAreIndependent() {
        // The firmware view model cancels an update job and starts a replacement without joining, so the cancelled
        // job's release can land after the replacement has acquired. The late release must not retire the live holder.
        val lock = RadioOperationLock()
        val abandoned = lock.acquire(RadioOperation.FirmwareUpdate)
        val replacement = lock.acquire(RadioOperation.FirmwareUpdate)

        lock.release(abandoned)

        assertTrue(lock.isActive, "the replacement holder must still hold the operation")
        assertEquals(setOf(RadioOperation.FirmwareUpdate), lock.activeOperations)

        lock.release(replacement)
        assertFalse(lock.isActive)
    }

    @Test
    fun differentOperationsDoNotClobberEachOther() {
        val lock = RadioOperationLock()
        val update = lock.acquire(RadioOperation.FirmwareUpdate)
        lock.acquire(RadioOperation.DiscoveryScan)

        lock.release(update)

        assertEquals(setOf(RadioOperation.DiscoveryScan), lock.activeOperations)
    }

    @Test
    fun onlyMaintenanceSuppressesTheTransport() {
        val lock = RadioOperationLock()

        lock.acquire(RadioOperation.DiscoveryScan)
        assertFalse(lock.suppressesTransport, "a scan needs the transport running")

        lock.acquire(RadioOperation.FirmwareUpdate)
        assertFalse(lock.suppressesTransport, "an OTA frees the transport by deselecting, not by suppression")

        val maintenance = lock.acquire(RadioOperation.FirmwareMaintenance)
        assertTrue(lock.suppressesTransport)

        lock.release(maintenance)
        assertFalse(lock.suppressesTransport, "suppression must end with the maintenance pass")
    }

    @Test
    fun suppressionSurvivesAnotherMaintenanceHolder() {
        val lock = RadioOperationLock()
        val first = lock.acquire(RadioOperation.FirmwareMaintenance)
        lock.acquire(RadioOperation.FirmwareMaintenance)

        lock.release(first)

        assertTrue(lock.suppressesTransport, "the second maintenance holder still needs the transport off the port")
    }

    @Test
    fun withOperationReleasesOnSuccess() = runTest {
        val lock = RadioOperationLock()
        assertEquals("done", lock.withOperation(RadioOperation.FirmwareUpdate) { "done" })
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
    fun withOperationCancellationLeavesAConcurrentHolderIntact() = runTest {
        val lock = RadioOperationLock()
        val other = lock.acquire(RadioOperation.FirmwareUpdate)

        assertFailsWith<CancellationException> {
            lock.withOperation(RadioOperation.FirmwareUpdate) { throw CancellationException("abandoned") }
        }

        assertTrue(lock.isActive, "the surviving holder keeps the operation")
        lock.release(other)
        assertFalse(lock.isActive)
    }

    @Test
    fun holdersFlowTracksAcquireAndRelease() = runTest {
        val lock = RadioOperationLock()
        assertEquals(emptyMap(), lock.holders.value)

        lock.withOperation(RadioOperation.DiscoveryScan) {
            assertEquals(listOf(RadioOperation.DiscoveryScan), lock.holders.value.values.toList())
        }

        assertEquals(emptyMap(), lock.holders.value)
    }
}
