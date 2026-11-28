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
import kotlinx.coroutines.CompletableDeferred
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
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransparentModeControllerTest {

    private val radioConfigRepository = FakeRadioConfigRepository()
    private val serviceRepository = FakeServiceRepository()
    private val connectionIdentity = MutableStateFlow<ConnectionIdentity?>(null)
    private var generation = 0L
    private val radio = NodeRadio()

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

    /**
     * The connected node behind [AdminController]: a LoRa read waits until the test answers it, a session-bound write
     * is recorded. An acknowledged write and a read answer both reach the cache before the call returns, as the SDK
     * folds them into the session's config (`AdminApiImpl.submitLocalWrite`, `MeshEngine.mergeLocalReadAnswer`) and the
     * app caches that.
     */
    private inner class NodeRadio : AdminController by FakeRadioController() {
        val reads = mutableListOf<Pair<Int, Int>>()
        val answers = mutableListOf<CompletableDeferred<Config.LoRaConfig>>()
        val writes = mutableListOf<Pair<RadioSessionContext, Config>>()
        var writeResult: AdminResult<Unit> = AdminResult.Success(Unit)

        override suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config> {
            reads += destNum to configType
            val lora = CompletableDeferred<Config.LoRaConfig>().also { answers += it }.await()
            cache(lora)
            return AdminResult.Success(config(lora))
        }

        override suspend fun setLocalConfigOn(session: RadioSessionContext, config: Config): AdminResult<Unit> {
            writes += session to config
            if (writeResult is AdminResult.Success) config.lora?.let { cache(it) }
            return writeResult
        }
    }

    private fun TestScope.newController(admin: AdminController = radio) = TransparentModeController(
        radioConfigRepository,
        serviceRepository,
        admin,
        mock<NodeManager>(MockMode.autofill) {
            every { connectionIdentity } returns this@TransparentModeControllerTest.connectionIdentity
        },
        // Not backgroundScope: advanceUntilIdle has to run the response timeouts.
        CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    private fun cache(lora: Config.LoRaConfig) {
        radioConfigRepository.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = lora }.build())
    }

    private fun config(lora: Config.LoRaConfig) = Config.Builder().also { wb -> wb.lora = lora }.build()

    /** The app session of handshake [generation] with the node at [ADDRESS]. */
    private fun session(generation: Long = 1L) = RadioSessionContext(generation, ADDRESS)

    /** A completed handshake, which is a new app session: the node's config is cached, then the state is Connected. */
    private fun connect(lora: Config.LoRaConfig, nodeNum: Int = NODE_NUM) {
        cache(lora)
        generation++
        connectionIdentity.value = ConnectionIdentity(generation, ADDRESS, nodeNum, deviceId = null)
        serviceRepository.setConnectionState(ConnectionState.Connected)
    }

    /** The link drops and the radio reconnects, as a BLE reconnect or a node reboot does. */
    private fun reconnect(lora: Config.LoRaConfig) {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        connect(lora)
    }

    private fun reads() = radio.reads.size

    /** The node answers the outstanding LoRa read. */
    private fun TestScope.nodeAnswers(lora: Config.LoRaConfig) {
        radio.answers.last().complete(lora)
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
        serviceRepository.setConnectionState(ConnectionState.Connecting(attempt = 1))

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
        assertTrue(radio.reads.isEmpty() && radio.writes.isEmpty())

        controller.reopenNotice()

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `the notice returns after a reconnect`() = runTest {
        val controller = newController()
        connect(transparentLora)
        controller.closeNotice()

        reconnect(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `an unanswered first read is unconfirmed and nothing is written`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertTrue(radio.writes.isEmpty())
    }

    @Test
    fun `success waits for the read-back to say transparent mode is off`() = runTest {
        val controller = newController()
        connect(transparentLora)

        startTurnOff(controller)
        nodeAnswers(transparentLora)

        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
        assertEquals(NODE_NUM to AdminMessage.ConfigType.LORA_CONFIG.value, radio.reads.last())
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
    fun `an unanswered read-back after an unacknowledged write is unconfirmed and still counts as transparent`() =
        runTest {
            val controller = newController()
            connect(transparentLora)
            radio.writeResult = AdminResult.Timeout

            startTurnOff(controller)
            nodeAnswers(transparentLora)
            advanceUntilIdle()

            val state = controller.state.first()
            assertEquals(TransparentModeTurnOff.UNCONFIRMED, state.turnOff)
            assertTrue(state.active)
        }

    @Test
    fun `a write the session refuses is unconfirmed and reads nothing back`() = runTest {
        val controller = newController()
        connect(transparentLora)
        radio.writeResult = AdminResult.NodeUnreachable

        startTurnOff(controller)
        nodeAnswers(transparentLora)
        advanceUntilIdle()

        assertEquals(TransparentModeTurnOff.UNCONFIRMED, controller.state.first().turnOff)
        assertEquals(1, reads())
    }

    @Test
    fun `a link that drops as the first read answers writes nothing`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)

        radio.answers.single().complete(transparentLora)
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        runCurrent()

        assertTrue(radio.writes.isEmpty())
    }

    @Test
    fun `a reconnect during the first read stops the attempt before it writes`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        val firstRead = radio.answers.single()

        reconnect(transparentLora)
        advanceUntilIdle()

        // A cancelled read leaves no response timeout to run out.
        assertEquals(0L, testScheduler.currentTime)

        firstRead.complete(transparentLora)
        advanceUntilIdle()

        assertTrue(radio.writes.isEmpty())
        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `switching radios during the first read stops the attempt before it writes`() = runTest {
        val controller = newController()
        connect(transparentLora)
        startTurnOff(controller)
        val firstRead = radio.answers.single()

        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        connect(transparentLora, nodeNum = OTHER_NODE_NUM)
        advanceUntilIdle()

        assertEquals(0L, testScheduler.currentTime)

        firstRead.complete(transparentLora)
        advanceUntilIdle()

        assertTrue(radio.writes.isEmpty())
        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `an attempt that dies on an error leaves a notice that can be closed`() = runTest {
        val failing =
            object : AdminController by radio {
                override suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config> =
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
        nodeAnswers(transparentLora)

        assertEquals(listOf(session() to config(normalLora)), radio.writes)

        nodeAnswers(normalLora)

        assertEquals(TransparentModeTurnOff.TURNED_OFF, controller.state.first().turnOff)
    }

    @Test
    fun `turning it on from settings holds the notice back and counts once the node reads it back on`() = runTest {
        val controller = newController()
        connect(normalLora)

        val confirmed = async { controller.confirmTurnedOnByUser(session()) }
        runCurrent()
        // The save's acknowledged write lands in the cache while the read-back is out.
        cache(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())

        nodeAnswers(transparentLora)

        assertEquals(true, confirmed.await())
        assertEquals(TransparentModeState(active = true, noticeVisible = false), controller.state.first())

        reconnect(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `an unanswered read-back after turning it on still holds the notice back for that session`() = runTest {
        val controller = newController()
        connect(normalLora)

        val confirmed = async { controller.confirmTurnedOnByUser(session()) }
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

        val confirmed = async { controller.confirmTurnedOnByUser(session()) }
        runCurrent()
        // The turn-on write's acknowledgement cached transparent mode on while the read-back is out.
        cache(transparentLora)
        // Firmware without field 108 accepts the write and drops the flag.
        nodeAnswers(normalLora)

        assertEquals(false, confirmed.await())
        // The read answer reached the cache, so the node no longer counts as transparent.
        assertEquals(false, controller.active.first())

        // Turned on later in this session, by another client say, the notice still asks.
        cache(transparentLora)

        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())
    }

    @Test
    fun `a turn-off handed over by a save starts only on the session the save was sent on`() = runTest {
        val controller = newController()
        connect(transparentLora)
        val savedOn = session()
        reconnect(transparentLora)

        controller.turnOff(savedOn)
        runCurrent()

        assertEquals(0, reads())
        assertEquals(TransparentModeState(active = true, noticeVisible = true), controller.state.first())

        controller.turnOff(session(generation = 2L))
        runCurrent()

        assertEquals(1, reads())
        assertEquals(TransparentModeTurnOff.IN_PROGRESS, controller.state.first().turnOff)
    }

    @Test
    fun `a read-back for a save whose session has moved on reads nothing and confirms nothing`() = runTest {
        val controller = newController()
        connect(normalLora)
        val savedOn = session()
        reconnect(transparentLora)

        assertEquals(null, controller.confirmTurnedOnByUser(savedOn))
        assertEquals(0, reads())
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
        const val ADDRESS = "x12:34"
    }
}
