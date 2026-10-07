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

import org.maplibre.compose.offline.OfflineManagerState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineDownloadGateTest {

    private val style = "https://tiles.openfreemap.org/styles/liberty"

    @Test
    fun `a ready manager with a vector style and tiles to fetch can download`() {
        assertTrue(canDownloadOfflinePack(OfflineManagerState.Ready(emptySet()), style, estimate = 12L))
    }

    @Test
    fun `a manager still loading its database cannot download`() {
        assertFalse(canDownloadOfflinePack(OfflineManagerState.Loading, style, estimate = 12L))
    }

    @Test
    fun `a manager whose database failed to open cannot download`() {
        val failed = OfflineManagerState.Failed(IllegalStateException("database is locked"))
        assertFalse(canDownloadOfflinePack(failed, style, estimate = 12L))
    }

    @Test
    fun `a raster basemap has no style to pack`() {
        assertFalse(canDownloadOfflinePack(OfflineManagerState.Ready(emptySet()), styleUrl = null, estimate = 12L))
    }

    @Test
    fun `an empty region has nothing to download`() {
        assertFalse(canDownloadOfflinePack(OfflineManagerState.Ready(emptySet()), style, estimate = 0L))
    }
}
