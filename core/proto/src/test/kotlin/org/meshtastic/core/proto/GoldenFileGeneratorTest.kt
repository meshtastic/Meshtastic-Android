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

import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import java.io.File

class GoldenFileGeneratorTest {

    @Test
    fun generateGoldenFiles() {
        // We write to the source tree so they are committed
        val outputDir = File("src/test/resources/golden")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // 1. Simple MeshPacket (Text)
        val simplePacket = MeshPacket(
            from = 123456789,
            to = 987654321,
            decoded = Data(
                payload = "Hello World".encodeToByteArray().toByteString(),
                portnum = PortNum.TEXT_MESSAGE_APP
            ),
            id = 1001,
            rx_time = 1678888888
        )
        
        File(outputDir, "mesh_packet_simple.bin").writeBytes(simplePacket.encode())

        // 2. Complex MeshPacket (Encrypted, Priority, HopLimit)
        val complexPacket = MeshPacket(
            from = 111,
            to = 222,
            encrypted = byteArrayOf(0x01, 0x02, 0x03, 0x04).toByteString(),
            priority = MeshPacket.Priority.RELIABLE,
            hop_limit = 3,
            channel = 1,
            want_ack = true
        )

        File(outputDir, "mesh_packet_complex.bin").writeBytes(complexPacket.encode())

        // 3a. Config (Device)
        val deviceConfig = Config(
            device = Config.DeviceConfig(
                role = Config.DeviceConfig.Role.CLIENT,
                button_gpio = 12,
                buzzer_gpio = 13
            )
        )

        File(outputDir, "config_device.bin").writeBytes(deviceConfig.encode())

        // 3b. Config (Display)
        val displayConfig = Config(
            display = Config.DisplayConfig(
                screen_on_secs = 300
            )
        )
        
        File(outputDir, "config_display.bin").writeBytes(displayConfig.encode())
        
        // 4. User Profile
        val user = User(
            id = "!12345678",
            long_name = "Test User",
            short_name = "TU",
            macaddr = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()).toByteString(),
            hw_model = HardwareModel.TBEAM
        )
            
        File(outputDir, "user_profile.bin").writeBytes(user.encode())
        
        println("Generated golden files in ${outputDir.absolutePath}")
    }
}
