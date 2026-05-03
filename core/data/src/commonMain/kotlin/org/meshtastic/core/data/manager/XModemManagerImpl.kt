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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.XModemFile
import org.meshtastic.core.repository.XModemManager
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.XModem
import kotlin.concurrent.Volatile

/**
 * XModem-CRC receiver state machine.
 *
 * Protocol summary (device = sender, Android = receiver):
 * - SOH / STX → data block with seq, CRC-CCITT-16, payload; reply ACK or NAK
 * - EOT → end of transfer; reply ACK, emit assembled file
 * - CAN → sender cancelled; reset state
 *
 * CRC algorithm: CRC-CCITT (poly 0x1021, init 0x0000), same as the Meshtastic firmware.
 */
@Single
class XModemManagerImpl(private val packetHandler: PacketHandler) : XModemManager {

    private val _fileTransferFlow =
        MutableSharedFlow<XModemFile>(
            replay = 0,
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val fileTransferFlow = _fileTransferFlow.asFlow()

    // --- mutable state ---
    // Thread-safety contract: [handleIncomingXModem] is called sequentially from
    // [FromRadioPacketHandlerImpl.handleFromRadio] on a single IO coroutine. The
    // [setTransferName] and [cancel] calls originate from UI/ViewModel coroutines
    // and are guarded by @Volatile for visibility. Concurrent block processing is
    // not possible because the firmware sends one XModem packet at a time and waits
    // for ACK/NAK before sending the next.
    @Volatile private var transferName = ""

    @Volatile private var expectedSeq = INITIAL_SEQ

    @Volatile private var lastActivityMillis = 0L
    private val blocks = mutableListOf<ByteArray>()

    override fun setTransferName(name: String) {
        transferName = name
    }

    override fun handleIncomingXModem(packet: XModem) {
        // If blocks have accumulated but no activity for INACTIVITY_TIMEOUT_MS,
        // the previous transfer is stale (firmware crash, BLE disconnect, etc.).
        if (blocks.isNotEmpty() && lastActivityMillis > 0L) {
            val elapsed = nowMillis - lastActivityMillis
            if (elapsed > INACTIVITY_TIMEOUT_MS) {
                Logger.w { "XModem: inactivity timeout (${elapsed}ms) — resetting stale transfer" }
                reset()
            }
        }
        lastActivityMillis = nowMillis

        when (packet.control) {
            XModem.Control.SOH,
            XModem.Control.STX,
            -> handleDataBlock(packet)

            XModem.Control.EOT -> handleEot()

            XModem.Control.CAN -> {
                Logger.w { "XModem: CAN received — transfer cancelled" }
                reset()
            }

            else -> Logger.w { "XModem: unexpected control byte ${packet.control}, ignoring" }
        }
    }

    private fun handleDataBlock(packet: XModem) {
        val seq = packet.seq and 0xFF
        val data = packet.buffer.toByteArray()

        if (!validateCrc(data, packet.crc16)) {
            Logger.w { "XModem: CRC error on block $seq (expected seq=$expectedSeq) — NAK" }
            sendControl(XModem.Control.NAK)
            return
        }

        when (seq) {
            expectedSeq -> {
                blocks.add(data)
                expectedSeq = (expectedSeq % MAX_SEQ) + 1
                Logger.d { "XModem: block $seq OK, total=${blocks.size} blocks" }
                sendControl(XModem.Control.ACK)
            }

            // Duplicate: sender did not receive our previous ACK; re-ACK without buffering again.
            (expectedSeq - 1 + MAX_SEQ_PLUS_ONE) % MAX_SEQ_PLUS_ONE -> {
                Logger.d { "XModem: duplicate block $seq — re-ACK" }
                sendControl(XModem.Control.ACK)
            }

            else -> {
                Logger.w { "XModem: unexpected seq $seq (expected $expectedSeq) — NAK" }
                sendControl(XModem.Control.NAK)
            }
        }
    }

    private fun handleEot() {
        Logger.i { "XModem: EOT — transfer complete (${blocks.size} blocks, name='$transferName')" }
        sendControl(XModem.Control.ACK)

        val raw = blocks.fold(ByteArray(0)) { acc, block -> acc + block }
        // Strip trailing CTRL-Z padding that XModem senders add to fill the last block.
        var end = raw.size
        while (end > 0 && raw[end - 1] == CTRLZ) end--
        val trimmed = if (end == raw.size) raw else raw.copyOf(end)
        _fileTransferFlow.tryEmit(XModemFile(name = transferName, data = trimmed))
        reset()
    }

    override fun cancel() {
        Logger.i { "XModem: cancelling transfer" }
        sendControl(XModem.Control.CAN)
        reset()
    }

    private fun sendControl(control: XModem.Control) {
        packetHandler.sendToRadio(ToRadio(xmodemPacket = XModem(control = control)))
    }

    private fun reset() {
        expectedSeq = INITIAL_SEQ
        blocks.clear()
        transferName = ""
        lastActivityMillis = 0L
    }

    // CRC-CCITT: polynomial 0x1021, initial value 0x0000 (XModem variant)
    private fun validateCrc(data: ByteArray, expectedCrc: Int): Boolean =
        calculateCrc16(data) == (expectedCrc and 0xFFFF)

    private fun calculateCrc16(data: ByteArray): Int {
        var crc = 0
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(BITS_PER_BYTE) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor CRC_POLY else crc shl 1 }
        }
        return crc and 0xFFFF
    }

    companion object {
        private const val INITIAL_SEQ = 1
        private const val MAX_SEQ = 255
        private const val MAX_SEQ_PLUS_ONE = 256
        private const val CTRLZ = 0x1A.toByte()
        private const val CRC_POLY = 0x1021
        private const val BITS_PER_BYTE = 8
        private const val INACTIVITY_TIMEOUT_MS = 30_000L
    }
}
