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
package org.meshtastic.app.map.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomTileProviderConfigTest {
    @Test
    fun `Google-compatible validation retains HTTP support`() {
        assertTrue("http://tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(requireHttps = false))
        assertTrue("https://{s}.example.org/{Z}/{X}/{Y}.jpg".isValidTileUrlTemplate(requireHttps = false))
    }

    @Test
    fun `Google-compatible validation rejects unsafe and unresolved templates`() {
        assertFalse("http://token@tiles.example.org/{z}/{x}/{y}.png".isValidTileUrlTemplate(requireHttps = false))
        assertFalse("http:///tiles/{z}/{x}/{y}.png".isValidTileUrlTemplate(requireHttps = false))
        assertFalse("http://tiles.example.org/static#{z}/{x}/{y}".isValidTileUrlTemplate(requireHttps = false))
        assertFalse(
            "http://tiles.example.org/{z}/{x}/{y}.png?token={apiKey}".isValidTileUrlTemplate(requireHttps = false),
        )
    }
}
