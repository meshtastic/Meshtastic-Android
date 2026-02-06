/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeUtilTest {

    @Test
    fun `extractWifiCredentials should parse valid QR code`() {
        val qrCode = "WIFI:S:MyNetwork;P:MyPassword;;"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertEquals("MyNetwork", ssid)
        assertEquals("MyPassword", password)
    }

    @Test
    fun `extractWifiCredentials should return null for invalid QR code`() {
        val qrCode = "INVALID_QR_CODE"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertNull(ssid)
        assertNull(password)
    }

    @Test
    fun `extractWifiCredentials should handle missing password`() {
        val qrCode = "WIFI:S:MyNetwork;;"
        val (ssid, password) = extractWifiCredentials(qrCode)
        assertNull(ssid)
        assertNull(password)
    }
}
