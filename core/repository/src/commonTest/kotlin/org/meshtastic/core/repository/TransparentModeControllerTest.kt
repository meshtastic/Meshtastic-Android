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
package org.meshtastic.core.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransparentModeControllerTest {

    private val radioConfigRepository = FakeRadioConfigRepository()
    private val serviceRepository = FakeServiceRepository()
    private val radioController = FakeRadioController().apply { nextPacketId = READ_BACK_ID }
    private val connectionIdentity = MutableStateFlow<ConnectionIdentity?>(null)
    private val controller =
        TransparentModeController(radioConfigRepository, serviceRepository, radioController, connectionIdentity)

    /** A LoRa config away from its defaults in every kind of field, with transparent mode off. */
    private val normalLora =
        Config.LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = false
                wb.region = Config.LoRaConfig.RegionCode.EU_868
                wb.bandwidth = 250
                wb.spread_factor = 9
                wb.coding_rate = 6
                wb.hop_limit = 5
                wb.tx_enabled = true
                wb.tx_power = 17
                wb.channel_num = 3
                wb.override_frequency = 869.525f
                wb.sx126x_rx_boosted_gain = true
                wb.ignore_mqtt = true
                wb.config_ok_to_mqtt = true
            }
            .build()
    private val transparentLora = normalLora.newBuilder().also { wb -> wb.transparent_mode = true }.build()

    private fun connect(lora: Config.LoRaConfig, session: Long = 1L) {
        radioConfigRepository.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = lora }.build())
        connectionIdentity.value = ConnectionIdentity(session, ADDRESS, NODE_NUM, deviceId = null)
        serviceRepository.setConnectionState(ConnectionState.Connected)
    }

    private fun loraResponse(lora: Config.LoRaConfig, requestId: Int = READ_BACK_ID, from: Int = NODE_NUM): MeshPacket {
        val admin =
            AdminMessage.Builder()
                .also { wb -> wb.get_config_response = Config.Builder().also { cb -> cb.lora = lora }.build() }
                .build()
        val data =
            Data.Builder()
                .also { wb ->
                    wb.portnum = PortNum.ADMIN_APP
                    wb.request_id = requestId
                    wb.payload = admin.encodeByteString()
                }
                .build()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = from
                wb.decoded = data
            }
            .build()
    }

    private fun TestScope.startTurnOff() {
        launch { controller.turnOff() }
        runCurrent()
    }

    @Test
    fun `the notice shows once the connected node reports transparent mode`() = runTest {
        radioConfigRepository.setLocalConfigDirect(
            LocalConfig.Builder().also { wb -> wb.lora = transparentLora }.build(),
        )
        connectionIdentity.value = ConnectionIdentity(1L, ADDRESS, NODE_NUM, deviceId = null)
        serviceRepository.setConnectionState(ConnectionState.Connecting)

        assertEquals(TransparentModeState(), controller.state.first())

        serviceRepository.setConnectionState(ConnectionState.Connected)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `a node with transparent mode off shows no notice and no indicator`() = runTest {
        connect(normalLora)

        assertEquals(TransparentModeState(), controller.state.first())
    }

    @Test
    fun `leaving it on keeps the indicator and writes nothing and the banner reopens the notice`() = runTest {
        connect(transparentLora)

        controller.closeNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())
        assertTrue(radioController.configWrites.isEmpty())

        controller.reopenNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `the notice returns on the next connection to the same node`() = runTest {
        connect(transparentLora, session = 1L)
        controller.closeNotice()

        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        connectionIdentity.value = null
        connect(transparentLora, session = 2L)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `turning it off sends the reported LoRa config with only transparent mode cleared`() = runTest {
        connect(transparentLora)

        startTurnOff()

        assertEquals(listOf(Config.Builder().also { wb -> wb.lora = normalLora }.build()), radioController.localConfigs)
    }

    @Test
    fun `success waits for the read-back to say transparent mode is off`() = runTest {
        connect(transparentLora)

        startTurnOff()

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
        val readBack = radioController.adminRequests.single { it.kind == "getConfig" }
        assertEquals(NODE_NUM, readBack.destNum)
        assertEquals(AdminMessage.ConfigType.LORA_CONFIG.value, readBack.payload)

        serviceRepository.emitMeshPacket(loraResponse(normalLora))
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.TURNED_OFF, controller.state.first().turnOff)
        assertEquals(false, radioConfigRepository.lastSetLocalConfig?.lora?.transparent_mode)
    }

    @Test
    fun `a read-back that still says on is reported as still on and a retry writes again`() = runTest {
        connect(transparentLora)

        startTurnOff()
        serviceRepository.emitMeshPacket(loraResponse(transparentLora))
        advanceUntilIdle()

        val state = controller.state.first()
        assertEquals(TransparentModeTurnOff.STILL_ON, state.turnOff)
        assertTrue(state.noticeVisible)
        assertEquals(true, radioConfigRepository.lastSetLocalConfig?.lora?.transparent_mode)

        startTurnOff()

        assertEquals(2, radioController.localConfigs.size)
        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
    }

    @Test
    fun `an unanswered read-back is unconfirmed and the cache keeps what the node last reported`() = runTest {
        connect(transparentLora)

        startTurnOff()
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertEquals(transparentLora, radioConfigRepository.lastSetLocalConfig?.lora)
    }

    @Test
    fun `a rejected write is unconfirmed`() = runTest {
        connect(transparentLora)
        radioController.rejectLocalConfigWritesRemaining = 1

        startTurnOff()
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertTrue(radioController.adminRequests.none { it.kind == "getConfig" })
    }

    @Test
    fun `an answer to another request or from another node does not confirm the turn-off`() = runTest {
        connect(transparentLora)

        startTurnOff()
        serviceRepository.emitMeshPacket(loraResponse(normalLora, requestId = READ_BACK_ID + 1))
        serviceRepository.emitMeshPacket(loraResponse(normalLora, from = NODE_NUM + 1))
        runCurrent()

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
    }

    @Test
    fun `closing the result of a turn-off clears the notice`() = runTest {
        connect(transparentLora)
        startTurnOff()
        serviceRepository.emitMeshPacket(loraResponse(normalLora))
        advanceUntilIdle()
        radioConfigRepository.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = normalLora }.build())

        controller.closeNotice()

        val state = controller.state.first()
        assertFalse(state.noticeVisible)
        assertFalse(state.active)
        assertNull(state.turnOff)
    }

    private companion object {
        const val NODE_NUM = 0x1234
        const val READ_BACK_ID = 42
        const val ADDRESS = "x12:34"
    }
}
