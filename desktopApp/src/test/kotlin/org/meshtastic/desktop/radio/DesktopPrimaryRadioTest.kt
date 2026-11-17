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
package org.meshtastic.desktop.radio

import org.meshtastic.core.network.radio.PrimaryRadio
import org.meshtastic.node.host.ExtraRadioDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPrimaryRadioTest {

    @Test
    fun `a board config path names a board by its file`() {
        assertEquals(
            PrimaryRadio(ExtraRadioDevice.BOARD, "lora-aio"),
            primaryRadioFor("/home/james/.local/share/meshtastic-desktop/lora-aio.yaml", spidev = null),
        )
    }

    @Test
    fun `a bundled board config name is a board`() {
        assertEquals(
            PrimaryRadio(ExtraRadioDevice.BOARD, "lora-RAK6421-13300-slot1"),
            primaryRadioFor("lora-RAK6421-13300-slot1", spidev = "/dev/spidev0.0"),
        )
    }

    @Test
    fun `no board config is the default USB stick with no id`() {
        assertEquals(PrimaryRadio(ExtraRadioDevice.USB_STICK), primaryRadioFor(null, spidev = null))
        assertEquals(PrimaryRadio(ExtraRadioDevice.USB_STICK), primaryRadioFor("  ", spidev = ""))
    }

    @Test
    fun `a kernel bus with no board config opens no radio and says nothing`() {
        assertNull(primaryRadioFor(null, spidev = "/dev/spidev1.0"))
    }
}
