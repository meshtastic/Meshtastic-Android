/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.proto

import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import java.io.File

class GoldenFileVerificationTest {

    private val goldenDir = File("src/test/resources/golden")

    @Test
    fun verifySimplePacket() {
        val file = File(goldenDir, "mesh_packet_simple.bin")
        val packet = MeshPacket.ADAPTER.decode(file.readBytes())

        assertEquals(123456789, packet.from)
        assertEquals(987654321, packet.to)
        assertEquals(1001, packet.id)
        assertEquals(1678888888, packet.rx_time)
        
        val data = packet.decoded!!
        assertEquals("Hello World", data.payload.utf8())
        assertEquals(PortNum.TEXT_MESSAGE_APP, data.portnum)
    }

    @Test
    fun verifyComplexPacket() {
        val file = File(goldenDir, "mesh_packet_complex.bin")
        val packet = MeshPacket.ADAPTER.decode(file.readBytes())

        assertEquals(111, packet.from)
        assertEquals(222, packet.to)
        assertEquals(MeshPacket.Priority.RELIABLE, packet.priority)
        assertEquals(3, packet.hop_limit)
        assertEquals(1, packet.channel)
        assertEquals(true, packet.want_ack)
        
        val encrypted = packet.encrypted!!
        assertEquals(4, encrypted.size)
        assertEquals(0x01.toByte(), encrypted[0])
    }

    @Test
    fun verifyDeviceConfig() {
        val file = File(goldenDir, "config_device.bin")
        val config = Config.ADAPTER.decode(file.readBytes())

        assertEquals(Config.DeviceConfig.Role.CLIENT, config.device?.role)
        assertEquals(12, config.device?.button_gpio)
        assertEquals(13, config.device?.buzzer_gpio)
    }

    @Test
    fun verifyDisplayConfig() {
        val file = File(goldenDir, "config_display.bin")
        val config = Config.ADAPTER.decode(file.readBytes())
        
        assertEquals(300, config.display?.screen_on_secs)
    }

    @Test
    fun verifyUser() {
        val file = File(goldenDir, "user_profile.bin")
        val user = User.ADAPTER.decode(file.readBytes())

        assertEquals("!12345678", user.id)
        assertEquals("Test User", user.long_name)
        assertEquals("TU", user.short_name)
        assertEquals(HardwareModel.TBEAM, user.hw_model)
        
        // MacAddr is deprecated but we set it
        assertEquals(4, user.macaddr?.size ?: 0)
        assertEquals(0xAA.toByte(), user.macaddr?.get(0))
    }
}
