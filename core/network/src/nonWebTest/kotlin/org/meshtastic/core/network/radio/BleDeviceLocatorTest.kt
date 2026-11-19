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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * On Linux, Kable's identifier for a peripheral is the BlueZ object path, while a saved radio address is its MAC. The
 * locator must treat the two as the same device.
 */
class BleDeviceLocatorTest {

    private val mac = "34:B7:DA:62:18:C5"
    private val objectPath = "/org/bluez/hci0/dev_34_B7_DA_62_18_C5"

    @Test
    fun `a scanned BlueZ object path is found by its saved MAC`() = runTest {
        val scanner = FakeBleScanner()
        val scanned = FakeBleDevice(address = objectPath)
        scanner.emitDevice(scanned)

        val found = BleDeviceLocator(scanner, FakeBluetoothRepository(), mac).findDevice()

        assertSame(scanned, found)
    }

    @Test
    fun `a bonded BlueZ object path is found by its saved MAC`() = runTest {
        val repository = FakeBluetoothRepository()
        val bonded = FakeBleDevice(address = objectPath)
        repository.bond(bonded)

        val found = BleDeviceLocator(FakeBleScanner(), repository, mac).findDevice()

        assertSame(bonded, found)
    }
}
