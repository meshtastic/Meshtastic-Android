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

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.info
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.util.toOneLineString
import dagger.Lazy
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PacketHandler(
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: MeshServiceBroadcasts,
) {

    private var queueJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val queuedPackets = ConcurrentLinkedQueue<MeshPacket>()
    private val queueResponse = mutableMapOf<Int, CompletableFuture<Boolean>>()

    fun startPacketQueue(getConnectionState: () -> ConnectionState) {
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

    /** Change the status on a DataPacket and update watchers */
    fun changeStatus(packetId: Int, m: MessageStatus) = scope.handledLaunch {
        if (packetId != 0) {
            getDataPacketById(packetId)?.let { p ->
                if (p.status == m) return@handledLaunch
                packetRepository.get().updateMessageStatus(p, m)
                serviceBroadcasts.broadcastMessageStatus(packetId, m)
            }
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

    fun addPacket(packet: MeshPacket) = queuedPackets.add(packet)

    fun setResponse(packetId: Int, future: CompletableFuture<Boolean>) {
        queueResponse[packetId] = future
    }

    fun removeResponse(dataRequestId: Int, complete: Boolean) {
        queueResponse.remove(dataRequestId)?.complete(complete)
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1000) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.get().getPacketById(packetId)?.data
            if (dataPacket == null) delay(100)
        }
        dataPacket
    }
}
