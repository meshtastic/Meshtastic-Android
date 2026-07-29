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

import com.juul.kable.Advertisement
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 0 dBm is the strongest value on the RSSI scale, so a bonded-only device with no advertisement must report `null`
 * rather than collapse into an excellent-signal reading. Both cases are pinned together.
 */
class MeshtasticBleDeviceRssiTest {

    @Test
    fun `advertised zero rssi is reported`() = runTest {
        val advertisement: Advertisement = mock(MockMode.autofill) { every { rssi } returns 0 }
        val device = MeshtasticBleDevice(address = ADDRESS, advertisement = advertisement)

        assertEquals(0, device.rssi)
        assertEquals(0, device.readRssi())
    }

    @Test
    fun `bonded-only device without an advertisement reports no rssi`() = runTest {
        val device = MeshtasticBleDevice(address = ADDRESS)

        assertNull(device.rssi)
        assertNull(device.readRssi())
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
