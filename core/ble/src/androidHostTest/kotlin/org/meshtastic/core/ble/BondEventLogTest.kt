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
package org.meshtastic.core.ble

import android.bluetooth.BluetoothDevice
import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BondEventLogTest {

    private val mac = "EF:E2:0A:BE:95:6A"

    @Test
    fun `key missing is a warning with the loss reason and only the address tail`() {
        val line = BondEvent.KeyMissing(mac, lossReason = 3).toLogLine("36.1")

        assertEquals(Severity.Warn, line.severity)
        assertEquals("BLE bond event key-missing sdk=36.1 lossReason=3 device=...:6A", line.message)
        assertFalse(mac in line.message)
    }

    @Test
    fun `key missing without a loss reason leaves the field out`() {
        val line = BondEvent.KeyMissing(mac, lossReason = null).toLogLine("36.0")

        assertEquals("BLE bond event key-missing sdk=36.0 device=...:6A", line.message)
    }

    @Test
    fun `losing an established bond is a warning`() {
        val line = bondStateLine(previous = BluetoothDevice.BOND_BONDED, current = BluetoothDevice.BOND_NONE)

        assertEquals(Severity.Warn, line.severity)
        assertEquals("BLE bond event bond-state BONDED->NONE sdk=35 device=...:6A", line.message)
    }

    @Test
    fun `a pairing attempt that ends unbonded is a warning`() {
        val line = bondStateLine(previous = BluetoothDevice.BOND_BONDING, current = BluetoothDevice.BOND_NONE)

        assertEquals(Severity.Warn, line.severity)
    }

    @Test
    fun `starting and completing a bond are info`() {
        val started = bondStateLine(previous = BluetoothDevice.BOND_NONE, current = BluetoothDevice.BOND_BONDING)
        val completed = bondStateLine(previous = BluetoothDevice.BOND_BONDING, current = BluetoothDevice.BOND_BONDED)

        assertEquals(Severity.Info, started.severity)
        assertEquals("BLE bond event bond-state NONE->BONDING sdk=35 device=...:6A", started.message)
        assertEquals(Severity.Info, completed.severity)
    }

    @Test
    fun `a bond state outside the known three is logged as its number`() {
        val line = bondStateLine(previous = BluetoothDevice.ERROR, current = BluetoothDevice.BOND_BONDED)

        assertEquals("BLE bond event bond-state ${BluetoothDevice.ERROR}->BONDED sdk=35 device=...:6A", line.message)
    }

    @Test
    fun `an encrypted link is info and a failed or dropped encryption is a warning`() {
        val encrypted = BondEvent.EncryptionChange(mac, encrypted = true, status = 0).toLogLine("36.0")
        val failed = BondEvent.EncryptionChange(mac, encrypted = false, status = 6).toLogLine("36.0")
        val errorStatus = BondEvent.EncryptionChange(mac, encrypted = true, status = 6).toLogLine("36.0")

        assertEquals(Severity.Info, encrypted.severity)
        assertEquals(Severity.Warn, failed.severity)
        assertEquals(
            "BLE bond event encryption-change sdk=36.0 encrypted=false status=6 device=...:6A",
            failed.message,
        )
        assertEquals(Severity.Warn, errorStatus.severity)
    }

    @Test
    fun `key missing and encryption change are only registered from API 36`() {
        assertEquals(listOf(BluetoothDevice.ACTION_BOND_STATE_CHANGED), bondEventActions(sdkInt = 35))
        assertEquals(
            listOf(
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_KEY_MISSING,
                BluetoothDevice.ACTION_ENCRYPTION_CHANGE,
            ),
            bondEventActions(sdkInt = 36),
        )
    }

    private fun bondStateLine(previous: Int, current: Int) =
        BondEvent.BondStateChanged(mac, previous = previous, current = current).toLogLine("35")
}
