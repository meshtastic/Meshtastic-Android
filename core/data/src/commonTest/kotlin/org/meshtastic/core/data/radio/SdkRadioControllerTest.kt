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
package org.meshtastic.core.data.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.AdminException
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SdkRadioControllerTest {

    @Test
    fun `setLocalConfig forwards admin config write`() = runTest {
        val fixture = connectedFixture()
        try {
            val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.setLocalConfig(config) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.set_config == config }
            assertTrue(request.want_ack)
            fixture.transport.injectRoutingAck(request.id)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `setRemoteChannel forwards remote admin channel write`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x22334455
            val channel = Channel(index = 1, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "Ops"))
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.setRemoteChannel(destNum, channel) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.set_channel == channel }
            assertEquals(destNum, request.to)
            fixture.transport.injectRoutingAck(request.id, fromNode = destNum)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `setRemoteChannel forwards disabled channel for removal`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x33445566
            val channel = Channel(index = 2, role = Channel.Role.DISABLED)
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.setRemoteChannel(destNum, channel) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.set_channel == channel }
            assertEquals(destNum, request.to)
            fixture.transport.injectRoutingAck(request.id, fromNode = destNum)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `requestTelemetry forwards device request to sdk`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x44556677
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.requestTelemetry(destNum, TelemetryType.DEVICE) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { telemetryOf(it) != null }
            assertEquals(destNum, request.to)
            assertEquals(PortNum.TELEMETRY_APP, request.decoded?.portnum)
            assertTrue(request.decoded?.want_response == true)
            fixture.transport.injectTelemetryResponse(
                requestId = request.id,
                telemetry = Telemetry(device_metrics = DeviceMetrics(battery_level = 87)),
                fromNode = destNum,
            )
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `requestTelemetry local stats targets local node`() = runTest {
        val fixture = connectedFixture()
        try {
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.requestTelemetry(0xCAFEBABE.toInt(), TelemetryType.LOCAL_STATS) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { telemetryOf(it) != null }
            assertEquals(fixture.myNodeNum, request.to)
            fixture.transport.injectTelemetryResponse(
                requestId = request.id,
                telemetry = Telemetry(local_stats = LocalStats(uptime_seconds = 123)),
                fromNode = fixture.myNodeNum,
            )
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `requestPosition encodes coordinates and requests ack`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x55667788
            val position = Position(latitude = 37.1234567, longitude = -122.7654321, altitude = 42, time = 1_700_000_123)
            val outboundBefore = fixture.transport.outboundPackets().size

            fixture.controller.requestPosition(destNum, position)
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { it.decoded?.portnum == PortNum.POSITION_APP }
            val sentPosition = org.meshtastic.proto.Position.ADAPTER.decode(request.decoded!!.payload)
            assertEquals(destNum, request.to)
            assertTrue(request.want_ack)
            assertEquals(Position.degI(position.latitude), sentPosition.latitude_i)
            assertEquals(Position.degI(position.longitude), sentPosition.longitude_i)
            assertEquals(position.altitude, sentPosition.altitude)
            assertEquals(position.time, sentPosition.time)
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `setFixedPosition encodes coordinates for admin api`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x66778899
            val position = Position(latitude = 12.3456789, longitude = 98.7654321, altitude = 321, time = 456)
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.setFixedPosition(destNum, position) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.set_fixed_position != null }
            val sentPosition = adminOf(request)!!.set_fixed_position!!
            assertEquals(destNum, request.to)
            assertEquals(Position.degI(position.latitude), sentPosition.latitude_i)
            assertEquals(Position.degI(position.longitude), sentPosition.longitude_i)
            assertEquals(position.altitude, sentPosition.altitude)
            assertEquals(position.time, sentPosition.time)
            fixture.transport.injectRoutingAck(request.id, fromNode = destNum)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `getConfig returns sdk config response`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x10203040
            val expected = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.getConfig(destNum, AdminMessage.ConfigType.DEVICE_CONFIG.value) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore)
                .last { adminOf(it)?.get_config_request == AdminMessage.ConfigType.DEVICE_CONFIG }
            fixture.transport.injectAdminResponse(
                requestId = request.id,
                response = AdminMessage(get_config_response = expected),
                fromNode = destNum,
            )
            runCurrent()

            assertEquals(expected, deferred.await())
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `getModuleConfig returns sdk module config response`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x11223344
            val expected = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.getModuleConfig(destNum, AdminMessage.ModuleConfigType.MQTT_CONFIG.value) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore)
                .last { adminOf(it)?.get_module_config_request == AdminMessage.ModuleConfigType.MQTT_CONFIG }
            fixture.transport.injectAdminResponse(
                requestId = request.id,
                response = AdminMessage(get_module_config_response = expected),
                fromNode = destNum,
            )
            runCurrent()

            assertEquals(expected, deferred.await())
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `listChannels delegates to sdk and stops at disabled slot`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x7ABCDE01
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.listChannels(destNum) }

            repeat(3) {
                runCurrent()
                val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.get_channel_request != null }
                assertEquals(destNum, request.to)
                val wireIndex = adminOf(request)!!.get_channel_request!!
                val channelIndex = wireIndex - 1
                val channel = if (channelIndex < 2) {
                    Channel(index = channelIndex, role = Channel.Role.PRIMARY, settings = ChannelSettings(name = "Channel $channelIndex"))
                } else {
                    Channel(index = channelIndex, role = Channel.Role.DISABLED)
                }
                fixture.transport.injectAdminResponse(
                    requestId = request.id,
                    response = AdminMessage(get_channel_response = channel),
                    fromNode = destNum,
                )
            }
            runCurrent()
            advanceUntilIdle()

            val channels = deferred.await()
            assertEquals(listOf("Channel 0", "Channel 1"), channels.map { it.settings?.name })
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `reboot forwards reboot command as fire and forget admin write`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x55667711
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async { fixture.controller.reboot(destNum) }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.reboot_seconds == 0 }
            assertEquals(destNum, request.to)
            fixture.transport.injectRoutingAck(request.id, fromNode = destNum)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `sdk timeouts surface to callers`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x12345678
            val deferred = async {
                assertFailsWith<AdminException.Timeout> {
                    fixture.controller.getConfig(destNum, AdminMessage.ConfigType.DEVICE_CONFIG.value)
                }
            }
            runCurrent()
            advanceTimeBy(70.seconds)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    @Test
    fun `unauthorized admin operations surface permission errors`() = runTest {
        val fixture = connectedFixture()
        try {
            val destNum = 0x21436587
            val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
            val outboundBefore = fixture.transport.outboundPackets().size
            val deferred = async {
                assertFailsWith<AdminException.Unauthorized> {
                    fixture.controller.setConfig(destNum, config)
                }
            }
            runCurrent()

            val request = fixture.transport.outboundPackets().drop(outboundBefore).last { adminOf(it)?.set_config == config }
            fixture.transport.injectRoutingError(request.id, Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED, fromNode = destNum)
            runCurrent()

            deferred.await()
        } finally {
            fixture.client.disconnect()
        }
    }

    private suspend fun TestScope.connectedFixture(myNodeNum: Int = 0x11111111): ControllerFixture {
        val transport = FakeRadioTransport(
            identity = TransportIdentity("fake:sdk-radio-controller"),
            autoHandshake = true,
            nodeNum = myNodeNum,
        )
        val client = RadioClient.Builder()
            .transport(transport)
            .storage(InMemoryStorageProvider())
            .autoSyncTimeOnConnect(false)
            .coroutineContext(backgroundScope.coroutineContext)
            .rpcTimeout(60.seconds)
            .sendTimeout(60.seconds)
            .build()
        val dispatcher = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as CoroutineDispatcher
        val controller = SdkRadioController(
            accessor = TestRadioClientAccessor(client),
            serviceRepository = FakeServiceRepository(),
            nodeRepository = FakeNodeRepository(),
            locationManager = NoOpLocationManager,
            deliveryTracker = MessageDeliveryTracker(lazyOf(mock<PacketRepository>(MockMode.autofill)), CoroutineDispatchers(dispatcher, dispatcher, dispatcher)),
            radioPrefs = FakeRadioPrefs(),
        )
        client.connect()
        runCurrent()
        return ControllerFixture(controller = controller, transport = transport, client = client, myNodeNum = myNodeNum)
    }

    private fun adminOf(packet: MeshPacket): AdminMessage? {
        val decoded = packet.decoded ?: return null
        if (decoded.portnum != PortNum.ADMIN_APP) return null
        return runCatching { AdminMessage.ADAPTER.decode(decoded.payload) }.getOrNull()
    }

    private fun telemetryOf(packet: MeshPacket): Telemetry? {
        val decoded = packet.decoded ?: return null
        if (decoded.portnum != PortNum.TELEMETRY_APP) return null
        return runCatching { Telemetry.ADAPTER.decode(decoded.payload) }.getOrNull()
    }

    private data class ControllerFixture(
        val controller: SdkRadioController,
        val transport: FakeRadioTransport,
        val client: RadioClient,
        val myNodeNum: Int,
    )

    private class TestRadioClientAccessor(client: RadioClient) : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(client)

        override fun rebuildAndConnectAsync() = Unit

        override fun disconnect() = Unit
    }

    private object NoOpLocationManager : MeshLocationManager {
        override fun start(scope: CoroutineScope, sendPositionFn: (org.meshtastic.proto.Position) -> Unit) = Unit

        override fun stop() = Unit
    }
}
