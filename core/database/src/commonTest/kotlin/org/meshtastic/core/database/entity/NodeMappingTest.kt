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
package org.meshtastic.core.database.entity

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.SoilWaterMetrics
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A [Node] with every constructor property set away from its default, so a dropped field changes equality. */
internal fun fullyPopulatedNode(): Node {
    val key = ByteArray(32) { (it + 1).toByte() }.toByteString()
    val refusedKey = ByteArray(32) { (it + 101).toByte() }.toByteString()
    return Node(
        num = 0x1234ABCD,
        metadata =
        DeviceMetadata.Builder()
            .also { wb ->
                wb.firmware_version = "2.8.1.abcdef"
                wb.hw_model = HardwareModel.HELTEC_V3
            }
            .build(),
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!1234abcd"
                wb.long_name = "Fixture Node"
                wb.short_name = "FXN"
                wb.hw_model = HardwareModel.HELTEC_V3
                wb.public_key = key
            }
            .build(),
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = 525_200_000
                wb.longitude_i = 134_050_000
                wb.altitude = 34
                wb.time = 1_700_000_000
            }
            .build(),
        snr = 5.5f,
        rssi = -91,
        lastHeard = 1_700_000_100,
        deviceMetrics =
        DeviceMetrics.Builder()
            .also { wb ->
                wb.battery_level = 80
                wb.voltage = 4.01f
            }
            .build(),
        channel = 2,
        viaMqtt = true,
        hopsAway = 3,
        isFavorite = true,
        isIgnored = true,
        isMuted = true,
        environmentMetrics = EnvironmentMetrics.Builder().also { wb -> wb.temperature = 21.5f }.build(),
        powerMetrics = PowerMetrics.Builder().also { wb -> wb.ch1_voltage = 12.6f }.build(),
        airQualityMetrics = AirQualityMetrics.Builder().also { wb -> wb.co2 = 450 }.build(),
        soilWaterMetrics = SoilWaterMetrics.Builder().also { wb -> wb.soil_ph = 6.8f }.build(),
        paxcounter = Paxcount.Builder().also { wb -> wb.ble = 3 }.build(),
        publicKey = key,
        notes = "fixture notes",
        powerChannelLabels = listOf("Solar", "Battery"),
        manuallyVerified = true,
        signsPackets = true,
        heardOnCurrentLora = false,
        nodeStatus = "on duty",
        lastTransport = 1,
        keyMatch = false,
        newPublicKey = refusedKey,
    )
}

class NodeMappingTest {

    @Test
    fun `every node field survives a round trip through the entity`() {
        val node = fullyPopulatedNode()

        assertEquals(node, node.toEntity().toModel(metadata = node.metadata))
    }

    @Test
    fun `a joined row carries metadata and manual verification into the model`() {
        val node = fullyPopulatedNode()
        val row =
            NodeWithRelations(
                node = node.toEntity(),
                metadata = MetadataEntity(num = node.num, proto = checkNotNull(node.metadata)),
            )

        assertEquals(node, row.toModel())
    }

    @Test
    fun `an entity read without its metadata join keeps manual verification`() {
        val entity = fullyPopulatedNode().toEntity()

        assertTrue(entity.toModel().manuallyVerified)
    }

    @Test
    fun `power channel labels reach the entity`() {
        assertEquals(listOf("Solar", "Battery"), fullyPopulatedNode().toEntity().powerChannelLabels)
    }
}
