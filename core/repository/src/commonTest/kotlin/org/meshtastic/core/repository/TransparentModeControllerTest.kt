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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransparentModeControllerTest {

    private val radioConfigRepository = FakeRadioConfigRepository()
    private val serviceRepository = FakeServiceRepository()
    private val radioController = FakeRadioController().apply { nextPacketId = FIRST_REQUEST_ID }
    private val connectionIdentity = MutableStateFlow<ConnectionIdentity?>(null)

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

    private fun TestScope.newController(radio: RadioController = radioController) = TransparentModeController(
        radioConfigRepository,
        serviceRepository,
        radio,
        mock<NodeManager>(MockMode.autofill) {
            every { connectionIdentity } returns this@TransparentModeControllerTest.connectionIdentity
        },
        // Not backgroundScope: advanceUntilIdle has to run the response timeouts.
        CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    private fun cache(lora: Config.LoRaConfig) {
        radioConfigRepository.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = lora }.build())
    }

    /** A completed handshake: the node's config is cached, then the state reaches Connected. */
    private fun connect(lora: Config.LoRaConfig, session: Long = 1L, nodeNum: Int = NODE_NUM) {
        cache(lora)
        connectionIdentity.value = ConnectionIdentity(session, ADDRESS, nodeNum, deviceId = null)
        serviceRepository.setConnectionState(ConnectionState.Connected)
    }

    /** The link drops and the same transport session reconnects, as a BLE reconnect or a node reboot does. */
    private fun reconnect(lora: Config.LoRaConfig) {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        connect(lora)
    }

    private fun loraResponse(lora: Config.LoRaConfig, requestId: Int, from: Int = NODE_NUM): MeshPacket {
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

    private fun pendingRead() = radioController.adminRequests.last { it.kind == "getConfig" }

    private fun reads() = radioController.adminRequests.count { it.kind == "getConfig" }

    /**
     * The node answers the outstanding LoRa read. In production the admin handler stores that answer in the config
     * cache for the session it arrived on, so the fake cache is updated the same way here.
     */
    private suspend fun TestScope.nodeAnswers(lora: Config.LoRaConfig) {
        val requestId = checkNotNull(pendingRead().packetId)
        radioController.nextPacketId++
        cache(lora)
        serviceRepository.emitMeshPacket(loraResponse(lora, requestId))
        runCurrent()
    }

    /** The node answers the outstanding LoRa read and the cache keeps what it held. */
    private suspend fun TestScope.nodeAnswersUncached(lora: Config.LoRaConfig) {
        val requestId = checkNotNull(pendingRead().packetId)
        radioController.nextPacketId++
        serviceRepository.emitMeshPacket(loraResponse(lora, requestId))
        runCurrent()
    }

    private fun TestScope.startTurnOff(controller: TransparentModeController) {
        controller.turnOff()
        runCurrent()
    }

    @Test
    fun `the notice shows once the connected node reports transparent mode`() = runTest {
        val controller = newController()
        cache(transparentLora)
        connectionIdentity.value = ConnectionIdentity(1L, ADDRESS, NODE_NUM, deviceId = null)
        serviceRepository.setConnectionState(ConnectionState.Connecting)

        assertEquals(TransparentModeState(), controller.state.first())

        serviceRepository.setConnectionState(ConnectionState.Connected)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `a node with transparent mode off shows no notice and no indicator`() = runTest {
        val controller = newController()
        connect(normalLora)

        assertEquals(TransparentModeState(), controller.state.first())
    }

    @Test
    fun `leaving it on keeps the indicator and sends nothing and the banner reopens the notice`() = runTest {
        val controller = newController()
        connect(transparentLora)

        controller.closeNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())
        assertTrue(radioController.adminRequests.isEmpty())

        controller.reopenNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `the notice returns after a reconnect on the same transport session`() = runTest {
        val controller = newController()
        connect(transparentLora)
        controller.closeNotice()

        reconnect(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `turning it off sends back what the node reports with only transparent mode cleared`() = runTest {
        val controller = newController()
        connect(transparentLora.newBuilder().also { wb -> wb.hop_limit = 7 }.build())

        startTurnOff(controller)

        assertTrue(radioController.localConfigs.isEmpty())

        nodeAnswers(transparentLora)

        assertEquals(listOf(Config.Builder().also { wb -> wb.lora = normalLora }.build()), radioController.localConfigs)
    }

    @Test
    fun `an unanswered first read is unconfirmed and nothing is written`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertTrue(radioController.localConfigs.isEmpty())
    }

    @Test
    fun `success waits for the read-back to say transparent mode is off`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        nodeAnswers(transparentLora)

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
        assertEquals(NODE_NUM, pendingRead().destNum)
        assertEquals(AdminMessage.ConfigType.LORA_CONFIG.value, pendingRead().payload)
        assertEquals(2, reads())

        nodeAnswers(normalLora)

        assertEquals(TransparentModeTurnOff.TURNED_OFF, controller.state.first().turnOff)
    }

    @Test
    fun `a read-back that still says on is reported as still on and a retry runs again`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        nodeAnswers(transparentLora)
        nodeAnswers(transparentLora)

        val state = controller.state.first()
        assertEquals(TransparentModeTurnOff.STILL_ON, state.turnOff)
        assertTrue(state.noticeVisible)

        startTurnOff(controller)

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
        assertEquals(3, reads())
    }

    @Test
    fun `an unanswered read-back is unconfirmed`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        nodeAnswers(transparentLora)
        advanceUntilIdle()

        val state = controller.state.first()
        assertEquals(TransparentModeTurnOff.UNCONFIRMED, state.turnOff)
        assertTrue(state.active)
    }

    @Test
    fun `a rejected write is unconfirmed`() = runTest {
        val controller = newController()
        connect(transparentLora)
        radioController.rejectLocalConfigWritesRemaining = 1

        startTurnOff(controller)
        nodeAnswers(transparentLora)
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertEquals(1, reads())
    }

    @Test
    fun `an answer to another request or from another node does not confirm the turn-off`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        nodeAnswers(transparentLora)
        val readBackId = checkNotNull(pendingRead().packetId)

        serviceRepository.emitMeshPacket(loraResponse(normalLora, requestId = readBackId + 1))
        serviceRepository.emitMeshPacket(loraResponse(normalLora, requestId = readBackId, from = NODE_NUM + 1))
        runCurrent()

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
    }

    @Test
    fun `a reconnect during the first read stops the attempt before it writes`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        val firstRead = checkNotNull(pendingRead().packetId)

        reconnect(transparentLora)
        advanceUntilIdle()

        // A cancelled read leaves no response timeout to run out.
        assertEquals(0L, testScheduler.currentTime)

        serviceRepository.emitMeshPacket(loraResponse(transparentLora, firstRead))
        advanceUntilIdle()

        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `switching radios during the first read stops the attempt before it writes`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        val firstRead = checkNotNull(pendingRead().packetId)

        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        connect(transparentLora, session = 2L, nodeNum = OTHER_NODE_NUM)
        advanceUntilIdle()

        assertEquals(0L, testScheduler.currentTime)

        serviceRepository.emitMeshPacket(loraResponse(transparentLora, firstRead))
        advanceUntilIdle()

        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `an attempt that dies on an error leaves a notice that can be closed`() = runTest {
        val failing =
            object : RadioController by radioController {
                override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int): Unit =
                    throw NotImplementedError("radio layer broke")
            }
        val controller =
            TransparentModeController(
                radioConfigRepository,
                serviceRepository,
                failing,
                mock<NodeManager>(MockMode.autofill) {
                    every { connectionIdentity } returns this@TransparentModeControllerTest.connectionIdentity
                },
                CoroutineScope(StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> }),
            )
        connect(transparentLora)

        startTurnOff(controller)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `after a reconnect mid turn-off the new notice can be closed`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)

        reconnect(transparentLora)
        controller.closeNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())
    }

    @Test
    fun `after a reconnect mid turn-off the new notice can turn it off`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        val readsBefore = reads()

        reconnect(transparentLora)
        startTurnOff(controller)

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
        assertEquals(readsBefore + 1, reads())
    }

    @Test
    fun `a turn-off in flight still counts as transparent though the cache says off`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        cache(normalLora)

        assertTrue(controller.active.first())
    }

    @Test
    fun `the write and the outcome follow the answers of the node where the cache holds something else`() = runTest {
        val controller = newController()
        connect(transparentLora.newBuilder().also { wb -> wb.hop_limit = 7 }.build())

        startTurnOff(controller)
        nodeAnswersUncached(transparentLora)

        assertEquals(listOf(Config.Builder().also { wb -> wb.lora = normalLora }.build()), radioController.localConfigs)

        nodeAnswersUncached(normalLora)

        assertEquals(TransparentModeTurnOff.TURNED_OFF, controller.state.first().turnOff)
    }

    @Test
    fun `a successful turn-off leaves the cached LoRa config to the answers of the node`() = runTest {
        // Models the production contract: setLocalConfig writes the cache optimistically, sendLocalConfig does not.
        val cacheWritingRadio =
            object : RadioController by radioController {
                override suspend fun setLocalConfig(config: Config) {
                    config.lora?.let(::cache)
                    radioController.setLocalConfig(config)
                }
            }
        val controller = newController(cacheWritingRadio)
        connect(transparentLora)

        startTurnOff(controller)
        nodeAnswersUncached(transparentLora)
        nodeAnswersUncached(normalLora)

        assertEquals(TransparentModeTurnOff.TURNED_OFF, controller.state.first().turnOff)
        assertEquals(transparentLora, radioConfigRepository.localConfigFlow.first().lora)
        assertEquals(null, radioConfigRepository.lastSetLocalConfig)
    }

    @Test
    fun `turning it on from settings holds the notice back and counts once the node reads it back on`() = runTest {
        val controller = newController()
        connect(normalLora)

        val confirmed = async { controller.confirmTurnedOnByUser() }
        runCurrent()
        // The save's optimistic cache write lands while the read-back is out.
        cache(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())

        nodeAnswers(transparentLora)

        assertEquals(true, confirmed.await())
        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())

        reconnect(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `an unanswered read-back after turning it on still holds the notice back for that connection`() = runTest {
        val controller = newController()
        connect(normalLora)

        val confirmed = async { controller.confirmTurnedOnByUser() }
        runCurrent()
        cache(transparentLora)
        advanceUntilIdle()

        assertEquals(null, confirmed.await())
        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())
    }

    @Test
    fun `a node that reads back off after turning it on is not taken as the choice of the user`() = runTest {
        val controller = newController()
        connect(normalLora)

        val confirmed = async { controller.confirmTurnedOnByUser() }
        runCurrent()
        // Firmware without field 108 accepts the write and drops the flag.
        nodeAnswers(normalLora)

        assertEquals(false, confirmed.await())

        // Turned on later in this connection, by another client say, the notice still asks.
        cache(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `closing the result of a turn-off clears the notice`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        nodeAnswers(transparentLora)
        nodeAnswers(normalLora)

        controller.closeNotice()

        assertEquals(TransparentModeState(), controller.state.first())
    }

    private companion object {
        const val NODE_NUM = 0x1234
        const val OTHER_NODE_NUM = 0x5678
        const val FIRST_REQUEST_ID = 42
        const val ADDRESS = "x12:34"
    }
}
