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
package org.meshtastic.core.service

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.NodeId
import kotlin.test.Test

class HeardOnCurrentLoraRefresherTest {

    private val serviceRepository = ServiceRepositoryImpl().apply { setConnectionState(ConnectionState.Connected) }
    private val meshConfigFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
    private val radioOperationLock = RadioOperationLock()
    private val reportsHeard = MutableStateFlow(true)
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also {
            every { it.myNodeNum } returns MutableStateFlow(MY_NODE)
            every { it.reportsHeardOnCurrentLora } returns reportsHeard
        }
    private val radioConfigRepository: RadioConfigRepository =
        mock<RadioConfigRepository>(MockMode.autofill).also {
            every { it.localConfigFlow } returns flowOf(LocalConfig.Builder().also { wb -> wb.lora = homeLora }.build())
            every { it.channelSetFlow } returns
                flowOf(ChannelSet.Builder().also { wb -> wb.settings = listOf(settings(name = "Home")) }.build())
        }

    /** Writes the device has not answered yet, oldest first. */
    private val pendingWrites = mutableListOf<CompletableDeferred<AdminResult<Unit>>>()

    private val admin =
        object : AdminApi by mock<AdminApi>(MockMode.autofill) {
            override fun forNode(dest: NodeId): AdminApi = this

            override suspend fun setConfig(config: Config) = awaitAnswer()

            override suspend fun setChannel(channel: Channel) = awaitAnswer()

            private suspend fun awaitAnswer(): AdminResult<Unit> =
                CompletableDeferred<AdminResult<Unit>>().also { pendingWrites += it }.await()
        }

    private val radio: RadioInterfaceService =
        mock<RadioInterfaceService>(MockMode.autofill).also {
            every { it.activeSession } returns MutableStateFlow(RadioSessionContext(1L, "tcp:radio"))
            every { it.adminForSession(any()) } returns admin
        }

    private fun TestScope.controller() = AdminControllerImpl(
        nodeManager = nodeManager,
        radio = radio,
        sessionAdmin = SessionAdmin(radio, backgroundScope.asServiceScope()),
        heardOnCurrentLoraRefresher =
        HeardOnCurrentLoraRefresher(
            nodeManager = nodeManager,
            meshConfigFlowManager = lazy { meshConfigFlowManager },
            serviceRepository = serviceRepository,
            radioConfigRepository = radioConfigRepository,
            radioOperationLock = radioOperationLock,
            scope = backgroundScope,
        ),
    )

    private fun lora(build: Config.LoRaConfig.Builder.() -> Unit) =
        Config.Builder().also { wb -> wb.lora = homeLora.newBuilder().apply(build).build() }.build()

    private val turbo = lora { modem_preset = Config.LoRaConfig.ModemPreset.LONG_TURBO }

    private fun settings(name: String, psk: Byte = 1) = ChannelSettings.Builder()
        .also { wb ->
            wb.name = name
            wb.psk = byteArrayOf(psk).toByteString()
        }
        .build()

    private fun channel(role: Channel.Role, settings: ChannelSettings) = Channel.Builder()
        .also { wb ->
            wb.role = role
            wb.settings = settings
        }
        .build()

    private fun TestScope.save(controller: AdminControllerImpl, destNum: Int, config: Config) {
        backgroundScope.launch { controller.setConfig(destNum, config) }
        runCurrent()
    }

    private fun TestScope.answer(index: Int = pendingWrites.lastIndex, result: AdminResult<Unit> = ACKED) {
        pendingWrites[index].complete(result)
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(30_000)
        runCurrent()
    }

    @Test
    fun `an acked local preset change that does not reboot refreshes the node info once`() = runTest {
        controller().setLocalConfig(turbo)
        runCurrent()
        answer()
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save the radio never acks does not refresh`() = runTest {
        save(controller(), MY_NODE, turbo)
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save followed by a reboot leaves the refresh to the next handshake`() = runTest {
        save(controller(), MY_NODE, turbo)
        answer()
        advanceTimeBy(2_000)
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `firmware that does not report heard flags is never refreshed`() = runTest {
        reportsHeard.value = false
        save(controller(), MY_NODE, turbo)
        answer()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `saves that leave the slot unchanged do not refresh`() = runTest {
        val controller = controller()
        val device =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { d -> d.led_heartbeat_disabled = true }.build()
                }
                .build()
        for (config in listOf(device, lora { tx_power = 10 }, lora { bandwidth = 500 })) {
            save(controller, MY_NODE, config)
            answer()
            settle()
        }

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a LoRa save to a remote node does not refresh`() = runTest {
        save(controller(), REMOTE_NODE, turbo)
        answer()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `only renaming the primary channel refreshes`() = runTest {
        val controller = controller()
        controller.setLocalChannel(channel(Channel.Role.SECONDARY, settings(name = "Away")))
        runCurrent()
        answer()
        controller.setLocalChannel(channel(Channel.Role.PRIMARY, settings(name = "Home", psk = 2)))
        runCurrent()
        answer()
        settle()
        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }

        controller.setLocalChannel(channel(Channel.Role.PRIMARY, settings(name = "Away")))
        runCurrent()
        answer()
        settle()
        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `back to back acked LoRa saves refresh once for the newest`() = runTest {
        val controller = controller()
        save(controller, MY_NODE, turbo)
        save(controller, MY_NODE, lora { channel_num = 3 })
        answer(index = 0)
        answer(index = 1)
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `a rejected later LoRa save does not cancel the refresh for an applied one`() = runTest {
        val controller = controller()
        save(controller, MY_NODE, turbo)
        answer()
        save(controller, MY_NODE, lora { channel_num = 3 })
        answer(result = AdminResult.Failed(Routing.Error.BAD_REQUEST))
        settle()

        verify(exactly(1)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    @Test
    fun `preset shifts during a discovery scan do not refresh`() = runTest {
        radioOperationLock.acquire(RadioOperation.DiscoveryScan)
        controller().setLocalConfig(turbo)
        runCurrent()
        answer()
        settle()

        verify(exactly(0)) { meshConfigFlowManager.refreshNodeInfo() }
    }

    private companion object {
        const val MY_NODE = 1234
        const val REMOTE_NODE = 5678
        val ACKED: AdminResult<Unit> = AdminResult.Success(Unit)

        val homeLora: Config.LoRaConfig =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.region = Config.LoRaConfig.RegionCode.US
                    wb.use_preset = true
                    wb.modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST
                    wb.bandwidth = 250
                }
                .build()
    }
}
