/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.data.manager

import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.DurableQueuedPacket
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.repository.RadioSessionState
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionMessageQueueTest {
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val radioController = mock<RadioController>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

    @Test
    fun `durable source mismatch is terminal and never reaches radio dispatch`() = runTest {
        val fixture = fixture()
        val replacement = ChannelSet(settings = listOf(ChannelSettings(name = "replacement")))
        fixture.channels.value = replacement
        everySuspend { packetRepository.getDurableQueuedPackets() } returns listOf(fixture.queued)

        fixture.queue.drain()

        verifySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ERROR) }
        verifySuspend(mode = VerifyMode.not) { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) }
        fixture.queue.close()
    }

    @Test
    fun `pending Gateway row does not block a later native message`() = runTest {
        val fixture = fixture(activeInitially = false)
        val nativePacket =
            DataPacket(
                bytes = null,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 502,
                status = MessageStatus.QUEUED,
                channel = 0,
                time = 2,
            )
        everySuspend { packetRepository.getDurableQueuedPackets() } returns
            listOf(fixture.queued, DurableQueuedPacket(nativePacket, fixture.sourceChannelId))

        fixture.queue.drain()

        verifySuspend { radioController.sendMessage(nativePacket) }
        verifySuspend { packetRepository.updateMessageStatus(nativePacket, MessageStatus.ENROUTE) }
        verifySuspend(mode = VerifyMode.not) { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) }
        fixture.queue.close()
    }

    @Test
    fun `final inbound activation revision drains an already connected durable row`() = runTest {
        val fixture = fixture(activeInitially = false)
        everySuspend { packetRepository.getDurableQueuedPackets() } returns listOf(fixture.queued)
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } returns
            GatewayPacketDispatchResult.ACCEPTED

        fixture.queue.start()
        runCurrent()
        fixture.gatewayIngressSessionGate.publish(EPOCH)
        runCurrent()

        verifySuspend(mode = VerifyMode.exactly(1)) {
            commandSender.sendDataAwaitForGatewaySession(fixture.packet, EPOCH, fixture.sourceChannelId)
        }
        verifySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ENROUTE) }
        fixture.queue.close()
    }

    @Test
    fun `transient dispatch retries the same durable packet without another connection or enqueue`() = runTest {
        val fixture = fixture()
        var queued = true
        var attempts = 0
        everySuspend { packetRepository.getDurableQueuedPackets() } calls
            {
                if (queued) listOf(fixture.queued) else emptyList()
            }
        everySuspend { packetRepository.getPacketByPacketId(fixture.packet.id) } returns fixture.packet
        everySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ENROUTE) } calls
            {
                queued = false
            }
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } calls
            {
                attempts++
                if (attempts == 1) {
                    GatewayPacketDispatchResult.TRANSIENT_FAILURE
                } else {
                    GatewayPacketDispatchResult.ACCEPTED
                }
            }
        fixture.queue.start()
        runCurrent()
        val attemptsBeforeRetry = attempts
        assertTrue(attemptsBeforeRetry >= 1)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, attempts)
        verifySuspend(mode = VerifyMode.exactly(2)) {
            commandSender.sendDataAwaitForGatewaySession(fixture.packet, EPOCH, fixture.sourceChannelId)
        }
        fixture.queue.close()
    }

    @Test
    fun `enqueue before startup is retained and does not wait for radio dispatch`() = runTest {
        val fixture = fixture()
        var queued = true
        everySuspend { packetRepository.getPacketByPacketId(fixture.packet.id) } returns fixture.packet
        everySuspend { packetRepository.getDurableQueuedPackets() } calls
            {
                if (queued) listOf(fixture.queued) else emptyList()
            }
        everySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ENROUTE) } calls
            {
                queued = false
            }
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } returns
            GatewayPacketDispatchResult.ACCEPTED
        fixture.queue.enqueue(fixture.packet.id)
        verifySuspend(mode = VerifyMode.not) { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) }
        fixture.queue.start()
        runCurrent()
        verifySuspend(mode = VerifyMode.exactly(1)) {
            commandSender.sendDataAwaitForGatewaySession(fixture.packet, EPOCH, fixture.sourceChannelId)
        }
        fixture.queue.close()
    }

    @Test
    fun `retry fails closed when channel identity changes after transient dispatch`() = runTest {
        val fixture = fixture()
        var queued = true
        everySuspend { packetRepository.getDurableQueuedPackets() } calls
            {
                if (queued) listOf(fixture.queued) else emptyList()
            }
        everySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ERROR) } calls
            {
                queued = false
            }
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } returns
            GatewayPacketDispatchResult.TRANSIENT_FAILURE
        fixture.queue.start()
        runCurrent()
        fixture.channels.value = ChannelSet(settings = listOf(ChannelSettings(name = "replacement")))
        advanceTimeBy(30_000)
        runCurrent()
        verifySuspend { packetRepository.updateMessageStatus(fixture.packet, MessageStatus.ERROR) }
        fixture.queue.close()
    }

    @Test
    fun `cancelled dispatch propagates cancellation without rewriting durable state`() = runTest {
        val fixture = fixture()
        everySuspend { packetRepository.getDurableQueuedPackets() } returns listOf(fixture.queued)
        everySuspend { commandSender.sendDataAwaitForGatewaySession(any(), any(), any()) } calls
            {
                throw CancellationException("retired endpoint")
            }
        var cancelled = false
        try {
            fixture.queue.drain()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        verifySuspend(mode = VerifyMode.not) { packetRepository.updateMessageStatus(any(), any()) }
        fixture.queue.close()
    }

    private fun TestScope.fixture(activeInitially: Boolean = true): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val session = MutableStateFlow(readySession())
        val gatewayIngressSessionGate = GatewayIngressSessionGate()
        if (activeInitially) gatewayIngressSessionGate.publish(EPOCH)
        val settings = ChannelSettings(name = "original")
        val channelSet = ChannelSet(settings = listOf(settings))
        val channels = MutableStateFlow(channelSet)
        val sourceChannelId =
            NtsocialGatewayIdentity.channel(
                Channel(index = 0, role = Channel.Role.PRIMARY, settings = settings),
                channelSet.lora_config ?: Config.LoRaConfig(),
            )
                .sourceChannelId
        val packet =
            DataPacket(bytes = null, dataType = 256, id = 501, status = MessageStatus.QUEUED, channel = 0, time = 1)

        every { radioController.connectionState } returns connection
        every { radioInterfaceService.radioSessionState } returns session
        every { radioConfigRepository.channelSetFlow } returns channels

        val queue =
            SessionMessageQueue(
                packetRepository = packetRepository,
                radioController = lazy { radioController },
                commandSender = commandSender,
                radioConfigRepository = radioConfigRepository,
                radioInterfaceService = radioInterfaceService,
                gatewayIngressSessionGate = gatewayIngressSessionGate,
                parentScope = kotlinx.coroutines.CoroutineScope(dispatcher),
            )
        return Fixture(
            queue = queue,
            packet = packet,
            queued = DurableQueuedPacket(packet, sourceChannelId),
            channels = channels,
            gatewayIngressSessionGate = gatewayIngressSessionGate,
            sourceChannelId = sourceChannelId,
        )
    }

    private fun readySession() = RadioSessionState(
        epoch = EPOCH,
        selectedDeviceAddress = RADIO,
        activeDeviceAddress = RADIO,
        transportConnectionState = ConnectionState.Connected,
        configured = true,
    )

    private data class Fixture(
        val queue: SessionMessageQueue,
        val packet: DataPacket,
        val queued: DurableQueuedPacket,
        val channels: MutableStateFlow<ChannelSet>,
        val gatewayIngressSessionGate: GatewayIngressSessionGate,
        val sourceChannelId: String,
    )

    private companion object {
        const val EPOCH = 7L
        const val RADIO = "xAAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
    }
}
