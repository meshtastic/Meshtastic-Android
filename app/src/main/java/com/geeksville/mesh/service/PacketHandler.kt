/*
 * Copyright (c) 2025 Meshtastic LLC
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

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MeshProtos.ToRadio
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.BuildUtils.info
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.fromRadio
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import dagger.Lazy
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PacketHandler(
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
) {

    private var queueJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val queuedPackets = ConcurrentLinkedQueue<MeshPacket>()
    private val queueResponse = mutableMapOf<Int, CompletableFuture<Boolean>>()

    /**
     * Send a command/packet to our radio. But cope with the possibility that we might start up before we are fully
     * bound to the RadioInterfaceService
     */
    fun sendToRadio(p: ToRadio.Builder) {
        val built = p.build()
        debug("Sending to radio ${built.toPIIString()}")
        val b = built.toByteArray()

        radioInterfaceService.sendToRadio(b)
        changeStatus(p.packet.id, MessageStatus.ENROUTE)

        if (p.packet.hasDecoded()) {
            val packetToSave =
                MeshLog(
                    uuid = UUID.randomUUID().toString(),
                    message_type = "Packet",
                    received_date = System.currentTimeMillis(),
                    raw_message = p.packet.toString(),
                    fromNum = p.packet.from,
                    portNum = p.packet.decoded.portnumValue,
                    fromRadio = fromRadio { packet = p.packet },
                )
            insertMeshLog(packetToSave)
        }
    }

    /**
     * Send a mesh packet to the radio, if the radio is not currently connected this function will throw
     * NotConnectedException
     */
    fun sendToRadio(packet: MeshPacket, getConnectionState: () -> ConnectionState) {
        queuedPackets.add(packet)
        startPacketQueue(getConnectionState)
    }

    fun stopPacketQueue() {
        if (queueJob?.isActive == true) {
            info("Stopping packet queueJob")
            queueJob?.cancel()
            queueJob = null
            queuedPackets.clear()
            queueResponse.entries.lastOrNull { !it.value.isDone }?.value?.complete(false)
            queueResponse.clear()
        }
    }

    fun handleQueueStatus(queueStatus: MeshProtos.QueueStatus) {
        debug("queueStatus ${queueStatus.toOneLineString()}")
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, meshPacketId) }
        if (success && isFull) return // Queue is full, wait for free != 0
        if (requestId != 0) {
            queueResponse.remove(requestId)?.complete(success)
        } else {
            queueResponse.entries.lastOrNull { !it.value.isDone }?.value?.complete(success)
        }
    }

    fun removeResponse(dataRequestId: Int, complete: Boolean) {
        queueResponse.remove(dataRequestId)?.complete(complete)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun startPacketQueue(getConnectionState: () -> ConnectionState) {
        if (queueJob?.isActive == true) return
        queueJob =
            scope.handledLaunch {
                debug("packet queueJob started")
                while (getConnectionState() == ConnectionState.CONNECTED) {
                    // take the first packet from the queue head
                    val packet = queuedPackets.poll() ?: break
                    try {
                        // send packet to the radio and wait for response
                        val response = sendPacket(packet, getConnectionState)
                        debug("queueJob packet id=${packet.id.toUInt()} waiting")
                        val success = response.get(2, TimeUnit.MINUTES)
                        debug("queueJob packet id=${packet.id.toUInt()} success $success")
                    } catch (e: TimeoutException) {
                        debug("queueJob packet id=${packet.id.toUInt()} timeout")
                    } catch (e: Exception) {
                        debug("queueJob packet id=${packet.id.toUInt()} failed")
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
    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1000) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.get().getPacketById(packetId)?.data
            if (dataPacket == null) delay(100)
        }
        dataPacket
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendPacket(packet: MeshPacket, getConnectionState: () -> ConnectionState): CompletableFuture<Boolean> {
        // send the packet to the radio and return a CompletableFuture that will be completed with
        // the result
        val future = CompletableFuture<Boolean>()
        queueResponse[packet.id] = future
        try {
            if (getConnectionState() != ConnectionState.CONNECTED) throw RadioNotConnectedException()
            sendToRadio(ToRadio.newBuilder().apply { this.packet = packet })
        } catch (ex: Exception) {
            errormsg("sendToRadio error:", ex)
            future.complete(false)
        }
        return future
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        scope.handledLaunch {
            // Do not log, because might contain PII
            // info("insert: ${packetToSave.message_type} =
            // ${packetToSave.raw_message.toOneLineString()}")
            meshLogRepository.get().insert(packetToSave)
        }
    }
}
