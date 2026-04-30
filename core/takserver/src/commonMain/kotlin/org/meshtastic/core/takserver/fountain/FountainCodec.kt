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
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Clock

internal object FountainConstants {
    val MAGIC = byteArrayOf(0x46, 0x54, 0x4E) // "FTN"
    const val BLOCK_SIZE = 220
    const val DATA_HEADER_SIZE = 11
    const val FOUNTAIN_THRESHOLD = 233
    const val TRANSFER_TYPE_COT: Byte = 0x00
    const val ACK_TYPE_COMPLETE: Byte = 0x02
    const val ACK_PACKET_SIZE = 19
}

internal data class FountainBlock(
    val seed: Int, // UInt16
    var indices: MutableSet<Int>,
    var payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as FountainBlock
        return seed == other.seed && indices == other.indices && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = seed
        result = 31 * result + indices.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

internal class FountainReceiveState(
    val transferId: Int, // UInt24
    val k: Int,
    val totalLength: Int,
) {
    val blocks = mutableListOf<FountainBlock>()
    private val createdAt = Clock.System.now().toEpochMilliseconds()

    fun addBlock(block: FountainBlock) {
        if (blocks.none { it.seed == block.seed }) {
            blocks.add(block)
        }
    }

    val isExpired: Boolean
        get() = (Clock.System.now().toEpochMilliseconds() - createdAt) > 60_000
}

internal data class FountainDataHeader(
    val transferId: Int, // UInt24
    val seed: Int, // UInt16
    val k: Int, // UInt8
    val totalLength: Int, // UInt16
)

internal data class FountainAck(
    val transferId: Int,
    val type: Byte,
    val received: Int,
    val needed: Int,
    val dataHash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as FountainAck
        return transferId == other.transferId &&
            type == other.type &&
            received == other.received &&
            needed == other.needed &&
            dataHash.contentEquals(other.dataHash)
    }

    override fun hashCode(): Int {
        var result = transferId
        result = 31 * result + type.toInt()
        result = 31 * result + received
        result = 31 * result + needed
        result = 31 * result + dataHash.contentHashCode()
        return result
    }
}

@Suppress("MagicNumber")
internal class JavaRandom(seed: Long) {
    private var seed: Long = (seed xor 0x5DEECE66DL) and ((1L shl 48) - 1)

    private fun next(bits: Int): Int {
        seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (seed ushr (48 - bits)).toInt()
    }

    fun nextInt(bound: Int): Int = when {
        bound <= 0 -> 0

        (bound and -bound) == bound -> ((bound.toLong() * next(31).toLong()) shr 31).toInt()

        else -> {
            var bits: Int
            var valResult: Int
            do {
                bits = next(31)
                valResult = bits % bound
            } while (bits - valResult + (bound - 1) < 0)
            valResult
        }
    }

    fun nextDouble(): Double {
        val high = next(26).toLong()
        val low = next(27).toLong()
        return ((high shl 27) + low).toDouble() / (1L shl 53).toDouble()
    }
}

@Suppress("MagicNumber", "TooManyFunctions")
internal class FountainCodec {
    private val receiveStates = mutableMapOf<Int, FountainReceiveState>()

    fun generateTransferId(): Int {
        val random = Random.nextInt(0, 0xFFFFFF + 1)
        val time = (Clock.System.now().toEpochMilliseconds() / 1000).toInt() and 0xFFFF
        return (random xor time) and 0xFFFFFF
    }

    fun encode(data: ByteArray, transferId: Int): List<ByteArray> {
        if (data.isEmpty()) {
            Logger.w { "Fountain encode: empty data" }
            return emptyList()
        }

        val k = maxOf(1, ceil(data.size.toDouble() / FountainConstants.BLOCK_SIZE).toInt())
        val overhead = getAdaptiveOverhead(k)
        val blocksToSend = maxOf(1, ceil(k.toDouble() * (1.0 + overhead)).toInt())

        val sourceBlocks = splitIntoBlocks(data, k)
        val packets = mutableListOf<ByteArray>()

        for (i in 0 until blocksToSend) {
            val seed = generateSeed(transferId, i)
            val indices = generateBlockIndices(seed, k, i)

            var blockPayload = ByteArray(FountainConstants.BLOCK_SIZE) { 0 }
            for (idx in indices) {
                blockPayload = xor(blockPayload, sourceBlocks[idx])
            }

            val packet = buildDataBlock(transferId, seed, k, data.size, blockPayload)
            packets.add(packet)
        }

        Logger.i { "Fountain encode: ${data.size} bytes -> $k source blocks -> $blocksToSend packets" }
        return packets
    }

    private fun splitIntoBlocks(data: ByteArray, k: Int): List<ByteArray> {
        val blocks = mutableListOf<ByteArray>()
        for (i in 0 until k) {
            val start = i * FountainConstants.BLOCK_SIZE
            val end = minOf(start + FountainConstants.BLOCK_SIZE, data.size)

            if (start < data.size) {
                val block = data.copyOfRange(start, end)
                if (block.size < FountainConstants.BLOCK_SIZE) {
                    val padded = ByteArray(FountainConstants.BLOCK_SIZE) { 0 }
                    block.copyInto(padded)
                    blocks.add(padded)
                } else {
                    blocks.add(block)
                }
            } else {
                blocks.add(ByteArray(FountainConstants.BLOCK_SIZE) { 0 })
            }
        }
        return blocks
    }

    private fun buildDataBlock(transferId: Int, seed: Int, k: Int, totalLength: Int, payload: ByteArray): ByteArray {
        val packet = ByteArray(FountainConstants.DATA_HEADER_SIZE + payload.size)

        packet[0] = FountainConstants.MAGIC[0]
        packet[1] = FountainConstants.MAGIC[1]
        packet[2] = FountainConstants.MAGIC[2]

        packet[3] = ((transferId shr 16) and 0xFF).toByte()
        packet[4] = ((transferId shr 8) and 0xFF).toByte()
        packet[5] = (transferId and 0xFF).toByte()

        packet[6] = ((seed shr 8) and 0xFF).toByte()
        packet[7] = (seed and 0xFF).toByte()

        packet[8] = (k and 0xFF).toByte()

        packet[9] = ((totalLength shr 8) and 0xFF).toByte()
        packet[10] = (totalLength and 0xFF).toByte()

        payload.copyInto(packet, FountainConstants.DATA_HEADER_SIZE)
        return packet
    }

    fun isFountainPacket(data: ByteArray): Boolean {
        if (data.size < 3) return false
        return data[0] == FountainConstants.MAGIC[0] &&
            data[1] == FountainConstants.MAGIC[1] &&
            data[2] == FountainConstants.MAGIC[2]
    }

    fun parseDataHeader(data: ByteArray): FountainDataHeader? {
        if (data.size < FountainConstants.DATA_HEADER_SIZE || !isFountainPacket(data)) return null

        val transferId =
            ((data[3].toInt() and 0xFF) shl 16) or ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val seed = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        val k = data[8].toInt() and 0xFF
        val totalLength = ((data[9].toInt() and 0xFF) shl 8) or (data[10].toInt() and 0xFF)

        return FountainDataHeader(transferId, seed, k, totalLength)
    }

    fun handleIncomingPacket(data: ByteArray): Pair<ByteArray, Int>? {
        cleanupExpiredStates()

        val header = parseDataHeader(data)
        if (header != null) {
            val payload = data.copyOfRange(FountainConstants.DATA_HEADER_SIZE, data.size)
            if (payload.size == FountainConstants.BLOCK_SIZE) {
                return processValidIncomingPacket(header, payload)
            } else {
                Logger.w { "Invalid fountain payload size: ${payload.size}" }
            }
        }
        return null
    }

    private fun processValidIncomingPacket(header: FountainDataHeader, payload: ByteArray): Pair<ByteArray, Int>? {
        val state =
            receiveStates.getOrPut(header.transferId) {
                FountainReceiveState(header.transferId, header.k, header.totalLength)
            }

        val indices = regenerateIndices(header.seed, state.k, header.transferId)
        val block = FountainBlock(header.seed, indices.toMutableSet(), payload)
        state.addBlock(block)

        if (state.blocks.size >= state.k) {
            val decoded = peelingDecode(state)
            if (decoded != null) {
                receiveStates.remove(header.transferId)
                Logger.i { "Fountain decode complete: ${decoded.size} bytes from ${state.blocks.size} blocks" }
                return Pair(decoded, header.transferId)
            }
        }
        return null
    }

    fun buildAck(transferId: Int, type: Byte, received: Int, needed: Int, dataHash: ByteArray): ByteArray {
        val packet = ByteArray(FountainConstants.ACK_PACKET_SIZE)

        packet[0] = FountainConstants.MAGIC[0]
        packet[1] = FountainConstants.MAGIC[1]
        packet[2] = FountainConstants.MAGIC[2]

        packet[3] = ((transferId shr 16) and 0xFF).toByte()
        packet[4] = ((transferId shr 8) and 0xFF).toByte()
        packet[5] = (transferId and 0xFF).toByte()

        packet[6] = type

        packet[7] = ((received shr 8) and 0xFF).toByte()
        packet[8] = (received and 0xFF).toByte()

        packet[9] = ((needed shr 8) and 0xFF).toByte()
        packet[10] = (needed and 0xFF).toByte()

        val hashLen = minOf(8, dataHash.size)
        dataHash.copyInto(packet, 11, 0, hashLen)

        return packet
    }

    fun parseAck(data: ByteArray): FountainAck? {
        if (data.size < FountainConstants.ACK_PACKET_SIZE || !isFountainPacket(data)) return null

        val transferId =
            ((data[3].toInt() and 0xFF) shl 16) or ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val type = data[6]
        val received = ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)
        val needed = ((data[9].toInt() and 0xFF) shl 8) or (data[10].toInt() and 0xFF)
        val dataHash = data.copyOfRange(11, 19)

        return FountainAck(transferId, type, received, needed, dataHash)
    }

    private fun peelingDecode(state: FountainReceiveState): ByteArray? {
        val decoded = mutableMapOf<Int, ByteArray>()
        val workingBlocks =
            state.blocks.map { FountainBlock(it.seed, it.indices.toMutableSet(), it.payload.copyOf()) }.toMutableList()

        var progress = true
        while (progress && decoded.size < state.k) {
            progress = processWorkingBlocks(workingBlocks, decoded)
        }

        if (decoded.size < state.k) {
            Logger.d { "Peeling decode incomplete: ${decoded.size}/${state.k} blocks decoded" }
            return null
        }
        return assembleDecodedData(state, decoded)
    }

    private fun processWorkingBlocks(workingBlocks: List<FountainBlock>, decoded: MutableMap<Int, ByteArray>): Boolean {
        var progress = false
        for (i in workingBlocks.indices) {
            val block = workingBlocks[i]
            val toRemove = mutableListOf<Int>()
            for (idx in block.indices) {
                val decodedBlock = decoded[idx]
                if (decodedBlock != null) {
                    block.payload = xor(block.payload, decodedBlock)
                    toRemove.add(idx)
                }
            }
            block.indices.removeAll(toRemove)

            if (block.indices.size == 1) {
                val idx = block.indices.first()
                if (!decoded.containsKey(idx)) {
                    decoded[idx] = block.payload
                    progress = true
                }
            }
        }
        return progress
    }

    private fun assembleDecodedData(state: FountainReceiveState, decoded: Map<Int, ByteArray>): ByteArray? {
        val result = ByteArray(state.k * FountainConstants.BLOCK_SIZE)
        for (i in 0 until state.k) {
            val block = decoded[i] ?: return null
            block.copyInto(result, i * FountainConstants.BLOCK_SIZE)
        }
        return result.copyOfRange(0, state.totalLength)
    }

    private fun cleanupExpiredStates() {
        val expiredIds = receiveStates.filter { it.value.isExpired }.map { it.key }
        for (id in expiredIds) {
            receiveStates.remove(id)
            Logger.d { "Cleaned up expired fountain state: $id" }
        }
    }

    private fun getAdaptiveOverhead(k: Int): Double = when {
        k <= 10 -> 0.50
        k <= 50 -> 0.25
        else -> 0.15
    }

    private fun generateSeed(transferId: Int, blockIndex: Int): Int {
        val combined = transferId * 31337 + blockIndex * 7919
        return combined and 0xFFFF
    }

    private fun generateBlockIndices(seed: Int, k: Int, blockIndex: Int): Set<Int> {
        val rng = JavaRandom(seed.toLong())
        val sampledDegree = sampleRobustSolitonDegree(rng, k)
        val degree = if (blockIndex == 0) 1 else sampledDegree
        return selectIndices(rng, k, degree)
    }

    private fun regenerateIndices(seed: Int, k: Int, transferId: Int): Set<Int> {
        val rng = JavaRandom(seed.toLong())
        val sampledDegree = sampleRobustSolitonDegree(rng, k)
        val expectedSeed0 = generateSeed(transferId, 0)
        val degree = if (seed == expectedSeed0) 1 else sampledDegree
        return selectIndices(rng, k, degree)
    }

    private fun selectIndices(rng: JavaRandom, k: Int, degree: Int): Set<Int> {
        val indices = mutableSetOf<Int>()
        while (indices.size < degree && indices.size < k) {
            val idx = rng.nextInt(k)
            indices.add(idx)
        }
        return indices
    }

    private fun sampleRobustSolitonDegree(rng: JavaRandom, k: Int): Int {
        val cdf = buildRobustSolitonCDF(k)
        val u = rng.nextDouble()
        for (d in 1..k) {
            if (u <= cdf[d]) return d
        }
        return k
    }

    private fun buildRobustSolitonCDF(k: Int, c: Double = 0.1, delta: Double = 0.5): DoubleArray {
        if (k <= 0) return doubleArrayOf(1.0)

        val rho = DoubleArray(k + 1)
        rho[1] = 1.0 / k.toDouble()
        for (d in 2..k) {
            rho[d] = 1.0 / (d.toDouble() * (d - 1).toDouble())
        }

        val rVal = c * ln(k.toDouble() / delta) * sqrt(k.toDouble())
        val tau = DoubleArray(k + 1)
        val threshold = (k.toDouble() / rVal).toInt()

        for (d in 1..k) {
            if (d < threshold) {
                tau[d] = rVal / (d.toDouble() * k.toDouble())
            } else if (d == threshold) {
                tau[d] = rVal * ln(rVal / delta) / k.toDouble()
            }
        }

        val mu = DoubleArray(k + 1)
        var sum = 0.0
        for (d in 1..k) {
            mu[d] = rho[d] + tau[d]
            sum += mu[d]
        }

        val cdf = DoubleArray(k + 1)
        var cumulative = 0.0
        for (d in 1..k) {
            cumulative += mu[d] / sum
            cdf[d] = cumulative
        }
        return cdf
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(maxOf(a.size, b.size))
        for (i in result.indices) {
            val byteA = if (i < a.size) a[i] else 0
            val byteB = if (i < b.size) b[i] else 0
            result[i] = (byteA.toInt() xor byteB.toInt()).toByte()
        }
        return result
    }
}
