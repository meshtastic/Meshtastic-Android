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
package org.meshtastic.feature.map.maplibre

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.map_engine_unavailable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The crash in #7001 is a missing native library, and a missing library is an `UnsatisfiedLinkError` — an `Error`, not
 * an `Exception`. The probe has to survive exactly that, and the map screen has to stop before reaching MapLibre when
 * it says no.
 *
 * The guard is the first statement of [MapLibreMapViewProvider.MapView], and this test leans on that: nothing else in
 * this module's Koin graph is available here, so anything composed past the guard would fail with a Koin error rather
 * than the notice. A guard that drifts later in the function fails this test rather than passing it quietly.
 */
@OptIn(ExperimentalTestApi::class)
class MapLibreRuntimeTest {

    @Test
    fun `a missing library is reported as unavailable, not thrown`() {
        assertFalse(probeNativeRuntime { throw UnsatisfiedLinkError("dlopen failed: library not found") })
    }

    @Test
    fun `a load that returns is available`() {
        assertTrue(probeNativeRuntime {})
    }

    @Test
    fun `the map screen composes the fallback and nothing else`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalMapLibreRuntimeProbe provides { false }) {
                MapLibreMapViewProvider()
                    .MapView(
                        modifier = Modifier,
                        navigateToNodeDetails = {},
                        waypointId = null,
                        sitePlannerNodeNum = null,
                    )
            }
        }
        onNodeWithText(getString(Res.string.map_engine_unavailable)).assertIsDisplayed()
    }
}
