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
package org.meshtastic.core.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.ble.CompanionAssociationRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reconciliation rules for [CompanionPresenceCoordinator]: presence observation follows the selected, associated BLE
 * radio and nothing else — old registrations retire on selection change or deselection, and associations that arrive
 * later (revision bump) are picked up.
 *
 * Robolectric supplies only the Context the repository fake's constructor needs; every CDM interaction is recorded by
 * the fake, so the tests are deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionPresenceCoordinatorTest {

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
        const val OTHER_MAC = "11:22:33:44:55:66"
        const val SDK_34 = 34
    }

    /** Records observation calls; association truth is the [associated] set plus the inherited revision flow. */
    private class RecordingRepository : CompanionAssociationRepository(RuntimeEnvironment.getApplication()) {
        val associated = mutableSetOf<String>()
        val startCalls = mutableListOf<String>()
        val stopCalls = mutableListOf<String>()

        override val associationsRevision = MutableStateFlow(0)

        override fun hasAssociationFor(mac: String): Boolean = associated.any { it.equals(mac, ignoreCase = true) }

        override fun startObservingPresence(mac: String) {
            startCalls += mac
        }

        override fun stopObservingPresence(mac: String) {
            stopCalls += mac
        }
    }

    private fun TestScope.startCoordinator(
        address: MutableStateFlow<String?>,
        repository: RecordingRepository,
        sdkInt: Int = SDK_34,
    ) = CompanionPresenceCoordinator(
        selectedAddressFlow = address,
        repository = repository,
        scope = backgroundScope,
        sdkInt = sdkInt,
    )
        .start()

    @Test
    fun `observes the selected associated radio on start`() = runTest(UnconfinedTestDispatcher()) {
        val repository = RecordingRepository().apply { associated += MAC }
        val address = MutableStateFlow<String?>("x$MAC")

        startCoordinator(address, repository)

        assertEquals(listOf(MAC), repository.startCalls)
        assertEquals(emptyList(), repository.stopCalls)
    }

    @Test
    fun `selection change retires the old registration and observes the new radio`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = RecordingRepository().apply { associated += listOf(MAC, OTHER_MAC) }
            val address = MutableStateFlow<String?>("x$MAC")
            startCoordinator(address, repository)

            address.value = "x$OTHER_MAC"

            assertEquals(listOf(MAC, OTHER_MAC), repository.startCalls)
            assertEquals(listOf(MAC), repository.stopCalls)
        }

    @Test
    fun `deselection retires the registration`() = runTest(UnconfinedTestDispatcher()) {
        val repository = RecordingRepository().apply { associated += MAC }
        val address = MutableStateFlow<String?>("x$MAC")
        startCoordinator(address, repository)

        address.value = null

        assertEquals(listOf(MAC), repository.startCalls)
        assertEquals(listOf(MAC), repository.stopCalls)
    }

    @Test
    fun `a radio without an association is not observed until one appears`() = runTest(UnconfinedTestDispatcher()) {
        val repository = RecordingRepository()
        val address = MutableStateFlow<String?>("x$MAC")
        startCoordinator(address, repository)

        assertEquals(emptyList(), repository.startCalls)

        // The user confirms the CDM chooser: association lands, revision bumps, observation follows.
        repository.associated += MAC
        repository.associationsRevision.value += 1

        assertEquals(listOf(MAC), repository.startCalls)
    }

    @Test
    fun `non-BLE selections are never observed`() = runTest(UnconfinedTestDispatcher()) {
        val repository = RecordingRepository().apply { associated += MAC }
        val address = MutableStateFlow<String?>("t192.168.1.10")

        startCoordinator(address, repository)

        assertEquals(emptyList(), repository.startCalls)
    }

    @Test
    fun `inert before API 31`() = runTest(UnconfinedTestDispatcher()) {
        val repository = RecordingRepository().apply { associated += MAC }
        val address = MutableStateFlow<String?>("x$MAC")

        startCoordinator(address, repository, sdkInt = 30)

        assertEquals(emptyList(), repository.startCalls)
    }
}
