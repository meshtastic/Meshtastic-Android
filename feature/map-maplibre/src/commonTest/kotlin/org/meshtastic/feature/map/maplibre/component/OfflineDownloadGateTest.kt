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
package org.meshtastic.feature.map.maplibre.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineDownloadGateTest {

    private val style = "https://tiles.openfreemap.org/styles/liberty"

    @Test
    fun `ready storage with a vector style and tiles to fetch can download`() {
        assertTrue(canDownloadOfflinePack(isReady = true, style, estimate = 12L))
    }

    @Test
    fun `storage that is not ready cannot download`() {
        assertFalse(canDownloadOfflinePack(isReady = false, style, estimate = 12L))
    }

    @Test
    fun `a raster basemap has no style to pack`() {
        assertFalse(canDownloadOfflinePack(isReady = true, styleUrl = null, estimate = 12L))
    }

    @Test
    fun `an empty region has nothing to download`() {
        assertFalse(canDownloadOfflinePack(isReady = true, style, estimate = 0L))
    }
}
