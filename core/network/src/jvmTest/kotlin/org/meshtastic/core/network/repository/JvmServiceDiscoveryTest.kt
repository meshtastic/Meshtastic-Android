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
package org.meshtastic.core.network.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmServiceDiscoveryTest {

    private val testDispatchers =
        UnconfinedTestDispatcher().let { dispatcher ->
            CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)
        }

    @Test
    fun `resolvedServices emits initial empty list immediately`() = runTest {
        val discovery = JvmServiceDiscovery(testDispatchers)
        discovery.resolvedServices.test {
            val first = awaitItem()
            assertNotNull(first, "First emission should not be null")
            assertTrue(first.isEmpty(), "First emission should be an empty list")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findLanAddress returns non-loopback address or null`() {
        val address = JvmServiceDiscovery.findLanAddress()
        // On CI machines there may be no LAN interface, so null is acceptable
        if (address != null) {
            assertTrue(!address.isLoopbackAddress, "Address should not be loopback")
            assertTrue(address is java.net.Inet4Address, "Address should be IPv4")
        }
    }

    @Test
    fun `findLanAddress does not throw`() {
        // Ensure the method handles exceptions gracefully
        val result = runCatching { JvmServiceDiscovery.findLanAddress() }
        assertTrue(result.isSuccess, "findLanAddress should not throw: ${result.exceptionOrNull()}")
    }
}
