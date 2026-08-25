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

import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for [CompanionAssociationRepository]'s two platform forks — the deprecated string-based association API (API
 * 26-32) and the `AssociationInfo` API (33+) — plus the guards around them: the `FEATURE_COMPANION_DEVICE_SETUP`
 * availability gate and the bond-reconciliation rule that an association may only be dropped on *positive* knowledge of
 * an unpair (adapter ON and the device absent from the bonded set).
 *
 * Per-test `@Config(sdk = ...)` pins each fork: 31/32 exercise the legacy branch on restricted-start levels, 34 the
 * modern branch.
 */
@RunWith(RobolectricTestRunner::class)
class CompanionAssociationRepositoryTest {

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
        const val OTHER_MAC = "11:22:33:44:55:66"
    }

    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    private fun newRepository(featurePresent: Boolean = true): CompanionAssociationRepository {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP, featurePresent)
        return CompanionAssociationRepository(context)
    }

    private val companionDeviceManager: CompanionDeviceManager
        get() = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

    /** Puts [mac] in the adapter's bonded set with the adapter ON, the state where reconciliation trusts the set. */
    private fun bond(mac: String) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        shadowOf(adapter).setBondedDevices(setOf(adapter.getRemoteDevice(mac)))
    }

    private fun setAdapterEnabled(enabled: Boolean) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(enabled)
    }

    // region API fork: legacy (26-32) vs AssociationInfo (33+)

    @Test
    @Config(sdk = [31])
    fun `legacy fork sees and removes a string association`() {
        val repository = newRepository()
        bond(MAC)
        shadowOf(companionDeviceManager).addAssociation(MAC)

        assertTrue(repository.hasAssociationFor(MAC))
        assertFalse(repository.hasAssociationFor(OTHER_MAC))

        repository.disassociate(MAC)
        assertFalse(repository.hasAssociationFor(MAC))
    }

    @Test
    @Config(sdk = [34])
    fun `AssociationInfo fork sees and removes an association by id`() {
        val repository = newRepository()
        bond(MAC)
        shadowOf(companionDeviceManager).addAssociation(MAC)

        assertTrue(repository.hasAssociationFor(MAC))
        assertFalse(repository.hasAssociationFor(OTHER_MAC))

        repository.disassociate(MAC)
        assertFalse(repository.hasAssociationFor(MAC))
        assertTrue(companionDeviceManager.myAssociations.isEmpty())
    }

    @Test
    @Config(sdk = [34])
    fun `association mac comparison is case-insensitive`() {
        val repository = newRepository()
        bond(MAC)
        shadowOf(companionDeviceManager).addAssociation(MAC.lowercase())

        assertTrue(repository.hasAssociationFor(MAC))
    }

    // endregion

    // region availability guard

    @Test
    @Config(sdk = [34])
    fun `without the companion feature everything is an inert no-op`() {
        val repository = newRepository(featurePresent = false)
        bond(MAC)

        assertFalse(repository.hasAssociationFor(MAC))
        repository.associate(MAC)
        assertNull(shadowOf(companionDeviceManager).lastAssociationRequest)
        repository.disassociate(MAC) // must not throw
    }

    // endregion

    // region associate() chooser flow

    @Test
    @Config(sdk = [32])
    fun `associate builds a single-device request and emits the chooser on API 26-32`() = runTest {
        val repository = newRepository()
        bond(MAC)

        repository.associate(MAC)

        val shadow = shadowOf(companionDeviceManager)
        assertNotNull(shadow.lastAssociationRequest, "associate() should reach CompanionDeviceManager")
        assertTrue(shadow.lastAssociationRequest.isSingleDevice)

        // The platform hands the chooser back through the callback; the repository must surface it to the activity.
        val received = collectChoosers(repository)
        @Suppress("DEPRECATION")
        shadow.lastAssociationCallback.onDeviceFound(chooserIntentSender())
        testScheduler.runCurrent()
        assertEquals(1, received.size)
    }

    @Test
    @Config(sdk = [34])
    fun `associate emits the chooser from onAssociationPending on API 33+`() = runTest {
        val repository = newRepository()
        bond(MAC)

        repository.associate(MAC)

        val shadow = shadowOf(companionDeviceManager)
        assertNotNull(shadow.lastAssociationRequest)

        val received = collectChoosers(repository)
        shadow.lastAssociationCallback.onAssociationPending(chooserIntentSender())
        testScheduler.runCurrent()
        assertEquals(1, received.size)
    }

    /**
     * Collects [CompanionAssociationRepository.chooserRequests] into the returned list. The scheduler is run so the
     * subscription is live before the caller fires the platform callback — the flow has no replay, so an emission
     * before subscription would be dropped and the test would hang instead of failing crisply.
     */
    private fun TestScope.collectChoosers(repository: CompanionAssociationRepository): List<IntentSender> {
        val received = mutableListOf<IntentSender>()
        backgroundScope.launch { repository.chooserRequests.collect { received += it } }
        testScheduler.runCurrent()
        return received
    }

    @Test
    @Config(sdk = [34])
    fun `associate is a no-op when an association already exists`() {
        val repository = newRepository()
        bond(MAC)
        shadowOf(companionDeviceManager).addAssociation(MAC)

        repository.associate(MAC)

        assertNull(shadowOf(companionDeviceManager).lastAssociationRequest)
    }

    private fun chooserIntentSender() =
        PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE).intentSender

    // endregion

    // region bond reconciliation ("disassociate when the radio is removed")

    @Test
    @Config(sdk = [34])
    fun `an association whose device was unpaired is dropped`() {
        val repository = newRepository()
        shadowOf(companionDeviceManager).addAssociation(MAC)
        // Adapter ON with MAC absent from the bonded set = positive knowledge the user unpaired the radio.
        bond(OTHER_MAC)

        assertFalse(repository.hasAssociationFor(MAC))
        assertTrue(companionDeviceManager.myAssociations.isEmpty(), "the stale association should be disassociated")
    }

    @Test
    @Config(sdk = [34])
    fun `an association is kept while the adapter is off`() {
        val repository = newRepository()
        shadowOf(companionDeviceManager).addAssociation(MAC)
        // A disabled adapter reports an EMPTY bonded set; that must never count as an unpair.
        setAdapterEnabled(false)

        assertTrue(repository.hasAssociationFor(MAC))
        assertEquals(1, companionDeviceManager.myAssociations.size)
    }

    // endregion
}
