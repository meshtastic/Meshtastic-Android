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

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleCharacteristicInfo
import org.meshtastic.core.ble.BleServiceInfo
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NiimbotPrinterTest {
    private val battery = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")
    private val vendor = Uuid.parse("e7810a71-73ae-499d-8c15-faa9aef0c3f2")
    private val print = Uuid.parse("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")
    private val other = Uuid.parse("00002a19-0000-1000-8000-00805f9b34fb")

    private fun characteristic(uuid: Uuid, notifies: Boolean, writes: Boolean) =
        BleCharacteristicInfo(uuid, notifies = notifies, writesWithoutResponse = writes)

    private val services =
        listOf(
            BleServiceInfo(battery, listOf(characteristic(other, notifies = true, writes = true))),
            BleServiceInfo(
                vendor,
                listOf(
                    characteristic(other, notifies = true, writes = false),
                    characteristic(print, notifies = true, writes = true),
                ),
            ),
        )

    // The rule is niimbluelib's, as Meshtastic-Apple's NiimbotPrinter applies it on a D110.
    @Test
    fun `the print channel is the vendor characteristic that notifies and writes without response`() {
        assertEquals(NiimbotChannel(vendor, print), niimbotChannel(services))
        assertEquals(null, niimbotChannel(services.take(1)))
    }

    @Test
    fun `printing runs on an auxiliary connection in writes the link can carry`() = runTest {
        val connection = FakeBleConnection().apply { discoveredServices = services }
        val factory = FakeBleConnectionFactory(connection)
        val parser = NiimbotPacketParser()
        val scripted = ScriptedPrinter(ScriptedPrinter.statusData(3, 0))
        // Answers each whole packet the app writes the way ScriptedPrinter does, as notifications.
        connection.service.beforeWrite = { _, bytes ->
            parser.append(bytes).forEach { scripted.send(it) }
            var reply = scripted.received.tryReceive().getOrNull()
            while (reply != null) {
                connection.service.emitNotification(print, reply.toBytes())
                reply = scripted.received.tryReceive().getOrNull()
            }
        }
        val printer = NiimbotPrinter(FakeBleScanner(), factory, backgroundScope)

        printer.connect(FakeBleDevice("AA:BB:CC:DD:EE:FF", name = "D110_M-H123"))
        printer.print(LabelBitmap(width = 96, height = 3).also { it.setInk(0, 1) })

        assertEquals(listOf("NiimbotPrinter"), factory.auxiliaryTags)
        val writes = connection.service.writes
        assertTrue(writes.all { it.characteristic.uuid == print && it.writeType == BleWriteType.WITHOUT_RESPONSE })
        // The inked row's packet is 25 bytes and an unnegotiated link carries 20.
        assertEquals(20, writes.maxOf { it.data.size })
        assertTrue("5555851200010100000180000000000000000000000016aaaa" in scripted.sent)
        assertEquals("5555c10101c1aaaa", scripted.sent[2])
        assertEquals("5555dc0101dcaaaa", scripted.sent.last())
    }

    @Test
    fun `a device with no print channel is reported as unsupported`() = runTest {
        val connection = FakeBleConnection().apply { discoveredServices = services.take(1) }
        val printer = NiimbotPrinter(FakeBleScanner(), FakeBleConnectionFactory(connection), backgroundScope)

        assertFailsWith<NiimbotException.Unsupported> { printer.connect(FakeBleDevice("AA:BB:CC:DD:EE:FF")) }
    }

    // A printer left connected does not advertise, so a failed connect has to let go of it.
    @Test
    fun `a connect that fails after the link is up disconnects`() = runTest {
        val connection = FakeBleConnection().apply { discoveredServices = services.take(1) }
        val printer = NiimbotPrinter(FakeBleScanner(), FakeBleConnectionFactory(connection), backgroundScope)

        assertFailsWith<NiimbotException.Unsupported> { printer.connect(FakeBleDevice("AA:BB:CC:DD:EE:FF")) }

        assertEquals(1, connection.disconnectCalls)
    }
}
