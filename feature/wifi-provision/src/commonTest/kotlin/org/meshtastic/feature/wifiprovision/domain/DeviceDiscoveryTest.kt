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
package org.meshtastic.feature.wifiprovision.domain

import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDiscoveryTest {
    private val firstSeen = FakeBleDevice("00:00:00:00:00:0A", name = "first seen", rssi = -80)
    private val strongest = FakeBleDevice("00:00:00:00:00:0B", name = "strongest", rssi = -50)
    private val chosen = FakeBleDevice("00:00:00:00:00:0C", name = "chosen", rssi = -65)

    @Test
    fun `devices are ordered strongest first with unknown signal last`() {
        val unknown = FakeBleDevice("00:00:00:00:00:0D", name = "unknown", rssi = null)
        val zeroDbm = FakeBleDevice("00:00:00:00:00:0E", name = "zero", rssi = 0)

        val listed =
            listOf(unknown, firstSeen, strongest, zeroDbm, chosen).fold(emptyList<DiscoveredDevice>()) { list, device ->
                DeviceDiscovery.upsertByStrength(list, device)
            }

        assertEquals(
            listOf(zeroDbm.address, strongest.address, chosen.address, firstSeen.address, unknown.address),
            listed.map { it.address },
        )
    }

    @Test
    fun `a repeated advertisement refreshes its row without moving it`() {
        val listed =
            listOf(strongest, firstSeen).fold(emptyList<DiscoveredDevice>()) { list, device ->
                DeviceDiscovery.upsertByStrength(list, device)
            }

        val refreshed =
            DeviceDiscovery.upsertByStrength(listed, FakeBleDevice(firstSeen.address, name = null, rssi = -40))

        assertEquals(listOf(strongest.address, firstSeen.address), refreshed.map { it.address })
        assertEquals(-40, refreshed[1].rssi)
        assertEquals("first seen", refreshed[1].name)
    }
}
