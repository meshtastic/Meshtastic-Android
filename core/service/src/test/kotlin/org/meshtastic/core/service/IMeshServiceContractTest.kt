/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.core.service.testing.FakeIMeshService

/** Test to verify that the AIDL contract is correctly implemented by our test harness. */
class IMeshServiceContractTest {

    @Test
    fun `verify fake implementation matches aidl contract`() {
        val service: IMeshService = FakeIMeshService()

        // Basic verification that we can call methods and get expected results
        assertEquals("fake_id", service.myId)
        assertEquals(1234, service.packetId)
        assertEquals("CONNECTED", service.connectionState())
        assertNotNull(service.nodes)
    }
}
