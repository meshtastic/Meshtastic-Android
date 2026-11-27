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
package org.meshtastic.core.ble.niimbot

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val BYTE_SHIFT = 8
private const val BYTE_MASK = 0xFF

private fun bigEndian(value: Int): ByteArray = byteArrayOf((value shr BYTE_SHIFT).toByte(), value.toByte())

private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and BYTE_MASK

private const val TYPE_FAULT = 0xDB
private const val TYPE_UNSUPPORTED = 0x00

/** Why the printer did not print. */
sealed class NiimbotException(message: String) : Exception(message) {
    /** The printer reported a fault (packet type 0xDB) with this code. */
    class PrinterFault(val code: Int) : NiimbotException("Printer fault $code")

    /** The printer answered that it does not know [request]. */
    class Unsupported(val request: Int) : NiimbotException("Printer does not support request $request")

    /** The printer did not answer [request] in time. */
    class NoResponse(val request: Int) : NiimbotException("No reply to request $request")

    /** The link to the printer is gone: a write failed or its notifications ended. Nothing more can be sent. */
    class LinkLost(val request: Int) : NiimbotException("Link lost at request $request")
}

/** [packet] unless it is the printer reporting a fault or saying it does not know [request]. */
private fun checked(packet: NiimbotPacket, request: Int): NiimbotPacket = when (packet.type) {
    TYPE_FAULT -> throw NiimbotException.PrinterFault(packet.data.firstOrNull()?.toInt()?.and(BYTE_MASK) ?: 0)
    TYPE_UNSUPPORTED -> throw NiimbotException.Unsupported(request)
    else -> packet
}

/** What a printer fault code means, per MultiMote/niimbluelib as labeltastic's vendored niimprint lists them. */
enum class NiimbotFault {
    COVER_OPEN,
    NO_LABELS,
    LOW_BATTERY,
    CANCELED,
    HEAD_TOO_HOT,
    LABELS_OUT_OR_LID_UNLATCHED,
    BUSY,
    WRONG_ROLL,
    OTHER,
    ;

    companion object {
        private const val CODE_COVER_OPEN = 0x01
        private const val CODE_NO_LABELS = 0x02
        private const val CODE_LOW_BATTERY = 0x03
        private const val CODE_CANCELED = 0x05
        private const val CODE_HEAD_TOO_HOT = 0x07
        private const val CODE_LABELS_OUT = 0x08
        private const val CODE_BUSY = 0x09
        private const val CODE_WRONG_ROLL = 0x10

        fun of(code: Int): NiimbotFault = when (code) {
            CODE_COVER_OPEN -> COVER_OPEN
            CODE_NO_LABELS -> NO_LABELS
            CODE_LOW_BATTERY -> LOW_BATTERY
            CODE_CANCELED -> CANCELED
            CODE_HEAD_TOO_HOT -> HEAD_TOO_HOT
            CODE_LABELS_OUT -> LABELS_OUT_OR_LID_UNLATCHED
            CODE_BUSY -> BUSY
            CODE_WRONG_ROLL -> WRONG_ROLL
            else -> OTHER
        }
    }
}

/** The two-way packet link to a printer: BLE in the app, a script in tests. */
interface NiimbotLink {
    suspend fun send(packet: NiimbotPacket)

    /** Packets the printer sent, in arrival order. Closed when the link is gone. */
    val received: ReceiveChannel<NiimbotPacket>
}

/**
 * The print sequence, as labeltastic's vendored niimprint and Meshtastic-Apple run it. Printers on protocol v3 and
 * later print blank on the older sequence, so the version the printer reports picks the sequence.
 */
class NiimbotProtocol(private val link: NiimbotLink) {

    /** Prints one copy of [bitmap] on die-cut labels. [density] is 1 to 3 on the D110. */
    suspend fun print(bitmap: LabelBitmap, density: Int = DEFAULT_DENSITY) {
        attempt { transceive(NiimbotRequest.HEARTBEAT, ONE) }
        val version = protocolVersion()
        if (version >= VERSION_3) attempt { transceive(NiimbotRequest.CONNECT, ONE) }
        transceive(NiimbotRequest.SET_LABEL_DENSITY, byteArrayOf(density.toByte()), STATUS_REPLY_OFFSET)
        transceive(NiimbotRequest.SET_LABEL_TYPE, byteArrayOf(LABEL_TYPE_GAP), STATUS_REPLY_OFFSET)
        startPage(version, bitmap)
        for (packet in NiimbotImageEncoder.packets(bitmap)) {
            if (!sendBounded(packet)) throw NiimbotException.NoResponse(packet.type)
            delay(ROW_PACING)
        }
        transceive(NiimbotRequest.END_PAGE_PRINT, ONE)
        awaitPage()
        afterPage { transceive(NiimbotRequest.END_PRINT, ONE) }
        // Some v4 models drop the packet after a print; this one is the spare.
        if (version >= VERSION_4) afterPage { sendBounded(NiimbotPacket(NiimbotRequest.HEARTBEAT, ONE)) }
    }

    /** 0 for older printers; 3, 4 or 5 for newer firmware (niimbluelib's decoding of the status data). */
    internal suspend fun protocolVersion(): Int {
        var number = 0
        attempt {
            val data = transceive(NiimbotRequest.PRINTER_STATUS_DATA, ONE, STATUS_REPLY_OFFSET).data
            if (data.size > VERSION_MINOR_INDEX) {
                number = data.unsigned(VERSION_MAJOR_INDEX) * VERSION_MAJOR_SCALE + data.unsigned(VERSION_MINOR_INDEX)
            }
        }
        return when {
            number >= VERSION_5_FROM -> VERSION_5
            number >= VERSION_4_FROM -> VERSION_4
            number >= VERSION_3_FROM -> VERSION_3
            else -> 0
        }
    }

    private suspend fun startPage(version: Int, bitmap: LabelBitmap) {
        val size = bigEndian(bitmap.height) + bigEndian(bitmap.width)
        val quantity = bigEndian(QUANTITY)
        when {
            version >= VERSION_4 -> {
                transceive(NiimbotRequest.START_PRINT, quantity + ByteArray(V4_PADDING))
                // niimbluelib sends this status query and does not wait for it; its late reply is ignored.
                sendBounded(NiimbotPacket(NiimbotRequest.GET_PRINT_STATUS, ONE))
                transceive(NiimbotRequest.SET_DIMENSION, size + quantity + ByteArray(V4_PADDING))
            }

            version == VERSION_3 -> {
                transceive(NiimbotRequest.START_PRINT, quantity + ByteArray(V3_PADDING))
                transceive(NiimbotRequest.START_PAGE_PRINT, ONE)
                transceive(NiimbotRequest.SET_DIMENSION, size + quantity)
            }

            else -> {
                transceive(NiimbotRequest.START_PRINT, ONE)
                transceive(NiimbotRequest.START_PAGE_PRINT, ONE)
                transceive(NiimbotRequest.SET_DIMENSION, size)
            }
        }
    }

    /** Polls until the printer reports the page, for at most [PAGE_TIMEOUT] of wall time, writes included. */
    private suspend fun awaitPage() {
        withTimeoutOrNull(PAGE_TIMEOUT) {
            do {
                delay(STATUS_POLL_INTERVAL)
            } while (pagesPrinted() < QUANTITY)
        } ?: throw NiimbotException.NoResponse(NiimbotRequest.GET_PRINT_STATUS)
    }

    /**
     * A fault the printer reports is the answer and a lost link ends the print; a poll a busy printer skips counts as
     * no page yet.
     */
    private suspend fun pagesPrinted(): Int = try {
        val data = transceive(NiimbotRequest.GET_PRINT_STATUS, ONE, STATUS_REPLY_OFFSET).data
        if (data.size >= 2) data.unsigned(0) shl BYTE_SHIFT or data.unsigned(1) else 0
    } catch (fault: NiimbotException.PrinterFault) {
        throw fault
    } catch (lost: NiimbotException.LinkLost) {
        throw lost
    } catch (_: NiimbotException) {
        0
    }

    /** Sends [request] and waits for its reply; the write and the wait together get [REPLY_TIMEOUT]. */
    private suspend fun transceive(request: Int, data: ByteArray, replyOffset: Int = 1): NiimbotPacket {
        val replyType = request + replyOffset and BYTE_MASK
        return try {
            withTimeoutOrNull(REPLY_TIMEOUT) {
                discardStale(request)
                link.send(NiimbotPacket(request, data))
                awaitReply(request, replyType)
            }
        } catch (_: ClosedReceiveChannelException) {
            throw NiimbotException.LinkLost(request)
        } ?: throw NiimbotException.NoResponse(request)
    }

    private suspend fun awaitReply(request: Int, replyType: Int): NiimbotPacket {
        var packet = checked(link.received.receive(), request)
        while (packet.type != replyType) packet = checked(link.received.receive(), request)
        return packet
    }

    /** Drops replies nothing waits for any more. A fault that arrived meanwhile still fails this request. */
    private fun discardStale(request: Int) {
        var stale = link.received.tryReceive().getOrNull()
        while (stale != null) {
            checked(stale, request)
            stale = link.received.tryReceive().getOrNull()
        }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (lost: NiimbotException.LinkLost) {
            throw lost
        } catch (_: NiimbotException) {
            // Optional step: older firmware skips it and the print goes on, unless the link itself is gone.
        }
    }

    /** Writes [packet] without waiting for a reply. False when the write did not return within [WRITE_TIMEOUT]. */
    private suspend fun sendBounded(packet: NiimbotPacket): Boolean =
        withTimeoutOrNull(WRITE_TIMEOUT) { link.send(packet) } != null

    private suspend fun afterPage(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: NiimbotException) {
            // The page is out by now, so nothing here fails the print, a lost link included.
        }
    }

    private companion object {
        val ONE = byteArrayOf(1)
        val REPLY_TIMEOUT = 2.seconds
        val ROW_PACING = 10.milliseconds
        val STATUS_POLL_INTERVAL = 300.milliseconds
        val PAGE_TIMEOUT = 60.seconds

        /**
         * Twice the 5 s link supervision timeout Android asks for, so a link that really died surfaces as lost first
         * and only a write stuck on a live link runs into this.
         */
        val WRITE_TIMEOUT = 10.seconds
        const val DEFAULT_DENSITY = 3
        const val QUANTITY = 1

        /** Die-cut labels with gaps. */
        const val LABEL_TYPE_GAP: Byte = 1
        const val STATUS_REPLY_OFFSET = 16
        const val V4_PADDING = 7
        const val V3_PADDING = 5
        const val VERSION_3 = 3
        const val VERSION_4 = 4
        const val VERSION_5 = 5
        const val VERSION_3_FROM = 204
        const val VERSION_4_FROM = 300
        const val VERSION_5_FROM = 302
        const val VERSION_MAJOR_INDEX = 11
        const val VERSION_MINOR_INDEX = 12
        const val VERSION_MAJOR_SCALE = 100
    }
}
