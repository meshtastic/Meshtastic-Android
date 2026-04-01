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
package org.meshtastic.core.takserver.fountain

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.takserver.CoTMessage
import org.meshtastic.core.takserver.CoTXmlParser
import org.meshtastic.core.takserver.TAKServerManager
import org.meshtastic.core.takserver.toXml
import org.meshtastic.proto.PortNum
import kotlin.time.Clock

class GenericCoTHandler(private val commandSender: CommandSender, private val takServerManager: TAKServerManager) :
    CoTHandler {
    companion object {
        private const val INTER_PACKET_DELAY_MS = 100L
        private const val ACK_RETRANSMIT_DELAY_MS = 50L
        private const val PENDING_TRANSFER_TTL_MS = 60_000L
    }

    private val fountainCodec = FountainCodec()
    private val pendingTransfersMutex = Mutex()
    private val pendingTransfers = mutableMapOf<Int, PendingTransfer>()

    private data class PendingTransfer(
        val transferId: Int,
        val totalBlocks: Int,
        val dataHash: ByteArray,
        val startTime: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as PendingTransfer
            return transferId == other.transferId &&
                totalBlocks == other.totalBlocks &&
                dataHash.contentEquals(other.dataHash) &&
                startTime == other.startTime
        }

        override fun hashCode(): Int {
            var result = transferId
            result = 31 * result + totalBlocks
            result = 31 * result + dataHash.contentHashCode()
            result = 31 * result + startTime.hashCode()
            return result
        }
    }

    override suspend fun sendGenericCoT(cotMessage: CoTMessage) {
        val xml = cotMessage.toXml()
        val xmlBytes = xml.encodeToByteArray()

        val compressed = ZlibCodec.compress(xmlBytes)
        if (compressed == null) {
            Logger.w { "Failed to compress CoT to Zlib" }
            return
        }

        val payload = ByteArray(compressed.size + 1)
        payload[0] = FountainConstants.TRANSFER_TYPE_COT
        compressed.copyInto(payload, 1)

        Logger.d { "Generic CoT: type=${cotMessage.type}, xml=${xmlBytes.size}B, compressed=${payload.size}B" }

        if (payload.size < FountainConstants.FOUNTAIN_THRESHOLD) {
            sendDirect(payload)
        } else {
            sendFountainCoded(payload)
        }
    }

    private fun sendDirect(payload: ByteArray) {
        val dataPacket =
            DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = payload.toByteString(),
                dataType = PortNum.ATAK_FORWARDER.value,
            )
        commandSender.sendData(dataPacket)
        Logger.i { "Sent generic CoT directly: ${payload.size} bytes on port 257" }
    }

    private suspend fun sendFountainCoded(payload: ByteArray) {
        val transferId = fountainCodec.generateTransferId()
        val packets = fountainCodec.encode(payload, transferId)
        val hash = CryptoCodec.sha256Prefix8(payload)

        pendingTransfersMutex.withLock {
            pendingTransfers[transferId] = PendingTransfer(transferId, packets.size, hash)
        }

        Logger.i { "Sending fountain-coded CoT: ${payload.size} bytes -> ${packets.size} blocks, xferId=$transferId" }

        for ((index, packetData) in packets.withIndex()) {
            val dataPacket =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = packetData.toByteString(),
                    dataType = PortNum.ATAK_FORWARDER.value,
                )
            commandSender.sendData(dataPacket)

            if (index < packets.size - 1) {
                delay(INTER_PACKET_DELAY_MS) // Inter-packet delay
            }
        }
    }

    override suspend fun handleIncomingForwarderPacket(payload: ByteArray, senderNodeNum: Int) {
        if (payload.isEmpty()) return

        if (fountainCodec.isFountainPacket(payload)) {
            if (payload.size == FountainConstants.ACK_PACKET_SIZE) {
                handleIncomingAck(payload, senderNodeNum)
            } else {
                handleFountainPacket(payload, senderNodeNum)
            }
        } else {
            handleDirectPacket(payload, senderNodeNum)
        }
    }

    private fun handleDirectPacket(payload: ByteArray, senderNodeNum: Int) {
        if (payload.size <= 1) return
        val transferType = payload[0]
        if (transferType != FountainConstants.TRANSFER_TYPE_COT) return

        val exiData = payload.copyOfRange(1, payload.size)
        processDecompressedCoT(exiData, senderNodeNum)
    }

    private suspend fun handleFountainPacket(payload: ByteArray, senderNodeNum: Int) {
        fountainCodec.handleIncomingPacket(payload)?.let { (decodedData, transferId) ->
            val hash = CryptoCodec.sha256Prefix8(decodedData)
            sendFountainAck(transferId, hash, senderNodeNum)
            delay(ACK_RETRANSMIT_DELAY_MS)
            sendFountainAck(transferId, hash, senderNodeNum)

            if (decodedData.size > 1 && decodedData[0] == FountainConstants.TRANSFER_TYPE_COT) {
                val exiData = decodedData.copyOfRange(1, decodedData.size)
                processDecompressedCoT(exiData, senderNodeNum)
            }
        }
    }

    private fun processDecompressedCoT(exiData: ByteArray, senderNodeNum: Int) {
        val xmlBytes = ZlibCodec.decompress(exiData) ?: return
        val xml = xmlBytes.decodeToString()

        val result = CoTXmlParser(xml).parse()
        val cot = result.getOrNull()

        if (cot != null) {
            takServerManager.broadcast(cot)
            Logger.i { "Received generic CoT from node $senderNodeNum: ${cot.type}" }
        } else {
            Logger.w(result.exceptionOrNull() ?: Exception("Unknown parse error")) { "Failed to parse CoT XML" }
        }
    }

    private fun sendFountainAck(transferId: Int, hash: ByteArray, toNodeNum: Int) {
        val ackPacket =
            fountainCodec.buildAck(
                transferId,
                FountainConstants.ACK_TYPE_COMPLETE,
                received = 0,
                needed = 0,
                dataHash = hash,
            )

        val dataPacket =
            DataPacket(
                to = toNodeNum.toString(),
                bytes = ackPacket.toByteString(),
                dataType = PortNum.ATAK_FORWARDER.value,
            )
        commandSender.sendData(dataPacket)
        Logger.d { "Sent fountain ACK for transfer $transferId" }
    }

    private suspend fun handleIncomingAck(payload: ByteArray, senderNodeNum: Int) {
        val ack = fountainCodec.parseAck(payload) ?: return
        Logger.d { "Received fountain ACK: xferId=${ack.transferId}, type=${ack.type}, from $senderNodeNum" }

        pendingTransfersMutex.withLock {
            cleanupStalePendingTransfersLocked()
            val pending = pendingTransfers[ack.transferId]
            if (pending != null) {
                if (ack.type == FountainConstants.ACK_TYPE_COMPLETE) {
                    if (ack.dataHash.contentEquals(pending.dataHash)) {
                        Logger.i { "Fountain transfer ${ack.transferId} acknowledged by node $senderNodeNum" }
                    } else {
                        Logger.w { "Fountain ACK hash mismatch for transfer ${ack.transferId}" }
                    }
                    pendingTransfers.remove(ack.transferId)
                }
            }
        }
    }

    /** Must be called inside [pendingTransfersMutex]. */
    private fun cleanupStalePendingTransfersLocked() {
        val now = Clock.System.now().toEpochMilliseconds()
        val stale = pendingTransfers.filter { (_, v) -> now - v.startTime > PENDING_TRANSFER_TTL_MS }.keys
        stale.forEach { id ->
            pendingTransfers.remove(id)
            Logger.d { "Evicted stale outbound pending transfer: $id" }
        }
    }
}
