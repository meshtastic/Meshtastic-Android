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

import org.meshtastic.core.common.log.expectedConditionLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class BluetoothUnsupportedExceptionTest {

    @Test
    fun `Kable no adapter message maps to an expected condition`() {
        val kable = IllegalStateException("Bluetooth not supported")

        val mapped = assertNotNull(kable.asBluetoothUnsupportedExceptionOrNull())

        assertSame(kable, mapped.cause)
        assertEquals("ble-unsupported", mapped.expectedConditionLabel())
    }

    @Test
    fun `Kable missing BluetoothManager message maps to an expected condition`() {
        val kable = IllegalStateException("BluetoothManager is not a supported system service")

        val mapped = assertNotNull(kable.asBluetoothUnsupportedExceptionOrNull())

        assertEquals("ble-unsupported", mapped.expectedConditionLabel())
    }

    @Test
    fun `unrelated failures are left alone`() {
        assertNull(IllegalStateException("Bluetooth is disabled").asBluetoothUnsupportedExceptionOrNull())
        assertNull(IllegalArgumentException("Bluetooth not supported").asBluetoothUnsupportedExceptionOrNull())
        assertNull(IllegalStateException().asBluetoothUnsupportedExceptionOrNull())
    }
}
