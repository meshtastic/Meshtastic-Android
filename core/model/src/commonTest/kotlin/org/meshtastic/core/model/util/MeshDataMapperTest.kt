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
package org.meshtastic.core.model.util

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.core.model.DeviceMetrics as DomainDeviceMetrics
import org.meshtastic.core.model.EnvironmentMetrics as DomainEnvironmentMetrics
import org.meshtastic.core.model.Position as DomainPosition

class MeshDataMapperTest {

    private val mapper = MeshDataMapper(TestNodeIdLookup())

    @Test
    fun toDataPacket_returnsNullWhenPacketHasNoDecodedData() {
        assertNull(mapper.toDataPacket(MeshPacket(from = 0x12345678)))
    }

    @Test
    fun toDataPacket_mapsMeshPacketFields() {
        val payload = "mesh payload".encodeUtf8()
        val packet =
            MeshPacket(
                from = 0x12345678,
                to = 0x90ABCDEF.toInt(),
                rx_time = 123,
                id = 456,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = payload, reply_id = 789, emoji = 321),
                hop_limit = 3,
                channel = 4,
                want_ack = true,
                hop_start = 5,
                rx_snr = 6.5f,
                rx_rssi = -70,
                relay_node = 77,
                via_mqtt = true,
                transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_MQTT,
            )

        val mapped = mapper.toDataPacket(packet)

        assertNotNull(mapped)
        assertEquals("!12345678", mapped.from)
        assertEquals("!90abcdef", mapped.to)
        assertEquals(123_000L, mapped.time)
        assertEquals(456, mapped.id)
        assertEquals(PortNum.TEXT_MESSAGE_APP.value, mapped.dataType)
        assertEquals(payload, mapped.bytes)
        assertEquals(3, mapped.hopLimit)
        assertEquals(4, mapped.channel)
        assertTrue(mapped.wantAck)
        assertEquals(5, mapped.hopStart)
        assertEquals(6.5f, mapped.snr)
        assertEquals(-70, mapped.rssi)
        assertEquals(789, mapped.replyId)
        assertEquals(77, mapped.relayNode)
        assertTrue(mapped.viaMqtt)
        assertEquals(321, mapped.emoji)
        assertEquals(MeshPacket.TransportMechanism.TRANSPORT_MQTT.value, mapped.transportMechanism)
    }

    @Test
    fun toDataPacket_usesPkcChannelWhenPacketIsPkiEncrypted() {
        val packet =
            MeshPacket(
                from = 1,
                to = 2,
                channel = 2,
                pki_encrypted = true,
                decoded = Data(portnum = PortNum.PRIVATE_APP),
            )

        val mapped = mapper.toDataPacket(packet)

        assertNotNull(mapped)
        assertEquals(DataPacket.PKC_CHANNEL_INDEX, mapped.channel)
    }

    @Test
    fun meshUser_mapsProtoFields() {
        val proto =
            User(
                id = "!cafebabe",
                long_name = "Meshtastic User",
                short_name = "MU",
                hw_model = HardwareModel.TLORA_V2,
                is_licensed = true,
                role = Config.DeviceConfig.Role.ROUTER,
            )

        val user = MeshUser(proto)

        assertEquals("!cafebabe", user.id)
        assertEquals("Meshtastic User", user.longName)
        assertEquals("MU", user.shortName)
        assertEquals(HardwareModel.TLORA_V2, user.hwModel)
        assertTrue(user.isLicensed)
        assertEquals(Config.DeviceConfig.Role.ROUTER.value, user.role)
    }

    @Test
    fun meshUser_defaultsEmptyFieldsFromEmptyProto() {
        val user = MeshUser(User())

        assertEquals("", user.id)
        assertEquals("", user.longName)
        assertEquals("", user.shortName)
        assertEquals(HardwareModel.UNSET, user.hwModel)
        assertFalse(user.isLicensed)
        assertEquals(0, user.role)
    }

    @Test
    fun position_mapsScaledCoordinatesAndProvidedTime() {
        val proto =
            Position(
                latitude_i = 377749000,
                longitude_i = -1224194000,
                altitude = 15,
                time = 456,
                sats_in_view = 9,
                ground_speed = 12,
                ground_track = 180,
                precision_bits = 7,
            )

        val position = DomainPosition(proto, defaultTime = 123)

        assertEquals(37.7749, position.latitude, 1e-6)
        assertEquals(-122.4194, position.longitude, 1e-6)
        assertEquals(15, position.altitude)
        assertEquals(456, position.time)
        assertEquals(9, position.satellitesInView)
        assertEquals(12, position.groundSpeed)
        assertEquals(180, position.groundTrack)
        assertEquals(7, position.precisionBits)
    }

    @Test
    fun position_usesDefaultTimeAndZeroValuesForUnsetProtoFields() {
        val position = DomainPosition(Position(), defaultTime = 789)

        assertEquals(0.0, position.latitude)
        assertEquals(0.0, position.longitude)
        assertEquals(0, position.altitude)
        assertEquals(789, position.time)
        assertEquals(0, position.satellitesInView)
        assertEquals(0, position.groundSpeed)
        assertEquals(0, position.groundTrack)
        assertEquals(0, position.precisionBits)
    }

    @Test
    fun deviceMetrics_mapsProtoFields() {
        val proto =
            DeviceMetrics(
                battery_level = 87,
                voltage = 4.12f,
                channel_utilization = 32.5f,
                air_util_tx = 7.75f,
                uptime_seconds = 3600,
            )

        val metrics = DomainDeviceMetrics(proto, telemetryTime = 123)

        assertEquals(123, metrics.time)
        assertEquals(87, metrics.batteryLevel)
        assertEquals(4.12f, metrics.voltage)
        assertEquals(32.5f, metrics.channelUtilization)
        assertEquals(7.75f, metrics.airUtilTx)
        assertEquals(3600, metrics.uptimeSeconds)
    }

    @Test
    fun deviceMetrics_defaultsUnsetFieldsToZero() {
        val metrics = DomainDeviceMetrics(DeviceMetrics(), telemetryTime = 222)

        assertEquals(222, metrics.time)
        assertEquals(0, metrics.batteryLevel)
        assertEquals(0f, metrics.voltage)
        assertEquals(0f, metrics.channelUtilization)
        assertEquals(0f, metrics.airUtilTx)
        assertEquals(0, metrics.uptimeSeconds)
    }

    @Test
    fun environmentMetrics_mapsTelemetryFields() {
        val proto =
            EnvironmentMetrics(
                temperature = 24.5f,
                relative_humidity = 55.5f,
                soil_temperature = 18.25f,
                soil_moisture = 44,
                barometric_pressure = 1013.2f,
                gas_resistance = 10.5f,
                voltage = 3.7f,
                current = 0.8f,
                iaq = 42,
                lux = 321.5f,
                uv_lux = 4.2f,
            )

        val metrics = DomainEnvironmentMetrics.fromTelemetryProto(proto, time = 999)

        assertEquals(999, metrics.time)
        assertEquals(24.5f, metrics.temperature)
        assertEquals(55.5f, metrics.relativeHumidity)
        assertEquals(18.25f, metrics.soilTemperature)
        assertEquals(44, metrics.soilMoisture)
        assertEquals(1013.2f, metrics.barometricPressure)
        assertEquals(10.5f, metrics.gasResistance)
        assertEquals(3.7f, metrics.voltage)
        assertEquals(0.8f, metrics.current)
        assertEquals(42, metrics.iaq)
        assertEquals(321.5f, metrics.lux)
        assertEquals(4.2f, metrics.uvLux)
    }

    @Test
    fun environmentMetrics_filtersSentinelAndInvalidValues() {
        val proto =
            EnvironmentMetrics(
                temperature = Float.NaN,
                relative_humidity = 0.0f,
                soil_temperature = Float.NaN,
                soil_moisture = Int.MIN_VALUE,
                barometric_pressure = Float.NaN,
                gas_resistance = Float.NaN,
                voltage = Float.NaN,
                current = Float.NaN,
                iaq = Int.MIN_VALUE,
                lux = Float.NaN,
                uv_lux = Float.NaN,
            )

        val metrics = DomainEnvironmentMetrics.fromTelemetryProto(proto, time = 111)

        assertEquals(111, metrics.time)
        assertNull(metrics.temperature)
        assertNull(metrics.relativeHumidity)
        assertNull(metrics.soilTemperature)
        assertNull(metrics.soilMoisture)
        assertNull(metrics.barometricPressure)
        assertNull(metrics.gasResistance)
        assertNull(metrics.voltage)
        assertNull(metrics.current)
        assertNull(metrics.iaq)
        assertNull(metrics.lux)
        assertNull(metrics.uvLux)
    }

    private class TestNodeIdLookup : NodeIdLookup {
        override fun toNodeID(nodeNum: Int): String = DataPacket.nodeNumToDefaultId(nodeNum)
    }
}
