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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.map_engine_unavailable
import org.meshtastic.core.resources.map_engine_unavailable_detail
import kotlin.test.Test

/**
 * Deliberately actionless: there is nothing a user can do about their phone's architecture, so the screen must say what
 * happened and offer nothing to tap.
 */
@OptIn(ExperimentalTestApi::class)
class MapEngineUnavailableTest {

    @Test
    fun `says what happened and that the rest of the app is fine`() = runComposeUiTest {
        setContent { MapEngineUnavailable() }
        onNodeWithText(getString(Res.string.map_engine_unavailable)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.map_engine_unavailable_detail)).assertIsDisplayed()
    }

    @Test
    fun `offers nothing to tap`() = runComposeUiTest {
        setContent { MapEngineUnavailable() }
        onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
