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
package org.meshtastic.core.testing

import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.proto.MeshPacket
import org.meshtastic.sdk.PacedMessageHandle

/** Recording [PacketHandler] for tests that send data packets without a radio session. */
class FakePacketHandler :
    BaseFake(),
    PacketHandler {

    private val mutableSentPackets = mutableListOf<DataPacket>()

    /** Snapshots of every data packet [sendData] accepted, in order. */
    val sentPackets: List<DataPacket>
        get() = mutableSentPackets.map { it.snapshot() }

    /** Thrown by [sendData] after it marks the packet [MessageStatus.ERROR], when set. */
    var sendDataFailure: PacketQueueRejectedException? = null

    private var nextPacketId = 1

    init {
        registerResetAction {
            mutableSentPackets.clear()
            nextPacketId = 1
            sendDataFailure = null
        }
    }

    override val currentPacketId: Long
        get() = nextPacketId.toLong()

    override fun generatePacketId(): Int = nextPacketId++

    override suspend fun sendData(p: DataPacket) {
        require(p.dataType != 0) { "Port numbers must be non-zero!" }
        if (p.id == 0) p.id = generatePacketId()
        sendDataFailure?.let { failure ->
            p.status = MessageStatus.ERROR
            throw failure
        }
        p.status = MessageStatus.QUEUED
        p.time = nowMillis
        mutableSentPackets += p.snapshot()
    }

    private fun DataPacket.snapshot(): DataPacket = copy().also { it.errorMessage = errorMessage }

    override suspend fun sendTracked(packet: MeshPacket): PacedMessageHandle? = null

    override suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult =
        AwaitedSendResult(AwaitedSendStatus.TRANSPORT_STOPPED)

    override suspend fun sendToRadioAndAwaitResultForConnection(
        packet: MeshPacket,
        expectedConnectionVersion: Long,
    ): AwaitedSendResult = AwaitedSendResult(AwaitedSendStatus.TRANSPORT_STOPPED)

    override fun rearmSendAckTimeouts() = Unit
}
