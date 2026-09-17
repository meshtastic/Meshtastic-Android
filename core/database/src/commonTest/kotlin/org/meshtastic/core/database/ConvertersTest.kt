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
package org.meshtastic.core.database

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.Position
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `data packet string converter round trips`() {
        val packet =
            DataPacket(
                to = NodeAddress.ID_BROADCAST,
                bytes = "hello mesh".encodeToByteArray().toByteString(),
                dataType = 1,
                from = "!12345678",
                id = 42,
                status = MessageStatus.DELIVERED,
                hopLimit = 3,
                channel = 2,
                wantAck = false,
                rssi = -80,
            )

        val encoded = converters.dataToString(packet)
        val decoded = converters.dataFromString(encoded)

        assertEquals(packet, decoded)
    }

    @Test
    fun `from radio converter round trips`() {
        assertProtoRoundTrip(
            expected =
            FromRadio.Builder()
                .also { wb ->
                    wb.queueStatus =
                        QueueStatus.Builder()
                            .also { wb ->
                                wb.res = 1
                                wb.free = 2
                                wb.mesh_packet_id = 3
                            }
                            .build()
                }
                .build(),
            toBytes = converters::fromRadioToBytes,
            fromBytes = converters::bytesToFromRadio,
        )
    }

    @Test
    fun `user converter round trips`() {
        assertProtoRoundTrip(
            expected =
            User.Builder()
                .also { wb ->
                    wb.id = "!abcdef01"
                    wb.long_name = "Test User"
                    wb.short_name = "TU"
                    wb.hw_model = HardwareModel.TBEAM
                }
                .build(),
            toBytes = converters::userToBytes,
            fromBytes = converters::bytesToUser,
        )
    }

    @Test
    fun `position converter round trips`() {
        assertProtoRoundTrip(
            expected =
            Position.Builder()
                .also { wb ->
                    wb.latitude_i = 450000000
                    wb.longitude_i = 900000000
                    wb.altitude = 123
                    wb.time = 456
                    wb.sats_in_view = 7
                }
                .build(),
            toBytes = converters::positionToBytes,
            fromBytes = converters::bytesToPosition,
        )
    }

    @Test
    fun `telemetry converter round trips`() {
        assertProtoRoundTrip(
            expected =
            Telemetry.Builder()
                .also { wb ->
                    wb.time = 1000
                    wb.device_metrics =
                        DeviceMetrics.Builder()
                            .also { wb ->
                                wb.battery_level = 85
                                wb.voltage = 4.1f
                                wb.channel_utilization = 0.12f
                                wb.air_util_tx = 0.05f
                                wb.uptime_seconds = 123456
                            }
                            .build()
                }
                .build(),
            toBytes = converters::telemetryToBytes,
            fromBytes = converters::bytesToTelemetry,
        )
    }

    @Test
    fun `paxcount converter round trips`() {
        assertProtoRoundTrip(
            expected =
            Paxcount.Builder()
                .also { wb ->
                    wb.wifi = 10
                    wb.ble = 5
                    wb.uptime = 1000
                }
                .build(),
            toBytes = converters::paxCounterToBytes,
            fromBytes = converters::bytesToPaxcounter,
        )
    }

    @Test
    fun `device metadata converter round trips`() {
        assertProtoRoundTrip(
            expected =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.firmware_version = "2.5.0"
                    wb.hw_model = HardwareModel.HELTEC_V3
                    wb.hasWifi = false
                }
                .build(),
            toBytes = converters::metadataToBytes,
            fromBytes = converters::bytesToMetadata,
        )
    }

    @Test
    fun `empty proto messages round trip to empty defaults`() {
        assertEquals(
            FromRadio.Builder().build(),
            converters.bytesToFromRadio(converters.fromRadioToBytes(FromRadio.Builder().build())),
        )
        assertEquals(User.Builder().build(), converters.bytesToUser(converters.userToBytes(User.Builder().build())))
        assertEquals(
            Position.Builder().build(),
            converters.bytesToPosition(converters.positionToBytes(Position.Builder().build())),
        )
        assertEquals(
            Telemetry.Builder().build(),
            converters.bytesToTelemetry(converters.telemetryToBytes(Telemetry.Builder().build())),
        )
        assertEquals(
            Paxcount.Builder().build(),
            converters.bytesToPaxcounter(converters.paxCounterToBytes(Paxcount.Builder().build())),
        )
        assertEquals(
            DeviceMetadata.Builder().build(),
            converters.bytesToMetadata(converters.metadataToBytes(DeviceMetadata.Builder().build())),
        )
    }

    @Test
    fun `empty byte arrays decode to empty proto messages`() {
        val emptyBytes = byteArrayOf()

        assertEquals(FromRadio.Builder().build(), converters.bytesToFromRadio(emptyBytes))
        assertEquals(User.Builder().build(), converters.bytesToUser(emptyBytes))
        assertEquals(Position.Builder().build(), converters.bytesToPosition(emptyBytes))
        assertEquals(Telemetry.Builder().build(), converters.bytesToTelemetry(emptyBytes))
        assertEquals(Paxcount.Builder().build(), converters.bytesToPaxcounter(emptyBytes))
        assertEquals(DeviceMetadata.Builder().build(), converters.bytesToMetadata(emptyBytes))
    }

    @Test
    fun `string list converter round trips and handles null`() {
        val value = listOf("alpha", "beta")

        val encoded = converters.toStringList(value)
        assertNotNull(encoded)
        assertEquals(value, converters.fromStringList(encoded))
        assertNull(converters.toStringList(null))
        assertNull(converters.fromStringList(null))
    }

    @Test
    fun `byte string converter round trips and handles null`() {
        val value = byteArrayOf(1, 2, 3, 4).toByteString()

        val encoded = converters.byteStringToBytes(value)
        assertNotNull(encoded)
        assertEquals(value, converters.bytesToByteString(encoded))
        assertNull(converters.byteStringToBytes(null))
        assertNull(converters.bytesToByteString(null))
    }

    @Test
    fun `empty byte arrays round trip as empty byte strings`() {
        val emptyByteString = ByteString.EMPTY

        val encoded = converters.byteStringToBytes(emptyByteString)
        assertNotNull(encoded)
        assertEquals(0, encoded.size)
        assertEquals(emptyByteString, converters.bytesToByteString(byteArrayOf()))
    }

    @Test
    fun `message status converter round trips and defaults unknown`() {
        assertEquals(
            MessageStatus.DELIVERED,
            converters.intToMessageStatus(converters.messageStatusToInt(MessageStatus.DELIVERED)),
        )
        assertEquals(MessageStatus.UNKNOWN.ordinal, converters.messageStatusToInt(null))
        assertEquals(MessageStatus.UNKNOWN, converters.intToMessageStatus(-1))
    }

    private fun <T> assertProtoRoundTrip(expected: T, toBytes: (T) -> ByteArray, fromBytes: (ByteArray) -> T) {
        val encoded = toBytes(expected)
        val decoded = fromBytes(encoded)

        assertEquals(expected, decoded)
    }
}
