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
package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SecureDfuTransportTest {

    private val address = "00:11:22:33:44:55"
    private val dfuAddress = "00:11:22:33:44:56"

    @Test
    fun `triggerButtonlessDfu writes reboot opcode through BleService`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.triggerButtonlessDfu()

        assertTrue(result.isSuccess)
        val write = connection.service.writes.single()
        assertEquals(SecureDfuUuids.BUTTONLESS_NO_BONDS, write.characteristic.uuid)
        assertContentEquals(byteArrayOf(0x01), write.data)
        assertEquals(BleWriteType.WITH_RESPONSE, write.writeType)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `connectToDfuMode succeeds using shared BleService observation`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `abort writes ABORT opcode through BleService`() = runTest {
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = FakeBleScanner(),
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        transport.abort()

        val write = connection.service.writes.single()
        assertEquals(SecureDfuUuids.CONTROL_POINT, write.characteristic.uuid)
        assertContentEquals(byteArrayOf(DfuOpcode.ABORT), write.data)
        assertEquals(BleWriteType.WITH_RESPONSE, write.writeType)
    }
}
