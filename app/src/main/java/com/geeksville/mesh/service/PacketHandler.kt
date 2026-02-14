/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
@Singleton
class PacketHandler
@Inject
constructor(
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val connectionStateHolder: ConnectionStateHandler,
) {

    companion object {
        private val TIMEOUT = 5.seconds // Increased from 250ms to be more tolerant
    }

    private var queueJob: Job? = null
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val queuedPackets = ConcurrentLinkedQueue<MeshPacket>()
    private val queueResponse = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Send a command/packet to our radio. But cope with the possibility that we might start up before we are fully
     * bound to the RadioInterfaceService
     */
    fun sendToRadio(p: ToRadio) {
        Logger.d { "Sending to radio ${p.toPIIString()}" }
        val b = p.encode()

        radioInterfaceService.sendToRadio(b)
        p.packet?.id?.let { changeStatus(it, MessageStatus.ENROUTE) }

        val packet = p.packet
        if (packet?.decoded != null) {
            val packetToSave =
                MeshLog(
                    uuid = UUID.randomUUID().toString(),
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = packet.toString(),
                    fromNum = packet.from ?: 0,
                    portNum = packet.decoded?.portnum?.value ?: 0,
                    fromRadio = FromRadio(packet = packet),
                )
            insertMeshLog(packetToSave)
        }
    }

    /**
     * Send a mesh packet to the radio, if the radio is not currently connected this function will throw
     * NotConnectedException
     */
    fun sendToRadio(packet: MeshPacket) {
        queuedPackets.add(packet)
        startPacketQueue()
    }

    fun stopPacketQueue() {
        if (queueJob?.isActive == true) {
            Logger.i { "Stopping packet queueJob" }
            queueJob?.cancel()
            queueJob = null
            queuedPackets.clear()
            queueResponse.entries.lastOrNull { !it.value.isCompleted }?.value?.complete(false)
            queueResponse.clear()
        }
    }

    fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, mesh_packet_id) }
        if (success && isFull) return // Queue is full, wait for free != 0
        if (requestId != 0) {
            queueResponse.remove(requestId)?.complete(success)
        } else {
            // This is slightly suboptimal but matches legacy behavior for packets without IDs
            queueResponse.values.firstOrNull { !it.isCompleted }?.complete(success)
        }
    }

    fun removeResponse(dataRequestId: Int, complete: Boolean) {
        queueResponse.remove(dataRequestId)?.complete(complete)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun startPacketQueue() {
        if (queueJob?.isActive == true) return
        queueJob =
            scope.handledLaunch {
                Logger.d { "packet queueJob started" }
                while (connectionStateHolder.connectionState.value == ConnectionState.Connected) {
                    // take the first packet from the queue head
                    val packet = queuedPackets.poll() ?: break
                    try {
                        // send packet to the radio and wait for response
                        val response = sendPacket(packet)
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
                        val success = withTimeout(TIMEOUT) { response.await() }
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} success $success" }
                    } catch (e: TimeoutCancellationException) {
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
                    } catch (e: Exception) {
                        Logger.d { "queueJob packet id=${packet.id.toUInt()} failed" }
                    } finally {
                        queueResponse.remove(packet.id)
                    }
                }
            }
    }

    /** Change the status on a DataPacket and update watchers */
    private fun changeStatus(packetId: Int, m: MessageStatus) = scope.handledLaunch {
        if (packetId != 0) {
            getDataPacketById(packetId)?.let { p ->
                if (p.status == m) return@handledLaunch
                packetRepository.get().updateMessageStatus(p, m)
                serviceBroadcasts.broadcastMessageStatus(packetId, m)
            }
        }
    }

    @Suppress("MagicNumber")
    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1.seconds) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.get().getPacketById(packetId)?.data
            if (dataPacket == null) delay(100.milliseconds)
        }
        dataPacket
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendPacket(packet: MeshPacket): CompletableDeferred<Boolean> {
        // send the packet to the radio and return a CompletableDeferred that will be completed with
        // the result
        val deferred = CompletableDeferred<Boolean>()
        queueResponse[packet.id] = deferred
        try {
            if (connectionStateHolder.connectionState.value != ConnectionState.Connected) {
                throw RadioNotConnectedException()
            }
            sendToRadio(ToRadio(packet = packet))
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            deferred.complete(false)
        }
        return deferred
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        scope.handledLaunch {
            // Do not log, because might contain PII

            Logger.d {
                "insert: ${packetToSave.message_type} = " +
                    "${packetToSave.raw_message.toOneLineString()} from=${packetToSave.fromNum}"
            }
            meshLogRepository.get().insert(packetToSave)
        }
    }
}
