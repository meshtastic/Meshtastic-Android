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
package org.meshtastic.core.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.RobolectricBleBonding
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the #5849 fix in [AndroidBluetoothRepository.bond]: when `createBond()` returns false the flow no longer
 * fails outright — it re-checks `bondState` and either resumes (already BONDED), keeps waiting on the broadcast
 * receiver (still BONDING), or fails ("Failed to initiate bonding").
 *
 * Exercises the real production method via Robolectric shadows (no production seam): [RobolectricBleBonding] drives the
 * address-cached [ShadowBluetoothDevice] and fires `ACTION_BOND_STATE_CHANGED` broadcasts. Each test uses a distinct
 * MAC so the static device cache cannot bleed bond-state across tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBluetoothRepositoryBondTest {

    private fun newRepository(dispatcher: TestDispatcher): AndroidBluetoothRepository {
        val context = RuntimeEnvironment.getApplication()
        val dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)
        return AndroidBluetoothRepository(context, dispatchers, startedLifecycle())
    }

    /** A minimal STARTED lifecycle so the repository's `init { ... }` launch has a live, non-cancelled scope. */
    private fun startedLifecycle(): Lifecycle {
        val owner =
            object : LifecycleOwner {
                override val lifecycle: LifecycleRegistry =
                    LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
            }
        return owner.lifecycle
    }

    /**
     * Launch `bond()` in the background so it can suspend (e.g. in the BONDING branch) while the test fires the
     * resolving broadcast. The returned [Deferred] completes with the throwable `bond()` raised, or `null` on success.
     * Using launch + captured result avoids `async`/`await` exception-propagation surprises under the test scope.
     */
    private fun TestScope.launchBond(repo: AndroidBluetoothRepository, mac: String): Deferred<Throwable?> {
        val result = CompletableDeferred<Throwable?>()
        backgroundScope.launch {
            result.complete(runCatching { repo.bond(FakeBleDevice(address = mac)) }.exceptionOrNull())
        }
        return result
    }

    @Test
    fun `createBond false while still BONDING keeps waiting and resumes on a later BONDED broadcast`() =
        runTest(UnconfinedTestDispatcher()) {
            val mac = "AA:BB:CC:DD:EE:01"
            RobolectricBleBonding.grantBluetoothConnectPermission()
            RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_BONDING, createBondReturns = false)
            val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

            // bond() runs until it parks in the BONDING branch (receiver left registered).
            val failure = launchBond(repo, mac)

            // The OS later completes bonding — the broadcast must resume the suspended call.
            RobolectricBleBonding.sendBondStateChanged(
                mac,
                newState = BluetoothDevice.BOND_BONDED,
                previousState = BluetoothDevice.BOND_BONDING,
            )

            assertNull(failure.await(), "bond() should have resumed without an error")
        }

    @Test
    fun `createBond false with no in-flight bond fails to initiate bonding`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:02"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_NONE, createBondReturns = false)
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val error = assertFailsWith<Exception> { repo.bond(FakeBleDevice(address = mac)) }
        assertEquals("Failed to initiate bonding", error.message)
    }

    @Test
    @Config(sdk = [34], shadows = [ShadowBondingThenBonded::class])
    fun `post receiver registration bond recheck resumes without createBond`() = runTest(UnconfinedTestDispatcher()) {
        // Models the real-device race where the bond completes between the early bondState check and receiver
        // registration. The custom shadow returns BONDING first, then BONDED, so the registered receiver is cleaned
        // up before createBond() is called.
        val mac = "AA:BB:CC:DD:EE:03"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        ShadowBondingThenBonded.createBondCalls = 0
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        // Resumes (no broadcast) via the post-registration BOND_BONDED re-check; assert no error surfaced.
        assertNull(launchBond(repo, mac).await(), "bond() should resume when the re-check finds BOND_BONDED")
        assertEquals(0, ShadowBondingThenBonded.createBondCalls, "createBond() should not run after the re-check")
    }

    @Test
    fun `already bonded device returns immediately without initiating a bond`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:04"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        // Make the device already bonded both at the adapter level (so isBonded is observable) and per-device
        // (so bond() hits the early BOND_BONDED guard at line 85 without ever calling createBond()).
        RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_BONDED, createBondReturns = false)
        shadowOf(BluetoothAdapter.getDefaultAdapter())
            .setBondedDevices(setOf(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)))
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        assertNull(launchBond(repo, mac).await(), "an already-bonded device should return without error")
        assertTrue(repo.isBonded(mac), "the device should remain reported as bonded")
    }

    @Test
    fun `bond fails when bonding is rejected (BOND_NONE from BONDING)`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:05"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_BONDING, createBondReturns = false)
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val failure = launchBond(repo, mac)
        RobolectricBleBonding.sendBondStateChanged(
            mac,
            newState = BluetoothDevice.BOND_NONE,
            previousState = BluetoothDevice.BOND_BONDING,
        )

        assertEquals("Bonding failed or rejected", failure.await()?.message)
    }

    @Test
    fun `createBond true then a BONDED broadcast resumes the bond`() = runTest(UnconfinedTestDispatcher()) {
        // The ordinary successful path: createBond() succeeds, the call parks on the receiver, and the OS later
        // confirms via ACTION_BOND_STATE_CHANGED(BOND_BONDED).
        val mac = "AA:BB:CC:DD:EE:08"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_NONE, createBondReturns = true)
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val failure = launchBond(repo, mac)
        RobolectricBleBonding.sendBondStateChanged(
            mac,
            newState = BluetoothDevice.BOND_BONDED,
            previousState = BluetoothDevice.BOND_BONDING,
        )

        assertNull(failure.await(), "a freshly initiated bond should resolve on the BONDED broadcast")
    }

    @Test
    fun `a BOND_NONE broadcast from a non-BONDING state does not fail the parked bond`() =
        runTest(UnconfinedTestDispatcher()) {
            // Only BOND_NONE *from* BOND_BONDING means rejection; a spurious BOND_NONE (prev != BONDING) must be
            // ignored and leave the call waiting.
            val mac = "AA:BB:CC:DD:EE:09"
            RobolectricBleBonding.grantBluetoothConnectPermission()
            RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_BONDING, createBondReturns = false)
            val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

            val failure = launchBond(repo, mac)
            RobolectricBleBonding.sendBondStateChanged(
                mac,
                newState = BluetoothDevice.BOND_NONE,
                previousState = BluetoothDevice.BOND_NONE,
            )
            assertFalse(failure.isCompleted, "a spurious BOND_NONE must not resolve the bond")

            // A genuine completion still resolves it (and cleans up the background coroutine).
            RobolectricBleBonding.sendBondStateChanged(
                mac,
                newState = BluetoothDevice.BOND_BONDED,
                previousState = BluetoothDevice.BOND_BONDING,
            )
            assertNull(failure.await())
        }

    @Test
    fun `bond times out when no terminal bond broadcast arrives`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:0A"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_BONDING, createBondReturns = false)
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val failure = launchBond(repo, mac)
        advanceTimeBy(29_999L)
        assertFalse(failure.isCompleted, "bond() should still be waiting before the timeout")
        advanceTimeBy(2L)

        assertEquals("Timed out waiting for bonding to complete", failure.await()?.message)
    }

    @Test
    fun `bond completes early when bond state becomes bonded without broadcast`() =
        runTest(UnconfinedTestDispatcher()) {
            val mac = "AA:BB:CC:DD:EE:0B"
            RobolectricBleBonding.grantBluetoothConnectPermission()
            val deviceShadow =
                RobolectricBleBonding.primeBond(
                    mac,
                    bondState = BluetoothDevice.BOND_BONDING,
                    createBondReturns = false,
                )
            val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

            val failure = launchBond(repo, mac)
            advanceTimeBy(499L)
            assertFalse(failure.isCompleted, "bond() should still be waiting before the poll interval")
            deviceShadow.setBondState(BluetoothDevice.BOND_BONDED)
            advanceTimeBy(2L)

            assertNull(failure.await(), "bond() should accept the polled BONDED state before timeout")
        }

    @Test
    fun `bond fails early when bond state returns none without broadcast`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:0D"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        val deviceShadow =
            RobolectricBleBonding.primeBond(
                mac,
                bondState = BluetoothDevice.BOND_BONDING,
                createBondReturns = false,
            )
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val failure = launchBond(repo, mac)
        advanceTimeBy(499L)
        assertFalse(failure.isCompleted, "bond() should still be waiting before the poll interval")
        deviceShadow.setBondState(BluetoothDevice.BOND_NONE)
        advanceTimeBy(2L)

        assertEquals("Bonding failed or rejected", failure.await()?.message)
    }

    @Test
    fun `createBond true does not fail immediately while bond state is initially none`() =
        runTest(UnconfinedTestDispatcher()) {
            val mac = "AA:BB:CC:DD:EE:0E"
            RobolectricBleBonding.grantBluetoothConnectPermission()
            val deviceShadow =
                RobolectricBleBonding.primeBond(mac, bondState = BluetoothDevice.BOND_NONE, createBondReturns = true)
            val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

            val failure = launchBond(repo, mac)
            deviceShadow.setBondState(BluetoothDevice.BOND_NONE)
            advanceTimeBy(499L)
            assertFalse(failure.isCompleted, "bond() should still be waiting before the initial grace poll")
            advanceTimeBy(2L)
            assertFalse(failure.isCompleted, "a newly initiated bond gets one poll before BOND_NONE is terminal")
            deviceShadow.setBondState(BluetoothDevice.BOND_BONDING)
            advanceTimeBy(500L)
            assertFalse(failure.isCompleted, "BOND_BONDING should keep the bond wait active")
            deviceShadow.setBondState(BluetoothDevice.BOND_BONDED)
            advanceTimeBy(500L)

            assertNull(failure.await(), "bond() should complete once Android reports BOND_BONDED")
        }

    @Test
    fun `bond succeeds when bond state becomes bonded at timeout boundary`() = runTest(UnconfinedTestDispatcher()) {
        val mac = "AA:BB:CC:DD:EE:0C"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        val deviceShadow =
            RobolectricBleBonding.primeBond(
                mac,
                bondState = BluetoothDevice.BOND_BONDING,
                createBondReturns = false,
            )
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        val failure = launchBond(repo, mac)
        advanceTimeBy(29_999L)
        assertFalse(failure.isCompleted, "bond() should still be waiting before the timeout")
        deviceShadow.setBondState(BluetoothDevice.BOND_BONDED)
        advanceTimeBy(2L)

        assertNull(failure.await(), "bond() should accept the final BONDED state after timeout")
    }

    @Test
    fun `isBonded reflects the adapter bonded devices`() = runTest(UnconfinedTestDispatcher()) {
        val bondedMac = "AA:BB:CC:DD:EE:06"
        val otherMac = "AA:BB:CC:DD:EE:07"
        RobolectricBleBonding.grantBluetoothConnectPermission()
        val bondedDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bondedMac)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setBondedDevices(setOf(bondedDevice))
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.isBonded(bondedMac))
        assertFalse(repo.isBonded(otherMac))
    }

    @Test
    fun `isValid accepts a well-formed MAC and rejects garbage`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepository(UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.isValid("AA:BB:CC:DD:EE:FF"))
        assertFalse(repo.isValid("not-a-mac"))
    }

    /**
     * Custom shadow that returns [BluetoothDevice.BOND_BONDING] on the first `getBondState()` read (the early guard)
     * and [BluetoothDevice.BOND_BONDED] thereafter (the post-receiver-registration re-check), reproducing the
     * bond-completed-mid-method race without a broadcast.
     */
    @Implements(BluetoothDevice::class)
    class ShadowBondingThenBonded : ShadowBluetoothDevice() {
        companion object {
            var createBondCalls: Int = 0
        }

        private var bondStateReads = 0

        @Implementation
        override fun getBondState(): Int =
            if (bondStateReads++ == 0) BluetoothDevice.BOND_BONDING else BluetoothDevice.BOND_BONDED

        @Implementation
        override fun createBond(): Boolean {
            createBondCalls++
            return false
        }
    }
}
