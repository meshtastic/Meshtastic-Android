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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HardwareSupportTierTest {

    private fun hardware(activelySupported: Boolean, isMaker: Boolean, supportLevel: Int?) =
        DeviceHardware(activelySupported = activelySupported, isMaker = isMaker, supportLevel = supportLevel)

    @Test
    fun `actively supported is the top rung whatever the other flags say`() {
        assertEquals(HardwareSupportTier.SUPPORTED, hardware(true, false, 1).supportTier)
        assertEquals(HardwareSupportTier.SUPPORTED, hardware(true, true, 1).supportTier)
        assertEquals(HardwareSupportTier.SUPPORTED, hardware(true, false, 3).supportTier)
        assertEquals(HardwareSupportTier.SUPPORTED, hardware(true, false, null).supportTier)
    }

    @Test
    fun `a flagship or niche maker board is maker`() {
        assertEquals(HardwareSupportTier.MAKER, hardware(false, true, 1).supportTier)
        assertEquals(HardwareSupportTier.MAKER, hardware(false, true, 2).supportTier)
    }

    @Test
    fun `a legacy or untiered maker board is community`() {
        assertEquals(HardwareSupportTier.COMMUNITY, hardware(false, true, 3).supportTier)
        assertEquals(HardwareSupportTier.COMMUNITY, hardware(false, true, null).supportTier)
    }

    @Test
    fun `neither flag is community`() {
        assertEquals(HardwareSupportTier.COMMUNITY, hardware(false, false, 1).supportTier)
        assertEquals(HardwareSupportTier.COMMUNITY, hardware(false, false, null).supportTier)
    }

    @Test
    fun `isMaker defaults to false so an absent key reads as not maker`() {
        assertEquals(false, DeviceHardware().isMaker)
        assertEquals(false, NetworkDeviceHardware().isMaker)
    }
}
