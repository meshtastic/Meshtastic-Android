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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.proto.Config.NetworkConfig.ProtocolFlags
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolFlagSwitchesTest {

    @Test
    fun everyNonZeroFlagBecomesOneSwitchInSchemaOrder() {
        val switches = protocolFlagSwitches(enabled = 0)

        assertEquals(ProtocolFlags.entries.filter { it.value != 0 }, switches.map { it.flag })
        assertEquals(switches.map { false }, switches.map { it.checked })
    }

    @Test
    fun aSetBitReadsAsChecked() {
        val switches = protocolFlagSwitches(enabled = ProtocolFlags.UDP_BROADCAST.value)

        assertEquals(true, switches.single { it.flag == ProtocolFlags.UDP_BROADCAST }.checked)
        assertEquals(emptyList(), switches.filter { it.flag != ProtocolFlags.UDP_BROADCAST && it.checked })
    }

    @Test
    fun togglingTouchesOnlyItsOwnBit() {
        val others = 0b1000

        assertEquals(others or 1, toggleFlag(current = others, flag = ProtocolFlags.UDP_BROADCAST, on = true))
        assertEquals(others, toggleFlag(current = others or 1, flag = ProtocolFlags.UDP_BROADCAST, on = false))
        assertEquals(others or 1, toggleFlag(current = others or 1, flag = ProtocolFlags.UDP_BROADCAST, on = true))
    }
}
