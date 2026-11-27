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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A printer that answers each request the way niimprint's `_transceive` expects: the request type plus 1, or plus 16
 * for the density, label type, print status and status data requests.
 */
internal class ScriptedPrinter(private val statusData: ByteArray? = null) : NiimbotLink {
    val sent = mutableListOf<String>()
    override val received = Channel<NiimbotPacket>(Channel.UNLIMITED)

    /** Pages reported by successive print status polls; the last value repeats. */
    var pages = listOf(1)

    /** A fault code the printer raises once it has taken a row packet. */
    var faultAfterRows: Int? = null
    private var polls = 0

    override suspend fun send(packet: NiimbotPacket) {
        sent += packet.toBytes().hex()
        when (packet.type) {
            0x84,
            0x85,
            -> faultAfterRows?.let { received.trySend(NiimbotPacket(0xDB, byteArrayOf(it.toByte()))) }

            0xA5 -> statusData?.let { received.trySend(NiimbotPacket(0xB5, it)) }

            0xA3 -> {
                val page = pages[minOf(polls++, pages.size - 1)]
                received.trySend(NiimbotPacket(0xB3, byteArrayOf(0, page.toByte(), 100, 100)))
            }

            0x21,
            0x23,
            -> received.trySend(NiimbotPacket(packet.type + 16, byteArrayOf(1)))

            else -> received.trySend(NiimbotPacket(packet.type + 1, byteArrayOf(1)))
        }
    }

    companion object {
        /** Status data whose bytes 11 and 12 carry the protocol number niimbluelib reads, here [major].[minor]. */
        fun statusData(major: Int, minor: Int) = ByteArray(13).also {
            it[11] = major.toByte()
            it[12] = minor.toByte()
        }
    }
}

/**
 * Expected sequences are what labeltastic's vendored niimprint (`print_image_new`, and the calls `print_image` opens
 * with) writes for a 96x3 picture with one dot at (0, 1), at meshtastic/labeltastic#38. The heartbeat and status data
 * requests ahead of them are the order labeltastic's `print_label` and Meshtastic-Apple use.
 */
class NiimbotProtocolTest {
    private val picture = LabelBitmap(width = 96, height = 3).also { it.setInk(0, 1) }
    private val probe = listOf("5555dc0101dcaaaa", "5555a50101a5aaaa")
    private val rows =
        listOf(
            "5555840300000186aaaa",
            "5555851200010100000180000000000000000000000016aaaa",
            "5555840300020184aaaa",
        )

    @Test
    fun `a protocol v4 printer gets the nine-byte start and no page start`() = runTest {
        val printer = ScriptedPrinter(ScriptedPrinter.statusData(3, 0))

        NiimbotProtocol(printer).print(picture)

        assertEquals(
            probe +
                listOf(
                    "5555c10101c1aaaa",
                    "555521010323aaaa",
                    "555523010123aaaa",
                    "5555010900010000000000000009aaaa",
                    "5555a30101a3aaaa",
                    "5555130d000300600001000000000000007caaaa",
                ) +
                rows +
                listOf("5555e30101e3aaaa", "5555a30101a3aaaa", "5555f30101f3aaaa", "5555dc0101dcaaaa"),
            printer.sent,
        )
    }

    @Test
    fun `a protocol v3 printer gets the seven-byte start and a page start`() = runTest {
        val printer = ScriptedPrinter(ScriptedPrinter.statusData(2, 4))

        NiimbotProtocol(printer).print(picture)

        assertEquals(
            probe +
                listOf(
                    "5555c10101c1aaaa",
                    "555521010323aaaa",
                    "555523010123aaaa",
                    "555501070001000000000007aaaa",
                    "555503010103aaaa",
                    "5555130600030060000177aaaa",
                ) +
                rows +
                listOf("5555e30101e3aaaa", "5555a30101a3aaaa", "5555f30101f3aaaa"),
            printer.sent,
        )
    }

    @Test
    fun `a printer without status data gets the legacy start and no connect`() = runTest {
        val printer = ScriptedPrinter(statusData = null)

        NiimbotProtocol(printer).print(picture)

        assertEquals(
            probe +
                listOf(
                    "555521010323aaaa",
                    "555523010123aaaa",
                    "555501010101aaaa",
                    "555503010103aaaa",
                    "555513040003006074aaaa",
                ) +
                rows +
                listOf("5555e30101e3aaaa", "5555a30101a3aaaa", "5555f30101f3aaaa"),
            printer.sent,
        )
    }

    @Test
    fun `the status data number picks the protocol version at niimbluelib's boundaries`() = runTest {
        val versions =
            listOf(2 to 3, 2 to 4, 2 to 99, 3 to 0, 3 to 1, 3 to 2).map { (major, minor) ->
                NiimbotProtocol(ScriptedPrinter(ScriptedPrinter.statusData(major, minor))).protocolVersion()
            }

        assertEquals(listOf(0, 3, 3, 4, 4, 5), versions)
    }

    @Test
    fun `the print ends only once the printer reports the page`() = runTest {
        val printer = ScriptedPrinter(ScriptedPrinter.statusData(2, 4)).apply { pages = listOf(0, 0, 1) }

        NiimbotProtocol(printer).print(picture)

        val endPage = printer.sent.indexOf("5555e30101e3aaaa")
        assertEquals(
            listOf("5555a30101a3aaaa", "5555a30101a3aaaa", "5555a30101a3aaaa", "5555f30101f3aaaa"),
            printer.sent.drop(endPage + 1),
        )
    }

    @Test
    fun `a fault raised while rows are sent stops the print with its code`() = runTest {
        val printer = ScriptedPrinter(ScriptedPrinter.statusData(3, 0)).apply { faultAfterRows = 0x08 }

        val fault = assertFailsWith<NiimbotException.PrinterFault> { NiimbotProtocol(printer).print(picture) }

        assertEquals(0x08, fault.code)
        assertEquals(false, "5555f30101f3aaaa" in printer.sent)
    }

    @Test
    fun `a printer that never reports the page fails instead of ending the print`() = runTest {
        val printer = ScriptedPrinter(ScriptedPrinter.statusData(3, 0)).apply { pages = listOf(0) }

        val failure = assertFailsWith<NiimbotException.NoResponse> { NiimbotProtocol(printer).print(picture) }

        assertEquals(NiimbotRequest.GET_PRINT_STATUS, failure.request)
    }

    @Test
    fun `fault codes read as niimprint's error table names them`() {
        val expected =
            mapOf(
                0x01 to NiimbotFault.COVER_OPEN,
                0x02 to NiimbotFault.NO_LABELS,
                0x03 to NiimbotFault.LOW_BATTERY,
                0x05 to NiimbotFault.CANCELED,
                0x07 to NiimbotFault.HEAD_TOO_HOT,
                0x08 to NiimbotFault.LABELS_OUT_OR_LID_UNLATCHED,
                0x09 to NiimbotFault.BUSY,
                0x10 to NiimbotFault.WRONG_ROLL,
                0x06 to NiimbotFault.OTHER,
            )

        assertEquals(expected, expected.keys.associateWith { NiimbotFault.of(it) })
    }
}
