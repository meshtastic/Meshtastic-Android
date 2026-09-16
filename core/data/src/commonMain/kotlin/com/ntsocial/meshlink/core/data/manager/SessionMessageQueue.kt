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

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.MessageStatus
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayIdentity
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.GatewayPacketDispatchResult
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel as SignalChannel

/**
 * Endpoint-local durable queue for hosts without WorkManager. Room is the work record; signals only wake its reader.
 * Enqueue returns after verifying durable admission and never waits for radio I/O while a caller owns admission locks.
 * Gateway rows revalidate their saved source identity and exact session before every attempt, including retries.
 */
class SessionMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: Lazy<RadioController>,
    private val commandSender: CommandSender,
    private val radioConfigRepository: RadioConfigRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val gatewayIngressSessionGate: GatewayIngressSessionGate,
    parentScope: CoroutineScope,
) : MessageQueue {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val drainSignals = SignalChannel<Unit>(SignalChannel.CONFLATED)
    private val drainMutex = Mutex()
    private var signalJob: Job? = null
    private var retryJob: Job? = null

    fun start() {
        if (signalJob?.isActive == true) return
        signalJob =
            scope.launch {
                for (signal in drainSignals) {
                    retryJob?.cancel()
                    if (drain()) {
                        retryJob =
                            scope.launch {
                                delay(RETRY_DELAY)
                                requestDrain()
                            }
                    }
                }
            }
        radioController.value.connectionState
            .filter { it == ConnectionState.Connected }
            .onEach { requestDrain() }
            .launchIn(scope)
        gatewayIngressSessionGate.activeSessionEpoch.onEach { requestDrain() }.launchIn(scope)
        requestDrain()
    }

    override suspend fun enqueue(packetId: Int) {
        require(packetId > 0) { "packetId must be positive" }
        val packet =
            requireNotNull(packetRepository.getPacketByPacketId(packetId)) {
                "Durable queue admission requires an existing Room packet"
            }
        require(packet.status == MessageStatus.QUEUED || packet.status == MessageStatus.ENROUTE) {
            "Durable queue admission requires QUEUED or already-admitted ENROUTE state"
        }
        Logger.i { "gateway_outbox stage=admitted packetId=$packetId" }
        requestDrain()
    }

    fun requestDrain() {
        drainSignals.trySend(Unit)
    }

    /** Returns whether connected durable work needs a later retry even if no new connection/admission event occurs. */
    @Suppress("TooGenericExceptionCaught", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    suspend fun drain(): Boolean = drainMutex.withLock {
        if (radioController.value.connectionState.value != ConnectionState.Connected) return@withLock false
        val queuedPackets = packetRepository.getDurableQueuedPackets().sortedBy { it.packet.time }
        // A gated Gateway row cannot prevent a native message from making progress.
        val dispatchOrder =
            queuedPackets.filterNot { it.requiresGatewaySession } +
                queuedPackets.filter { it.requiresGatewaySession }
        for (queued in dispatchOrder) {
            val packet = queued.packet
            if (radioController.value.connectionState.value != ConnectionState.Connected) return@withLock false
            try {
                if (!queued.requiresGatewaySession) {
                    radioController.value.sendMessage(packet)
                    packetRepository.updateMessageStatus(packet, MessageStatus.ENROUTE)
                    continue
                }
                val expectedSource = queued.expectedSourceChannelId
                if (expectedSource == null) {
                    Logger.w { "gateway_outbox stage=missing_source packetId=${packet.id}" }
                    packetRepository.updateMessageStatus(packet, MessageStatus.ERROR)
                    continue
                }
                val session = radioInterfaceService.radioSessionState.value
                val gateActive = gatewayIngressSessionGate.isActive(session.epoch)
                if (!session.isConfiguredReady || !gateActive) {
                    Logger.i {
                        "gateway_outbox stage=waiting_session packetId=${packet.id} epoch=${session.epoch} " +
                            "configured=${session.isConfiguredReady} ingressActive=$gateActive"
                    }
                    return@withLock true
                }
                val channelSet = radioConfigRepository.channelSetFlow.first()
                if (channelSet.sourceChannelId(packet.channel) != expectedSource) {
                    Logger.w { "gateway_outbox stage=source_changed packetId=${packet.id}" }
                    packetRepository.updateMessageStatus(packet, MessageStatus.ERROR)
                    continue
                }
                Logger.i { "gateway_outbox stage=dispatch packetId=${packet.id} epoch=${session.epoch}" }
                val result = commandSender.sendDataAwaitForGatewaySession(packet, session.epoch, expectedSource)
                Logger.i { "gateway_outbox stage=dispatch_result packetId=${packet.id} result=$result" }
                when (result) {
                    GatewayPacketDispatchResult.ACCEPTED ->
                        packetRepository.updateMessageStatus(packet, MessageStatus.ENROUTE)

                    GatewayPacketDispatchResult.SOURCE_IDENTITY_MISMATCH ->
                        packetRepository.updateMessageStatus(packet, MessageStatus.ERROR)

                    GatewayPacketDispatchResult.TRANSIENT_FAILURE -> {
                        packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                        return@withLock true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w { "gateway_outbox stage=retry packetId=${packet.id} error=${error::class.simpleName}" }
                packetRepository.updateMessageStatus(packet, MessageStatus.QUEUED)
                return@withLock true
            }
        }
        false
    }

    fun close() {
        drainSignals.close()
        scope.cancel()
    }

    private companion object {
        val RETRY_DELAY = 30.seconds
    }
}

private fun ChannelSet.sourceChannelId(slotIndex: Int): String? {
    val settings = settings.getOrNull(slotIndex) ?: return null
    val role = if (slotIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY
    return NtsocialGatewayIdentity.channel(
        Channel(index = slotIndex, role = role, settings = settings),
        lora_config ?: Config.LoRaConfig(),
    )
        .sourceChannelId
}
