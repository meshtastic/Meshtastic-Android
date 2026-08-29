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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.feature.map.tiles.MapTileCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RasterOverlayTogglesTest {

    private val overlay = MapTileCatalogue.overlays.first()

    @Test
    fun `an overlay that is switched off offers no opacity slider`() = runComposeUiTest {
        setContent {
            RasterOverlayToggles(
                available = listOf(overlay),
                enabledIds = emptySet(),
                onToggle = {},
                opacity = emptyMap(),
                onOpacityChange = { _, _ -> },
            )
        }

        onNodeWithTag(layerOpacityTestTag(overlay.id)).assertDoesNotExist()
    }

    @Test
    fun `an overlay that is switched on offers an opacity slider`() = runComposeUiTest {
        setContent {
            RasterOverlayToggles(
                available = listOf(overlay),
                enabledIds = setOf(overlay.id),
                onToggle = {},
                opacity = emptyMap(),
                onOpacityChange = { _, _ -> },
            )
        }

        onNodeWithTag(layerOpacityTestTag(overlay.id)).assertIsDisplayed()
    }

    @Test
    fun `the slider reports once, when the drag ends`() = runComposeUiTest {
        // Every report is a DataStore write; reporting per frame would write dozens of times across one drag.
        val reported = mutableListOf<Pair<String, Float>>()
        setContent {
            RasterOverlayToggles(
                available = listOf(overlay),
                enabledIds = setOf(overlay.id),
                onToggle = {},
                opacity = mapOf(overlay.id to 0.2f),
                onOpacityChange = { id, value -> reported += id to value },
            )
        }

        onNodeWithTag(layerOpacityTestTag(overlay.id)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }

        runOnIdle { assertEquals(listOf(overlay.id to 0.8f), reported) }
    }
}
