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

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.XModem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XModemManagerImplTest {
    private lateinit var packetHandler: PacketHandler
    private lateinit var xmodemManager: XModemManagerImpl

    @BeforeTest
    fun setup() {
        packetHandler = mock<PacketHandler> { every { sendToRadio(any<ToRadio>()) } returns Unit }
        xmodemManager = XModemManagerImpl(packetHandler)
    }

    private fun calculateExpectedCrc(data: ByteArray): Int {
        var crc = 0
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
        }
        return crc and 0xFFFF
    }

    @Test
    fun `successful transfer emits file and ACKs blocks`() = runTest {
        val payload1 = "Hello, ".encodeToByteArray()
        val payload2 = "Meshtastic!".encodeToByteArray()

        xmodemManager.setTransferName("test.txt")

        xmodemManager.fileTransferFlow.test {
            // Send Block 1
            xmodemManager.handleIncomingXModem(
                XModem(
                    control = XModem.Control.SOH,
                    seq = 1,
                    crc16 = calculateExpectedCrc(payload1),
                    buffer = payload1.toByteString(),
                ),
            )

            // Send Block 2
            xmodemManager.handleIncomingXModem(
                XModem(
                    control = XModem.Control.SOH,
                    seq = 2,
                    crc16 = calculateExpectedCrc(payload2),
                    buffer = payload2.toByteString(),
                ),
            )

            // EOT
            xmodemManager.handleIncomingXModem(XModem(control = XModem.Control.EOT))

            val file = awaitItem()
            assertEquals("test.txt", file.name)
            assertEquals("Hello, Meshtastic!", file.data.decodeToString())

            verify(exactly(3)) { packetHandler.sendToRadio(any<ToRadio>()) }
        }
    }

    @Test
    fun `ignores bad CRC and replies NAK`() = runTest {
        val payload1 = "Bad CRC payload".encodeToByteArray()

        xmodemManager.handleIncomingXModem(
            XModem(
                control = XModem.Control.SOH,
                seq = 1,
                crc16 = 0xBAD, // intentionally bad
                buffer = payload1.toByteString(),
            ),
        )

        verify(exactly(1)) { packetHandler.sendToRadio(any<ToRadio>()) }
    }

    @Test
    fun `handles CAN and resets state`() = runTest {
        xmodemManager.setTransferName("bad.txt")

        xmodemManager.handleIncomingXModem(XModem(control = XModem.Control.CAN))

        // No control sent back for CAN by the device, just resets.
        // If we cancel locally, we send CAN. Wait, the test is for receiving CAN.
        // So nothing should be sent, but state should reset.
        // Let's verify no ACK/NAK sent when receiving CAN.
        verify(exactly(0)) { packetHandler.sendToRadio(any<ToRadio>()) }
    }

    @Test
    fun `removes CTRLZ padding from end of file`() = runTest {
        val payload = byteArrayOf(0x48, 0x69, 0x1A, 0x1A) // "Hi" + CTRL-Z padding
        xmodemManager.setTransferName("padded.txt")

        xmodemManager.fileTransferFlow.test {
            xmodemManager.handleIncomingXModem(
                XModem(
                    control = XModem.Control.SOH,
                    seq = 1,
                    crc16 = calculateExpectedCrc(payload),
                    buffer = payload.toByteString(),
                ),
            )
            xmodemManager.handleIncomingXModem(XModem(control = XModem.Control.EOT))

            val file = awaitItem()
            val expected = byteArrayOf(0x48, 0x69) // "Hi"
            assertTrue(expected.contentEquals(file.data))
        }
    }
}
