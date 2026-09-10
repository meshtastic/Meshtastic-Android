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
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.NodeAddress
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
        assertNull(mapper.toDataPacket(MeshPacket.Builder().also { wb ->wb.from = 0x12345678}.build()))
    }

    @Test
    fun toDataPacket_mapsMeshPacketFields() {
        val payload = "mesh payload".encodeUtf8()
        val packet =
            MeshPacket.Builder().also { wb ->
            wb.from = 0x12345678
            wb.to = 0x90ABCDEF.toInt()
            wb.rx_time = 123
            wb.id = 456
            wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.TEXT_MESSAGE_APP; wb.payload = payload; wb.reply_id = 789; wb.emoji = 321}.build()
            wb.hop_limit = 3
            wb.channel = 4
            wb.want_ack = true
            wb.hop_start = 5
            wb.rx_snr = 6.5f
            wb.rx_rssi = -70
            wb.relay_node = 77
            wb.via_mqtt = true
            wb.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_MQTT
            }.build()

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
    fun toDataPacket_preservesAbsentRssiRatherThanCoercingToZero() {
        val packet = MeshPacket.Builder().also { wb ->wb.from = 1; wb.to = 2; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.TEXT_MESSAGE_APP}.build()}.build()

        val mapped = mapper.toDataPacket(packet)

        assertNotNull(mapped)
        assertNull(mapped.rssi)
    }

    @Test
    fun toDataPacket_keepsAReportedZeroRssiDistinctFromAbsent() {
        val packet = MeshPacket.Builder().also { wb ->wb.from = 1; wb.to = 2; wb.rx_rssi = 0; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.TEXT_MESSAGE_APP}.build()}.build()

        val mapped = mapper.toDataPacket(packet)

        assertNotNull(mapped)
        assertEquals(0, mapped.rssi)
    }

    @Test
    fun toDataPacket_usesPkcChannelWhenPacketIsPkiEncrypted() {
        val packet =
            MeshPacket.Builder().also { wb ->
            wb.from = 1
            wb.to = 2
            wb.channel = 2
            wb.pki_encrypted = true
            wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.PRIVATE_APP}.build()
            }.build()

        val mapped = mapper.toDataPacket(packet)

        assertNotNull(mapped)
        assertEquals(NodeAddress.PKC_CHANNEL_INDEX, mapped.channel)
    }

    @Test
    fun meshUser_mapsProtoFields() {
        val proto =
            User.Builder().also { wb ->
            wb.id = "!cafebabe"
            wb.long_name = "Meshtastic User"
            wb.short_name = "MU"
            wb.hw_model = HardwareModel.TLORA_V2
            wb.is_licensed = true
            wb.role = Config.DeviceConfig.Role.ROUTER
            }.build()

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
        val user = MeshUser(User.Builder().build())

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
            Position.Builder().also { wb ->
            wb.latitude_i = 377749000
            wb.longitude_i = -1224194000
            wb.altitude = 15
            wb.time = 456
            wb.sats_in_view = 9
            wb.ground_speed = 12
            wb.ground_track = 180
            wb.precision_bits = 7
            }.build()

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
        val position = DomainPosition(Position.Builder().build(), defaultTime = 789)

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
            DeviceMetrics.Builder().also { wb ->
            wb.battery_level = 87
            wb.voltage = 4.12f
            wb.channel_utilization = 32.5f
            wb.air_util_tx = 7.75f
            wb.uptime_seconds = 3600
            }.build()

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
        val metrics = DomainDeviceMetrics(DeviceMetrics.Builder().build(), telemetryTime = 222)

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
            EnvironmentMetrics.Builder().also { wb ->
            wb.temperature = 24.5f
            wb.relative_humidity = 55.5f
            wb.soil_temperature = 18.25f
            wb.soil_moisture = 44
            wb.barometric_pressure = 1013.2f
            wb.gas_resistance = 10.5f
            wb.voltage = 3.7f
            wb.current = 0.8f
            wb.iaq = 42
            wb.lux = 321.5f
            wb.uv_lux = 4.2f
            }.build()

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
            EnvironmentMetrics.Builder().also { wb ->
            wb.temperature = Float.NaN
            wb.relative_humidity = 0.0f
            wb.soil_temperature = Float.NaN
            wb.soil_moisture = Int.MIN_VALUE
            wb.barometric_pressure = Float.NaN
            wb.gas_resistance = Float.NaN
            wb.voltage = Float.NaN
            wb.current = Float.NaN
            wb.iaq = Int.MIN_VALUE
            wb.lux = Float.NaN
            wb.uv_lux = Float.NaN
            }.build()

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
        override fun toNodeID(nodeNum: Int): String = NodeAddress.numToDefaultId(nodeNum)
    }
}
