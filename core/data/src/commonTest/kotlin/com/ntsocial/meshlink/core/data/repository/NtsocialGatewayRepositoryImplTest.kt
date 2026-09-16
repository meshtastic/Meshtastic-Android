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
package com.ntsocial.meshlink.core.data.repository

import com.ntsocial.meshlink.core.data.manager.RadioIngressWorkTracker
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.Node
import com.ntsocial.meshlink.core.model.Position
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayHistoryState
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageChange
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.testing.FakeDatabaseManager
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeRadioInterfaceService
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NtsocialGatewayRepositoryImplTest {

    private val commandSender = RecordingCommandSender()
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val messageQueue = RecordingMessageQueue()
    private val nodeRepository =
        FakeNodeRepository().apply {
            setOurNode(Node(num = 0x12345678, user = User(id = "!12345678")))
            setMyId("!12345678")
        }
    private val radioConfigRepository =
        FakeRadioConfigRepository().apply {
            setCompleteChannelReadback(
                ChannelSet(
                    settings = listOf(ChannelSettings(name = "primary"), ChannelSettings(name = "native", id = 42)),
                ),
            )
        }
    private val radioInterfaceService =
        FakeRadioInterfaceService(CoroutineScope(SupervisorJob())).apply {
            setDeviceAddress(RADIO_A)
            onConnect()
            markCurrentSessionConfigured(radioSessionState.value.epoch)
        }
    private val databaseManager = FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_A) }

    init {
        everySuspend { packetRepository.readCurrentGatewayHistoryState(emptyList()) } returns
            NtsocialGatewayHistoryState(historyEpoch = "epoch-a", messageChangeSeq = 0)
    }

    private val repository =
        NtsocialGatewayRepositoryImpl(
            commandSender,
            packetRepository,
            messageQueue,
            nodeRepository,
            radioConfigRepository,
            radioInterfaceService,
            databaseManager,
            RadioIngressWorkTracker(),
            CoroutineScope(SupervisorJob()),
            ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
        )

    @Test
    fun `cacheInbound caches valid PRIVATE_APP NTsocial envelope`() = runTest {
        activateInbound(repository)
        val payload = "inbound".encodeToByteArray().toByteString()
        val raw = NtsocialEnvelopeCodec.encode(headerMsgId = testHeaderMsgId(), payload = payload)
        val packet = MeshPacket(id = 42)
        val dataPacket = dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, raw = raw)

        val accepted = repository.cacheInbound(packet, dataPacket)

        val cached = repository.cachedEnvelopes.value.single()
        assertTrue(accepted)
        assertEquals(NtsocialEnvelopeDirection.INBOUND, cached.direction)
        assertEquals(payload, cached.envelope.payload)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, cached.portNum)
        assertEquals(42, cached.packetId)
    }

    @Test
    fun `cache captures insertion-time channel identity and history epoch`() = runTest {
        val history = MutableStateFlow(NtsocialGatewayHistoryState(historyEpoch = "epoch-a", messageChangeSeq = 0))
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns history
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "stable", id = 42))))
            }
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } returns history.value
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                radioInterfaceService,
                databaseManager,
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )
        activateInbound(scopedRepository)

        val firstRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = "first".encodeToByteArray().toByteString(),
            )
        scopedRepository.cacheInbound(
            MeshPacket(id = 7),
            dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, raw = firstRaw).copy(channel = 0),
        )
        val captured = scopedRepository.cachedEnvelopes.value.single()
        val expectedSourceId =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 0,
                    role = org.meshtastic.proto.Channel.Role.PRIMARY,
                    settings = ChannelSettings(name = "stable", id = 42),
                ),
            )
                .sourceChannelId

        channels.setChannelSet(ChannelSet(settings = listOf(ChannelSettings(name = "replacement", id = 99))))
        history.value = NtsocialGatewayHistoryState(historyEpoch = "epoch-b", messageChangeSeq = 0)
        runCurrent()

        assertEquals(expectedSourceId, captured.sourceChannelId)
        assertEquals("epoch-a", captured.historyEpoch)
    }

    @Test
    fun `cacheInbound rejects non NTsocial PRIVATE_APP payload`() = runTest {
        activateInbound(repository)
        val dataPacket =
            dataPacket(
                portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                raw = "not-nm".encodeToByteArray().toByteString(),
            )

        val accepted = repository.cacheInbound(MeshPacket(id = 42), dataPacket)

        assertFalse(accepted)
        assertTrue(repository.cachedEnvelopes.value.isEmpty())
    }

    @Test
    fun `cacheInbound accepts legacy port as receive-only compatibility`() = runTest {
        activateInbound(repository)
        val payload = "legacy".encodeToByteArray().toByteString()
        val raw = NtsocialEnvelopeCodec.encode(headerMsgId = testHeaderMsgId(), payload = payload)
        val dataPacket = dataPacket(portNum = NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, raw = raw)

        val accepted = repository.cacheInbound(MeshPacket(id = 43), dataPacket)

        val cached = repository.cachedEnvelopes.value.single()
        assertTrue(accepted)
        assertEquals(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM, cached.portNum)
        assertEquals(payload, cached.envelope.payload)
    }

    @Test
    fun `cacheInbound deduplicates envelopes by header message id`() = runTest {
        activateInbound(repository)
        val headerMsgId = testHeaderMsgId()
        val firstRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = headerMsgId,
                payload = "first".encodeToByteArray().toByteString(),
            )
        val secondRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = headerMsgId,
                payload = "second".encodeToByteArray().toByteString(),
            )

        repository.cacheInbound(
            MeshPacket(id = 1),
            dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, firstRaw),
        )
        repository.cacheInbound(
            MeshPacket(id = 2),
            dataPacket(portNum = NtsocialTransport.PRIVATE_APP_PORT_NUM, secondRaw),
        )

        val cached = repository.cachedEnvelopes.value.single()
        assertEquals("first".encodeToByteArray().toByteString(), cached.envelope.payload)
        assertEquals(1, repository.cachedEnvelopes.value.size)
    }

    @Test
    fun `radio switch invalidates stale ingress identity before replacement is ready`() = runTest {
        activateInbound(repository)
        val raw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = "radio-a".encodeToByteArray().toByteString(),
            )
        assertTrue(
            repository.cacheInbound(MeshPacket(id = 50), dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, raw)),
        )

        radioInterfaceService.setDeviceAddress(RADIO_B)
        databaseManager.setCurrentAddressForTest(RADIO_B)
        val replacementRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId().toByteArray().reversedArray().toByteString(),
                payload = "radio-b-not-ready".encodeToByteArray().toByteString(),
            )

        assertFalse(
            repository.cacheInbound(
                MeshPacket(id = 51),
                dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, replacementRaw),
            ),
        )
        assertEquals(listOf(50), repository.cachedEnvelopes.value.map { it.packetId })
    }

    @Test
    fun `activation ignores lagged history and channel collectors and captures exact replacement`() = runTest {
        val staleHistory = MutableStateFlow(NtsocialGatewayHistoryState("epoch-a", 0))
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns staleHistory
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } returns
            NtsocialGatewayHistoryState("epoch-b", 0)
        val exactChannels = ChannelSet(settings = listOf(ChannelSettings(name = "radio-b", id = 99)))
        val channels = FakeRadioConfigRepository().apply { setCompleteChannelReadback(exactChannels) }
        val replacementRadio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(RADIO_B)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val replacementDb = FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_B) }
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                replacementRadio,
                replacementDb,
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )

        // Do not run the repository's asynchronous collectors before activation.
        assertTrue(scopedRepository.activateInboundSession(replacementRadio.radioSessionState.value.epoch))
        val raw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = "exact-b".encodeToByteArray().toByteString(),
            )
        assertTrue(
            scopedRepository.cacheInbound(
                MeshPacket(id = 52),
                dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, raw).copy(channel = 0),
            ),
        )

        val cached = scopedRepository.cachedEnvelopes.value.single()
        val expectedChannelId =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 0,
                    role = org.meshtastic.proto.Channel.Role.PRIMARY,
                    settings = exactChannels.settings.single(),
                ),
            )
                .sourceChannelId
        assertEquals(expectedChannelId, cached.sourceChannelId)
        assertEquals("epoch-b", cached.historyEpoch)
    }

    @Test
    fun `ordinary channel mutation revokes old identity until exact final snapshot is activated`() = runTest {
        val history = MutableStateFlow(NtsocialGatewayHistoryState("epoch-a", 0))
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns history
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } calls { history.value }
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "primary", id = 1))))
            }
        val exactRadio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(RADIO_A)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val exactDb = FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_A) }
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                exactRadio,
                exactDb,
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )
        assertTrue(scopedRepository.activateInboundSession(exactRadio.radioSessionState.value.epoch))

        val secondary = ChannelSettings(name = "new-secondary", id = 77)
        channels.updateChannelSettings(
            org.meshtastic.proto.Channel(
                index = 1,
                role = org.meshtastic.proto.Channel.Role.SECONDARY,
                settings = secondary,
            ),
        )
        val raw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = "new-slot".encodeToByteArray().toByteString(),
            )

        // The collector has deliberately not run: the synchronous snapshot revision check must close the old identity.
        assertFalse(
            scopedRepository.cacheInbound(
                MeshPacket(id = 53),
                dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, raw).copy(channel = 1),
            ),
        )

        history.value = NtsocialGatewayHistoryState("epoch-b", 0)
        assertTrue(scopedRepository.activateInboundSession(exactRadio.radioSessionState.value.epoch))
        assertTrue(
            scopedRepository.cacheInbound(
                MeshPacket(id = 54),
                dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, raw).copy(channel = 1),
            ),
        )
        val expectedSource =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 1,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = secondary,
                ),
            )
                .sourceChannelId
        assertEquals(expectedSource, scopedRepository.cachedEnvelopes.value.single().sourceChannelId)
        assertEquals("epoch-b", scopedRepository.cachedEnvelopes.value.single().historyEpoch)
    }

    @Test
    fun `manual invalidation latch stays closed across lora and channel snapshot collectors`() = runTest {
        val history = MutableStateFlow(NtsocialGatewayHistoryState("epoch-a", 0))
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns history
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } calls { history.value }
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "primary", id = 1))))
            }
        val exactRadio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(RADIO_A)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                exactRadio,
                FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_A) },
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )
        val sessionEpoch = exactRadio.radioSessionState.value.epoch
        assertTrue(scopedRepository.activateInboundSession(sessionEpoch))

        scopedRepository.invalidateInboundSession()
        channels.setLocalConfig(Config(lora = Config.LoRaConfig(use_preset = true)))
        runCurrent()
        assertFalse(scopedRepository.isInboundSessionActive(sessionEpoch))

        channels.updateChannelSettings(
            org.meshtastic.proto.Channel(
                index = 1,
                role = org.meshtastic.proto.Channel.Role.SECONDARY,
                settings = ChannelSettings(name = "after-lora", id = 2),
            ),
        )
        runCurrent()
        assertFalse(scopedRepository.isInboundSessionActive(sessionEpoch))

        assertTrue(scopedRepository.activateInboundSession(sessionEpoch))
        assertTrue(scopedRepository.isInboundSessionActive(sessionEpoch))
    }

    @Test
    fun `late even snapshot collector cannot supersede suspended explicit activation`() = runTest {
        val history = MutableStateFlow(NtsocialGatewayHistoryState("epoch-a", 0))
        val explicitReadStarted = CompletableDeferred<Unit>()
        val releaseExplicitRead = CompletableDeferred<Unit>()
        var readCount = 0
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns history
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } calls
            {
                readCount += 1
                if (readCount == 2) {
                    explicitReadStarted.complete(Unit)
                    releaseExplicitRead.await()
                }
                history.value
            }
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "primary", id = 1))))
            }
        val exactRadio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(RADIO_A)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                exactRadio,
                FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_A) },
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )
        val sessionEpoch = exactRadio.radioSessionState.value.epoch
        assertTrue(scopedRepository.activateInboundSession(sessionEpoch))
        channels.updateChannelSettings(
            org.meshtastic.proto.Channel(
                index = 1,
                role = org.meshtastic.proto.Channel.Role.SECONDARY,
                settings = ChannelSettings(name = "final", id = 9),
            ),
        )

        val explicitActivation = async { scopedRepository.activateInboundSession(sessionEpoch) }
        explicitReadStarted.await()
        runCurrent() // The even-generation collector queues behind the explicit opener's identity mutex.
        releaseExplicitRead.complete(Unit)

        assertTrue(explicitActivation.await())
        runCurrent()
        assertTrue(scopedRepository.isInboundSessionActive(sessionEpoch))
    }

    @Test
    fun `even snapshot refresh survives concurrent stale cache identity clear`() = runTest {
        val history = MutableStateFlow(NtsocialGatewayHistoryState("epoch-a", 0))
        val packetStore = mock<PacketRepository>(MockMode.autofill)
        every { packetStore.getGatewayHistoryState(emptyList()) } returns history
        everySuspend { packetStore.readCurrentGatewayHistoryState(emptyList()) } calls { history.value }
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "primary", id = 1))))
            }
        val exactRadio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(RADIO_A)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val scopedRepository =
            NtsocialGatewayRepositoryImpl(
                commandSender,
                packetStore,
                messageQueue,
                nodeRepository,
                channels,
                exactRadio,
                FakeDatabaseManager().apply { setCurrentAddressForTest(RADIO_A) },
                RadioIngressWorkTracker(),
                backgroundScope,
                ingressSessionGate = com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate(),
            )
        val sessionEpoch = exactRadio.radioSessionState.value.epoch
        assertTrue(scopedRepository.activateInboundSession(sessionEpoch))
        channels.updateChannelSettings(
            org.meshtastic.proto.Channel(
                index = 1,
                role = org.meshtastic.proto.Channel.Role.SECONDARY,
                settings = ChannelSettings(name = "replacement", id = 8),
            ),
        )
        val raw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = "stale-clear".encodeToByteArray().toByteString(),
            )
        var concurrentCacheAccepted = true
        scopedRepository.beforeInboundIdentityClearCompareAndSetForTest = {
            concurrentCacheAccepted =
                scopedRepository.cacheInbound(
                    MeshPacket(id = 55),
                    dataPacket(NtsocialTransport.PRIVATE_APP_PORT_NUM, raw).copy(channel = 1),
                )
        }

        runCurrent()

        assertFalse(concurrentCacheAccepted)
        assertTrue(scopedRepository.isInboundSessionActive(sessionEpoch))
    }

    @Test
    fun `sendTestPayload sends only PRIVATE_APP port 256 and caches outbound envelope`() {
        val headerMsgId = testHeaderMsgId()
        val payload = "outbound".encodeToByteArray().toByteString()

        val cached =
            repository.sendTestPayload(
                payload = payload,
                to = "!0000002a",
                channelIndex = 2,
                wantAck = false,
                headerMsgId = headerMsgId,
            )

        val sent = commandSender.sentData.single()
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, sent.dataType)
        assertTrue(NtsocialTransport.isOutboundPort(sent.dataType))
        assertFalse(NtsocialTransport.isOutboundPort(NtsocialTransport.LEGACY_RECEIVE_ONLY_PORT_NUM))
        assertEquals(0, commandSender.adminSendCount)
        assertEquals(2, sent.channel)
        assertFalse(sent.wantAck)
        assertEquals(cached.rawBytes, sent.bytes)
        assertEquals(NtsocialEnvelopeDirection.OUTBOUND, cached.direction)
        assertEquals(payload, cached.envelope.payload)
        assertEquals(cached, repository.cachedEnvelopes.value.single())
    }

    @Test
    fun `sendTestPayload rejects payloads over conservative MVP limit`() {
        val payload = ByteArray(NtsocialTransport.MAX_PAYLOAD_SIZE_BYTES + 1) { 0x01 }.toByteString()

        assertFailsWith<IllegalArgumentException> {
            repository.sendTestPayload(payload = payload, headerMsgId = testHeaderMsgId())
        }
        assertTrue(commandSender.sentData.isEmpty())
        assertTrue(repository.cachedEnvelopes.value.isEmpty())
    }

    @Test
    fun `sendRawEnvelope keeps parent NM envelope unchanged on PRIVATE_APP port`() {
        val raw = NtsocialEnvelopeCodec.encode(testHeaderMsgId(), "parent-payload".encodeToByteArray().toByteString())

        val cached =
            repository.sendRawEnvelope(
                rawEnvelope = raw,
                to = "!0000002a",
                channelIndex = 2,
                hopLimit = 3,
                wantAck = false,
                packetId = 77,
            )

        val sent = commandSender.sentData.single()
        assertEquals(raw, sent.bytes)
        assertEquals(NtsocialTransport.PRIVATE_APP_PORT_NUM, sent.dataType)
        assertEquals(2, sent.channel)
        assertEquals(3, sent.hopLimit)
        assertEquals(77, sent.id)
        assertEquals(77, cached.packetId)
        assertFalse(sent.wantAck)
        assertEquals(raw, cached.rawBytes)
        assertEquals(NtsocialEnvelopeDirection.OUTBOUND, cached.direction)
    }

    @Test
    fun `sendRawEnvelope rejects invalid or oversized complete parent envelopes`() {
        assertFailsWith<IllegalArgumentException> {
            repository.sendRawEnvelope(
                rawEnvelope = "not-an-nm-envelope".encodeToByteArray().toByteString(),
                channelIndex = 0,
            )
        }

        val oversizedRaw =
            NtsocialEnvelopeCodec.encode(
                headerMsgId = testHeaderMsgId(),
                payload = ByteArray(NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES).toByteString(),
            )
        assertFailsWith<IllegalArgumentException> {
            repository.sendRawEnvelope(rawEnvelope = oversizedRaw, channelIndex = 0)
        }
        assertTrue(commandSender.sentData.isEmpty())
    }

    @Test
    fun `durable raw envelope is persisted before platform retry work is admitted`() = runTest {
        val raw = NtsocialEnvelopeCodec.encode(testHeaderMsgId(), "durable".encodeToByteArray().toByteString())
        everySuspend { packetRepository.getPacketByPacketId(77) } returns null

        val queued =
            repository.persistAndQueueRawEnvelope(
                rawEnvelope = raw,
                to = DataPacket.ID_BROADCAST,
                channelIndex = 1,
                hopLimit = 3,
                wantAck = true,
                packetId = 77,
            )

        verifySuspend { packetRepository.savePacket(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(listOf(77), messageQueue.packetIds)
        assertEquals(77, queued.packetId)
        assertTrue(commandSender.sentData.isEmpty())
    }

    @Test
    fun `durable native broadcast text captures identity and origin before queue admission`() = runTest {
        val clientMessageId = "0123456789ABCDEF0123456789ABCDEF"
        val channelIdentity =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 1,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = radioConfigRepository.currentChannelSet.settings[1],
                ),
            )
        everySuspend { packetRepository.getGatewayMessageChangeByPacketId(77) } returns null

        val queued =
            repository.persistAndQueueNativeBroadcastText(
                text = "native text",
                sourceChannelId = channelIdentity.sourceChannelId,
                channelIndex = 1,
                packetId = 77,
                originClientMessageId = clientMessageId,
            )
        val expectedIdentity = requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channelIdentity, queued))

        assertEquals(DataPacket.ID_BROADCAST, queued.to)
        assertEquals("!12345678", queued.from)
        assertEquals(1, queued.dataType)
        assertEquals("native text", queued.text)
        assertEquals(MessageStatus.QUEUED, queued.status)
        verifySuspend {
            packetRepository.savePacket(
                myNodeNum = 0x12345678,
                contactKey = "1${DataPacket.ID_BROADCAST}",
                packet = queued,
                receivedTime = queued.time,
                gatewayIdentity = expectedIdentity,
                originClientMessageId = clientMessageId,
            )
        }
        assertEquals(listOf(77), messageQueue.packetIds)
        assertTrue(commandSender.sentData.isEmpty())
    }

    @Test
    fun `durable native retry exact-checks existing row and re-admits queued work`() = runTest {
        val clientMessageId = "FEDCBA9876543210FEDCBA9876543210"
        val channelIdentity =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 1,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = radioConfigRepository.currentChannelSet.settings[1],
                ),
            )
        val existingPacket =
            DataPacket(to = DataPacket.ID_BROADCAST, channel = 1, text = "retry").apply {
                from = "!12345678"
                id = 88
                status = MessageStatus.QUEUED
            }
        val existing =
            NtsocialGatewayMessageChange(
                changeSeq = 9,
                identity = requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channelIdentity, existingPacket)),
                packet = existingPacket,
                receivedAtMillis = existingPacket.time,
                originClientMessageId = clientMessageId,
            )
        everySuspend { packetRepository.getGatewayMessageChangeByPacketId(88) } returns existing

        val queued =
            repository.persistAndQueueNativeBroadcastText(
                text = "retry",
                sourceChannelId = channelIdentity.sourceChannelId,
                channelIndex = 1,
                packetId = 88,
                originClientMessageId = clientMessageId,
            )

        assertEquals(existingPacket, queued)
        verifySuspend(mode = VerifyMode.not) {
            packetRepository.savePacket(any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals(listOf(88), messageQueue.packetIds)
    }

    @Test
    fun `concurrent native retries persist one chat row and safely re-admit queued work`() = runTest {
        val clientMessageId = "ABCDEF0123456789ABCDEF0123456789"
        val channelIdentity =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 1,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = radioConfigRepository.currentChannelSet.settings[1],
                ),
            )
        var persisted: NtsocialGatewayMessageChange? = null
        var saveCount = 0
        everySuspend { packetRepository.getGatewayMessageChangeByPacketId(90) } calls { persisted }
        everySuspend { packetRepository.savePacket(any(), any(), any(), any(), any(), any(), any(), any()) } calls
            { call ->
                saveCount++
                yield()
                val savedPacket: DataPacket = call.arg(2)
                persisted =
                    NtsocialGatewayMessageChange(
                        changeSeq = 1,
                        identity = call.arg(6),
                        packet = savedPacket,
                        receivedAtMillis = call.arg(3),
                        originClientMessageId = call.arg(7),
                    )
            }

        val queued =
            listOf(
                async {
                    repository.persistAndQueueNativeBroadcastText(
                        text = "one durable row",
                        sourceChannelId = channelIdentity.sourceChannelId,
                        channelIndex = 1,
                        packetId = 90,
                        originClientMessageId = clientMessageId,
                    )
                },
                async {
                    repository.persistAndQueueNativeBroadcastText(
                        text = "one durable row",
                        sourceChannelId = channelIdentity.sourceChannelId,
                        channelIndex = 1,
                        packetId = 90,
                        originClientMessageId = clientMessageId,
                    )
                },
            )
                .awaitAll()

        assertEquals(1, saveCount)
        assertEquals(queued.first(), queued.last())
        assertEquals(listOf(90, 90), messageQueue.packetIds)
    }

    @Test
    fun `native send rejects nonbroadcast substitutions through fixed repository construction`() = runTest {
        val channelIdentity =
            NtsocialGatewayIdentity.channel(
                org.meshtastic.proto.Channel(
                    index = 1,
                    role = org.meshtastic.proto.Channel.Role.SECONDARY,
                    settings = radioConfigRepository.currentChannelSet.settings[1],
                ),
            )
        everySuspend { packetRepository.getGatewayMessageChangeByPacketId(99) } returns null

        val queued =
            repository.persistAndQueueNativeBroadcastText(
                text = "broadcast only",
                sourceChannelId = channelIdentity.sourceChannelId,
                channelIndex = 1,
                packetId = 99,
                originClientMessageId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )

        assertEquals(DataPacket.ID_BROADCAST, queued.to)
        assertEquals(1, queued.dataType)
    }

    private fun dataPacket(portNum: Int, raw: ByteString) = DataPacket(
        from = "!00000001",
        to = DataPacket.ID_BROADCAST,
        bytes = raw,
        dataType = portNum,
        id = 42,
        channel = 1,
    )

    private fun testHeaderMsgId() =
        ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) { index -> (index + 1).toByte() }.toByteString()

    private suspend fun activateInbound(target: NtsocialGatewayRepositoryImpl) {
        assertTrue(target.activateInboundSession(radioInterfaceService.radioSessionState.value.epoch))
    }

    private companion object {
        const val RADIO_A = "xAA:AA:AA:AA:AA:AA"
        const val RADIO_B = "xBB:BB:BB:BB:BB:BB"
    }

    private class RecordingCommandSender : CommandSender {
        val sentData = mutableListOf<DataPacket>()
        var adminSendCount = 0
        private var nextPacketId = 100

        override fun getCurrentPacketId(): Long = nextPacketId.toLong()

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = nextPacketId++

        override fun sendData(p: DataPacket) {
            sentData += p.copy()
        }

        override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
            adminSendCount++
        }

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ): Boolean {
            adminSendCount++
            return false
        }

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun requestPositionOnChannel(destNum: Int, currentPosition: Position, channelIndex: Int) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit
    }

    private class RecordingMessageQueue : MessageQueue {
        val packetIds = mutableListOf<Int>()

        override suspend fun enqueue(packetId: Int) {
            packetIds += packetId
        }
    }
}
