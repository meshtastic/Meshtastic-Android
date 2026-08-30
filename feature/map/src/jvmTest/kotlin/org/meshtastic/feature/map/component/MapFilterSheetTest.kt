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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MapFilterSheetTest {

    private val router = Config.DeviceConfig.Role.ROUTER

    private fun state(excludedRoles: Set<Config.DeviceConfig.Role> = emptySet()) = BaseMapViewModel.MapFilterState(
        onlyFavorites = false,
        showWaypoints = true,
        showPrecisionCircle = true,
        lastHeardFilter = LastHeardFilter.Any,
        lastHeardTrackFilter = LastHeardFilter.Any,
        excludedRoles = excludedRoles,
    )

    private fun actions(onToggleRole: (Config.DeviceConfig.Role) -> Unit = {}) = MapFilterActions(
        onToggleOnlyFavorites = {},
        onToggleShowWaypoints = {},
        onToggleShowPrecisionCircle = {},
        onSelectLastHeard = {},
        onToggleRoleExcluded = onToggleRole,
        onClearExcludedRoles = {},
        onToggleOnlyOnline = {},
        onToggleOnlyDirect = {},
        onToggleExcludeMqtt = {},
        onToggleShowIgnored = {},
        onToggleIncludeUnknown = {},
    )

    @Test
    fun `a role chip reads as selected when that role is shown`() = runComposeUiTest {
        // The state behind the row is the complement — a set of excluded roles — so the chip's selected flag is the
        // one place that inversion can go wrong, and it would look like the filter working backwards.
        setContent { MapFilterSheetContent(filterState = state(), actions = actions()) }

        onNodeWithTag(roleFilterChipTestTag(router)).assertIsSelected()
    }

    @Test
    fun `a role chip reads as unselected once that role is excluded`() = runComposeUiTest {
        setContent { MapFilterSheetContent(filterState = state(excludedRoles = setOf(router)), actions = actions()) }

        onNodeWithTag(roleFilterChipTestTag(router)).assertIsNotSelected()
    }

    @Test
    fun `tapping a role chip reports that role`() = runComposeUiTest {
        val toggled = mutableListOf<Config.DeviceConfig.Role>()
        setContent { MapFilterSheetContent(filterState = state(), actions = actions { toggled += it }) }

        onNodeWithTag(roleFilterChipTestTag(router)).performClick()

        runOnIdle { assertEquals(listOf(router), toggled) }
    }

    @Test
    fun `every role has a chip`() = runComposeUiTest {
        // A role with no chip is a role the user can never hide, and one that silently stays on the map.
        setContent { MapFilterSheetContent(filterState = state(), actions = actions()) }

        @Suppress("DEPRECATION")
        Config.DeviceConfig.Role.entries.forEach { role -> onNodeWithTag(roleFilterChipTestTag(role)).assertExists() }
    }
}
