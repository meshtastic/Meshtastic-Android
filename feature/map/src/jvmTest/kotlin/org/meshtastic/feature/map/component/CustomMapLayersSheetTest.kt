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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.feature.map.layers.LayerType
import org.meshtastic.feature.map.layers.MapLayerItem
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CustomMapLayersSheetTest {

    private fun layer(isVisible: Boolean = true) = MapLayerItem(
        id = "regenerated-on-every-load",
        name = "Trail",
        uri = "file:///layers/trail.kml",
        isVisible = isVisible,
        layerType = LayerType.KML,
    )

    @Composable
    private fun sheet(layer: MapLayerItem) {
        CustomMapLayersSheet(
            mapLayers = listOf(layer),
            onToggleVisibility = {},
            onRemoveLayer = {},
            onAddLayerClicked = {},
            onRefreshLayer = {},
            onAddNetworkLayer = { _, _ -> },
            opacity = emptyMap(),
            onOpacityChange = { _, _ -> },
        )
    }

    @Test
    fun `an imported layer's opacity is keyed by its uri rather than its id`() = runComposeUiTest {
        // A file-backed layer is handed a fresh random id on every load, so an id key would silently lose the
        // user's setting at the next start. hiddenLayerUrls keys visibility by URI for the same reason.
        val layer = layer()

        setContent { sheet(layer) }

        onNodeWithTag(layerOpacityTestTag("file:///layers/trail.kml")).assertIsDisplayed()
        onNodeWithTag(layerOpacityTestTag("regenerated-on-every-load")).assertDoesNotExist()
    }

    @Test
    fun `a hidden layer offers no opacity slider`() = runComposeUiTest {
        setContent { sheet(layer(isVisible = false)) }

        onNodeWithTag(layerOpacityTestTag("file:///layers/trail.kml")).assertDoesNotExist()
    }
}
