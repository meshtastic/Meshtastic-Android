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
@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ntsocial.meshlink.core.data.repository

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.data.manager.RadioIngressWorkTracker
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.DataPacket
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialDefaultChannelStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeDirection
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageChange
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayMessageIdentity
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayNativeText
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialTransport
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import kotlin.concurrent.Volatile
import kotlin.random.Random

// This repository intentionally owns both overlay and native-text admission surfaces so their shared
// insertion/idempotency mutexes cannot be bypassed. The broad history-flow catch is a long-lived service boundary:
// cancellation is rethrown immediately and an unavailable database is reported without killing the service scope.
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@Single(binds = [NtsocialGatewayRepository::class])
class NtsocialGatewayRepositoryImpl(
    private val commandSender: CommandSender,
    private val packetRepository: PacketRepository,
    private val messageQueue: MessageQueue,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val databaseManager: DatabaseManager,
    private val ingressWorkTracker: RadioIngressWorkTracker,
    @Named("ServiceScope") private val scope: CoroutineScope,
    private val ingressSessionGate: GatewayIngressSessionGate,
) : NtsocialGatewayRepository {
    private val _cachedEnvelopes = MutableStateFlow<List<NtsocialCachedEnvelope>>(emptyList())
    private val _defaultChannelStatus = MutableStateFlow(NtsocialDefaultChannelStatus())
    private val cacheMutex = Mutex()
    private val nativeTextMutex = Mutex()
    private val inboundIdentityMutex = Mutex()
    private val seenCacheKeys = mutableSetOf<String>()
    private val inboundActivationState = atomic(InboundActivationState())
    private val _inboundSessionRevision = MutableStateFlow(0L)

    internal var beforeInboundIdentityClearCompareAndSetForTest: (() -> Unit)? = null

    @Volatile private var currentChannelSet = ChannelSet()

    @Volatile private var currentHistoryEpoch: String? = null

    init {
        scope.launch { radioConfigRepository.channelSetFlow.collectLatest { currentChannelSet = it } }
        scope.launch {
            radioConfigRepository.channelSnapshotGeneration.collectLatest { generation ->
                inboundIdentityMutex.withLock {
                    val current = inboundActivationState.value
                    if (current.identity?.channelSnapshotGeneration == generation && current.identity.isCurrent()) {
                        return@withLock
                    }
                    val capturedGate = clearPublishedIdentity(current) ?: return@withLock
                    val expectedEpoch = capturedGate.expectedRadioSessionEpoch
                    if (generation.isMutationInFlight() || expectedEpoch == null) return@withLock
                    val session = radioInterfaceService.radioSessionState.value
                    if (
                        radioConfigRepository.channelReadbackGeneration.value > 0 &&
                        session.isConfiguredReady &&
                        session.epoch == expectedEpoch
                    ) {
                        refreshInboundSession(expectedEpoch, capturedGate)
                    }
                }
            }
        }
        scope.launch {
            try {
                packetRepository.getGatewayHistoryState(emptyList()).collectLatest {
                    currentHistoryEpoch = it.historyEpoch
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(error) { "Gateway history domain is not available yet" }
            }
        }
    }

    override val cachedEnvelopes: StateFlow<List<NtsocialCachedEnvelope>> = _cachedEnvelopes.asStateFlow()

    override val inboundSessionRevision: StateFlow<Long> = _inboundSessionRevision.asStateFlow()

    override val defaultChannelStatus: StateFlow<NtsocialDefaultChannelStatus> = _defaultChannelStatus.asStateFlow()

    override suspend fun activateInboundSession(expectedRadioSessionEpoch: Long): Boolean =
        inboundIdentityMutex.withLock {
            val capturedGate = rotateInboundActivation(replacementExpectedEpoch = expectedRadioSessionEpoch)
            refreshInboundSession(expectedRadioSessionEpoch, capturedGate).also { active ->
                Logger.i {
                    "gateway_ingress stage=activation epoch=$expectedRadioSessionEpoch active=$active " +
                        "gateActive=${ingressSessionGate.isActive(expectedRadioSessionEpoch)}"
                }
            }
        }

    override fun invalidateInboundSession() {
        rotateInboundActivation(replacementExpectedEpoch = null)
    }

    override fun isInboundSessionActive(expectedRadioSessionEpoch: Long): Boolean {
        if (!ingressSessionGate.isActive(expectedRadioSessionEpoch)) return false
        val state = inboundActivationState.value
        val identity = state.identity ?: return false
        return state.expectedRadioSessionEpoch == expectedRadioSessionEpoch &&
            identity.radioSessionEpoch == expectedRadioSessionEpoch &&
            identity.isCurrent()
    }

    override fun cacheInbound(packet: MeshPacket, dataPacket: DataPacket): Boolean {
        val activationState = inboundActivationState.value
        val identity = activationState.identity
        val record =
            when {
                identity == null -> null

                !identity.isCurrent() -> {
                    if (inboundActivationState.compareAndSet(activationState, activationState.copy(identity = null))) {
                        ingressSessionGate.invalidate()
                        _inboundSessionRevision.update { it + 1 }
                    }
                    null
                }

                else ->
                    toCacheRecord(
                        packet = packet,
                        dataPacket = dataPacket,
                        direction = NtsocialEnvelopeDirection.INBOUND,
                        channelSet = identity.channelSet,
                        historyEpoch = identity.historyEpoch,
                    )
            }
        return record?.also(::cacheInboundRecord) != null
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "ComplexCondition")
    private suspend fun refreshInboundSession(
        expectedRadioSessionEpoch: Long,
        capturedGate: InboundActivationState,
    ): Boolean {
        if (
            capturedGate.expectedRadioSessionEpoch != expectedRadioSessionEpoch ||
            capturedGate.identity != null ||
            inboundActivationState.value !== capturedGate
        ) {
            return false
        }
        val session = radioInterfaceService.radioSessionState.value
        val selectedAddress = session.selectedDeviceAddress
        val readbackGeneration = radioConfigRepository.channelReadbackGeneration.value
        val snapshotGeneration = radioConfigRepository.channelSnapshotGeneration.value
        if (
            selectedAddress == null ||
            !session.isExactConfiguredSession(expectedRadioSessionEpoch) ||
            databaseManager.currentAddress.value != selectedAddress ||
            readbackGeneration <= 0 ||
            snapshotGeneration.isMutationInFlight()
        ) {
            return false
        }

        val channelSet = radioConfigRepository.channelSetFlow.first()
        val historyState =
            try {
                packetRepository.readCurrentGatewayHistoryState(emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(error) { "Unable to capture exact Gateway ingress history identity" }
                return false
            }

        val currentSession = radioInterfaceService.radioSessionState.value
        if (
            inboundActivationState.value !== capturedGate ||
            !currentSession.isExactConfiguredSession(expectedRadioSessionEpoch) ||
            currentSession.selectedDeviceAddress != selectedAddress ||
            databaseManager.currentAddress.value != selectedAddress ||
            radioConfigRepository.channelReadbackGeneration.value != readbackGeneration ||
            radioConfigRepository.channelSnapshotGeneration.value != snapshotGeneration
        ) {
            return false
        }

        val published =
            inboundActivationState.compareAndSet(
                capturedGate,
                capturedGate.copy(
                    identity =
                    GatewayIngressIdentity(
                        radioSessionEpoch = expectedRadioSessionEpoch,
                        radioAddress = selectedAddress,
                        channelReadbackGeneration = readbackGeneration,
                        channelSnapshotGeneration = snapshotGeneration,
                        historyEpoch = historyState.historyEpoch,
                        channelSet = channelSet,
                    ),
                ),
            )
        if (published) {
            ingressSessionGate.publish(expectedRadioSessionEpoch)
            _inboundSessionRevision.update { it + 1 }
        }
        return published
    }

    private fun rotateInboundActivation(replacementExpectedEpoch: Long?): InboundActivationState {
        while (true) {
            val current = inboundActivationState.value
            val replacement =
                InboundActivationState(
                    revision = current.revision + 1,
                    expectedRadioSessionEpoch = replacementExpectedEpoch,
                )
            if (inboundActivationState.compareAndSet(current, replacement)) {
                ingressSessionGate.invalidate()
                _inboundSessionRevision.update { it + 1 }
                return replacement
            }
        }
    }

    @Suppress("ReturnCount")
    private fun clearPublishedIdentity(current: InboundActivationState): InboundActivationState? {
        var candidate = current
        while (true) {
            if (
                candidate.revision != current.revision ||
                candidate.expectedRadioSessionEpoch != current.expectedRadioSessionEpoch
            ) {
                return null
            }
            if (candidate.identity == null) return candidate
            val replacement = candidate.copy(identity = null)
            beforeInboundIdentityClearCompareAndSetForTest?.let { hook ->
                beforeInboundIdentityClearCompareAndSetForTest = null
                hook()
            }
            if (inboundActivationState.compareAndSet(candidate, replacement)) {
                ingressSessionGate.invalidate()
                _inboundSessionRevision.update { it + 1 }
                return replacement
            }
            candidate = inboundActivationState.value
        }
    }

    override fun sendTestPayload(
        payload: ByteString,
        to: String?,
        channelIndex: Int,
        wantAck: Boolean,
        headerMsgId: ByteString?,
    ): NtsocialCachedEnvelope {
        val msgId = headerMsgId ?: randomHeaderMsgId()
        val rawEnvelope = NtsocialEnvelopeCodec.encode(headerMsgId = msgId, payload = payload)
        val dataPacket =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = commandSender.generatePacketId(),
                channel = channelIndex,
                wantAck = wantAck,
            )

        commandSender.sendData(dataPacket)

        val record =
            NtsocialCachedEnvelope(
                direction = NtsocialEnvelopeDirection.OUTBOUND,
                envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)),
                rawBytes = rawEnvelope,
                packetId = dataPacket.id,
                from = dataPacket.from,
                to = dataPacket.to,
                channelIndex = dataPacket.channel,
                portNum = dataPacket.dataType,
                cachedAtMillis = nowMillis,
                sourceChannelId = sourceChannelIdAtInsertion(dataPacket.channel),
                historyEpoch = currentHistoryEpoch,
            )
        cache(record)
        return record
    }

    override fun sendRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int?,
    ): NtsocialCachedEnvelope {
        require(rawEnvelope.size <= NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES) {
            "NTsocial command envelope exceeds the external gateway limit"
        }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(hopLimit >= 0) { "hopLimit must not be negative" }

        val envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)) { "Invalid NTsocial command envelope" }
        val dataPacket =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = packetId ?: commandSender.generatePacketId(),
                channel = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
            )
        Logger.i {
            "ntsocial_gateway_tx stage=data_packet packetId=${dataPacket.id} channelIndex=${dataPacket.channel} " +
                "port=${dataPacket.dataType} bytes=${rawEnvelope.size} wantAck=$wantAck"
        }
        commandSender.sendData(dataPacket)
        Logger.i {
            "ntsocial_gateway_tx stage=command_sender_return packetId=${dataPacket.id} status=${dataPacket.status}"
        }

        return NtsocialCachedEnvelope(
            direction = NtsocialEnvelopeDirection.OUTBOUND,
            envelope = envelope,
            rawBytes = rawEnvelope,
            packetId = dataPacket.id,
            from = dataPacket.from,
            to = dataPacket.to,
            channelIndex = dataPacket.channel,
            portNum = dataPacket.dataType,
            cachedAtMillis = nowMillis,
            sourceChannelId = sourceChannelIdAtInsertion(dataPacket.channel),
            historyEpoch = currentHistoryEpoch,
        )
            .also(::cache)
    }

    override suspend fun persistAndQueueRawEnvelope(
        rawEnvelope: ByteString,
        sourceChannelId: String?,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): NtsocialCachedEnvelope {
        val prepared =
            prepareRawEnvelope(
                rawEnvelope = rawEnvelope,
                to = to,
                channelIndex = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
                packetId = packetId,
            )
        val packet = prepared.first
        val exactChannelSet = radioConfigRepository.channelSetFlow.first()
        val record = prepared.second.copy(sourceChannelId = sourceChannelIdAtInsertion(channelIndex, exactChannelSet))
        val durableSourceChannelId = requireNotNull(record.sourceChannelId) { "Gateway source channel is unavailable" }
        require(sourceChannelId == null || sourceChannelId == durableSourceChannelId) {
            "Gateway route no longer matches its channel"
        }
        val existing = packetRepository.getDurablePacketByPacketId(packetId)
        when {
            existing == null ->
                packetRepository.savePacket(
                    myNodeNum = 0,
                    contactKey = "$channelIndex${to ?: DataPacket.ID_BROADCAST}",
                    packet = packet,
                    receivedTime = nowMillis,
                    expectedGatewaySourceChannelId = durableSourceChannelId,
                )

            !existing.packet.matchesDurableGatewayPacket(packet) ||
                existing.expectedSourceChannelId != durableSourceChannelId ->
                throw IllegalArgumentException("Gateway packet ID already belongs to different content")
        }

        if (existing == null || existing.packet.status == MessageStatus.QUEUED) {
            messageQueue.enqueue(packetId)
        }
        cache(record)
        return record
    }

    override suspend fun persistAndQueueNativeBroadcastText(
        text: String,
        sourceChannelId: String,
        channelIndex: Int,
        packetId: Int,
        originClientMessageId: String,
    ): DataPacket {
        require(NtsocialGatewayNativeText.isValid(text)) { "Native channel text is empty or exceeds the UTF-8 limit" }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(packetId > 0) { "packetId must be positive" }
        require(CLIENT_MESSAGE_ID_REGEX.matches(originClientMessageId)) { "originClientMessageId must be canonical" }

        return nativeTextMutex.withLock {
            val channelSet = radioConfigRepository.channelSetFlow.first()
            val settings =
                requireNotNull(channelSet.settings.getOrNull(channelIndex)) { "Gateway route no longer exists" }
            val channelIdentity =
                NtsocialGatewayIdentity.channel(
                    Channel(
                        index = channelIndex,
                        role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
                        settings = settings,
                    ),
                    channelSet.lora_config ?: Config.LoRaConfig(),
                )
            require(channelIdentity.sourceChannelId == sourceChannelId) {
                "Gateway route no longer matches its channel"
            }

            val sender = nodeRepository.requireNativeSenderIdentity()
            val packet =
                DataPacket(to = DataPacket.ID_BROADCAST, channel = channelIndex, text = text).apply {
                    from = sender.nodeId
                    id = packetId
                    status = MessageStatus.QUEUED
                    time = nowMillis
                }
            val gatewayIdentity =
                requireNotNull(NtsocialGatewayIdentity.nativeBroadcastText(channelIdentity, packet)) {
                    "Native channel text did not produce a stable Gateway identity"
                }

            val existing = packetRepository.getGatewayMessageChangeByPacketId(packetId)
            when {
                existing == null ->
                    packetRepository.savePacket(
                        myNodeNum = sender.nodeNum ?: 0,
                        contactKey = "$channelIndex${DataPacket.ID_BROADCAST}",
                        packet = packet,
                        receivedTime = packet.time,
                        gatewayIdentity = gatewayIdentity,
                        originClientMessageId = originClientMessageId,
                    )

                !existing.matchesDurableNativeText(packet, gatewayIdentity, originClientMessageId) ->
                    throw IllegalArgumentException("Gateway packet ID already belongs to different content")
            }

            if (existing == null || existing.packet.status == MessageStatus.QUEUED) {
                messageQueue.enqueue(packetId)
            }
            existing?.packet ?: packet
        }
    }

    override fun updateDefaultChannelStatus(status: NtsocialDefaultChannelStatus) {
        _defaultChannelStatus.value = status
    }

    override fun clearCache() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cacheMutex.withLock {
                seenCacheKeys.clear()
                _cachedEnvelopes.value = emptyList()
            }
        }
    }

    private fun toCacheRecord(
        packet: MeshPacket,
        dataPacket: DataPacket,
        direction: NtsocialEnvelopeDirection,
        channelSet: ChannelSet = currentChannelSet,
        historyEpoch: String? = currentHistoryEpoch,
    ): NtsocialCachedEnvelope? {
        val rawBytes = dataPacket.bytes
        val envelope =
            rawBytes
                ?.takeIf { NtsocialTransport.isInboundPort(dataPacket.dataType) }
                ?.let(NtsocialEnvelopeCodec::decode)

        return envelope?.let {
            NtsocialCachedEnvelope(
                direction = direction,
                envelope = it,
                rawBytes = rawBytes,
                packetId = packet.id,
                from = dataPacket.from,
                to = dataPacket.to,
                channelIndex = dataPacket.channel,
                portNum = dataPacket.dataType,
                cachedAtMillis = nowMillis,
                sourceChannelId = sourceChannelIdAtInsertion(dataPacket.channel, channelSet),
                historyEpoch = historyEpoch,
            )
        }
    }

    private fun prepareRawEnvelope(
        rawEnvelope: ByteString,
        to: String?,
        channelIndex: Int,
        hopLimit: Int,
        wantAck: Boolean,
        packetId: Int,
    ): Pair<DataPacket, NtsocialCachedEnvelope> {
        require(rawEnvelope.size <= NtsocialTransport.MAX_CLIENT_ENVELOPE_SIZE_BYTES) {
            "NTsocial command envelope exceeds the external gateway limit"
        }
        require(channelIndex >= 0) { "channelIndex must not be negative" }
        require(hopLimit >= 0) { "hopLimit must not be negative" }
        require(packetId > 0) { "packetId must be positive" }

        val envelope = requireNotNull(NtsocialEnvelopeCodec.decode(rawEnvelope)) { "Invalid NTsocial command envelope" }
        val packet =
            DataPacket(
                to = to,
                bytes = rawEnvelope,
                dataType = NtsocialTransport.PRIVATE_APP_PORT_NUM,
                id = packetId,
                channel = channelIndex,
                hopLimit = hopLimit,
                wantAck = wantAck,
            )
                .apply {
                    from = DataPacket.ID_LOCAL
                    status = MessageStatus.QUEUED
                    time = nowMillis
                }
        return packet to
            NtsocialCachedEnvelope(
                direction = NtsocialEnvelopeDirection.OUTBOUND,
                envelope = envelope,
                rawBytes = rawEnvelope,
                packetId = packet.id,
                from = packet.from,
                to = packet.to,
                channelIndex = packet.channel,
                portNum = packet.dataType,
                cachedAtMillis = nowMillis,
                sourceChannelId = sourceChannelIdAtInsertion(packet.channel),
                historyEpoch = currentHistoryEpoch,
            )
    }

    private fun DataPacket.matchesDurableGatewayPacket(expected: DataPacket): Boolean = id == expected.id &&
        bytes == expected.bytes &&
        dataType == expected.dataType &&
        to == expected.to &&
        channel == expected.channel &&
        hopLimit == expected.hopLimit &&
        wantAck == expected.wantAck

    private fun NtsocialGatewayMessageChange.matchesDurableNativeText(
        expectedPacket: DataPacket,
        expectedIdentity: NtsocialGatewayMessageIdentity,
        expectedOriginClientMessageId: String,
    ): Boolean = packet.id == expectedPacket.id &&
        packet.bytes == expectedPacket.bytes &&
        packet.dataType == expectedPacket.dataType &&
        packet.from == expectedPacket.from &&
        packet.to == DataPacket.ID_BROADCAST &&
        packet.channel == expectedPacket.channel &&
        packet.hopLimit == expectedPacket.hopLimit &&
        packet.wantAck == expectedPacket.wantAck &&
        identity == expectedIdentity &&
        originClientMessageId == expectedOriginClientMessageId

    private fun cache(record: NtsocialCachedEnvelope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { applyCache(record) }
    }

    private fun cacheInboundRecord(record: NtsocialCachedEnvelope) {
        ingressWorkTracker.launchUndispatched(scope) { applyCache(record) }
    }

    private suspend fun applyCache(record: NtsocialCachedEnvelope) {
        cacheMutex.withLock {
            if (!seenCacheKeys.add(record.cacheKey)) return@withLock

            val next = (_cachedEnvelopes.value + record).takeLast(NtsocialTransport.MAX_CACHED_ENVELOPES)
            if (next.size == NtsocialTransport.MAX_CACHED_ENVELOPES) {
                seenCacheKeys.clear()
                seenCacheKeys.addAll(next.map { it.cacheKey })
            }
            _cachedEnvelopes.value = next
        }
    }

    private fun sourceChannelIdAtInsertion(channelIndex: Int, channelSet: ChannelSet = currentChannelSet): String? {
        val settings = channelSet.settings.getOrNull(channelIndex) ?: return null
        val role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
        return NtsocialGatewayIdentity.channel(
            Channel(index = channelIndex, role = role, settings = settings),
            channelSet.lora_config ?: Config.LoRaConfig(),
        )
            .sourceChannelId
    }

    private fun randomHeaderMsgId(): ByteString = ByteArray(NtsocialTransport.HEADER_MSG_ID_SIZE_BYTES) {
        Random.nextInt(from = 0, until = RANDOM_BYTE_EXCLUSIVE).toByte()
    }
        .toByteString()

    private companion object {
        const val RANDOM_BYTE_EXCLUSIVE = 256
        val CLIENT_MESSAGE_ID_REGEX = Regex("^[0-9A-F]{32}$")
    }

    private fun GatewayIngressIdentity.isCurrent(): Boolean {
        val session = radioInterfaceService.radioSessionState.value
        return session.isExactConfiguredSession(radioSessionEpoch) &&
            session.selectedDeviceAddress == radioAddress &&
            databaseManager.currentAddress.value == radioAddress &&
            radioConfigRepository.channelReadbackGeneration.value == channelReadbackGeneration &&
            radioConfigRepository.channelSnapshotGeneration.value == channelSnapshotGeneration
    }

    private fun com.ntsocial.meshlink.core.repository.RadioSessionState.isExactConfiguredSession(
        expectedEpoch: Long,
    ): Boolean = epoch == expectedEpoch &&
        selectedDeviceAddress != null &&
        selectedDeviceAddress == activeDeviceAddress &&
        transportConnectionState == ConnectionState.Connected &&
        configured
}

private data class GatewayIngressIdentity(
    val radioSessionEpoch: Long,
    val radioAddress: String,
    val channelReadbackGeneration: Long,
    val channelSnapshotGeneration: Long,
    val historyEpoch: String,
    val channelSet: ChannelSet,
)

private data class InboundActivationState(
    val revision: Long = 0,
    val expectedRadioSessionEpoch: Long? = null,
    val identity: GatewayIngressIdentity? = null,
)

private fun Long.isMutationInFlight(): Boolean = this and 1L != 0L

private data class NativeSenderIdentity(val nodeNum: Int?, val nodeId: String)

private fun NodeRepository.requireNativeSenderIdentity(): NativeSenderIdentity {
    val ourNode = ourNodeInfo.value
    val nodeNum = ourNode?.num?.takeIf { it != 0 } ?: myNodeInfo.value?.myNodeNum?.takeIf { it != 0 }
    val nodeId =
        requireNotNull(
            NtsocialGatewayIdentity.stableLocalNodeId(userId = ourNode?.user?.id, myId = myId.value, nodeNum = nodeNum),
        ) {
            "Stable local node identity is not ready"
        }
    return NativeSenderIdentity(nodeNum, nodeId)
}
