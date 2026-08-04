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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The persisted map selection is resolved in two places — the renderer and the start-up restore. They disagreed: the
 * restore path only compared [CustomTileProviderConfig.urlTemplate], which is empty for an imported MBTiles file, so a
 * local provider could never be matched back and its layer was dropped on every restart.
 */
class CustomTileProviderConfigTest {

    private val offlineMap =
        CustomTileProviderConfig(
            name = "SCAN 25",
            urlTemplate = "",
            localUri = "file:///data/user/0/app/files/map_layers/mbtiles_scan25.mbtiles",
        )
    private val networkMap =
        CustomTileProviderConfig(name = "OpenTopoMap", urlTemplate = "https://tile.opentopomap.org/{z}/{x}/{y}.png")

    @Test
    fun `a local provider is identified by its file uri`() {
        assertEquals(offlineMap.localUri, offlineMap.selectionKey)
        assertTrue(offlineMap.isLocal)
    }

    @Test
    fun `a network provider is identified by its url template`() {
        assertEquals(networkMap.urlTemplate, networkMap.selectionKey)
        assertFalse(networkMap.isLocal)
    }

    @Test
    fun `a provider matches the selection key it produced`() {
        assertTrue(offlineMap.matchesSelection(offlineMap.selectionKey))
        assertTrue(networkMap.matchesSelection(networkMap.selectionKey))
    }

    @Test
    fun `a local provider does not match another provider's selection`() {
        assertFalse(offlineMap.matchesSelection(networkMap.selectionKey))
        assertFalse(networkMap.matchesSelection(offlineMap.selectionKey))
    }
}
