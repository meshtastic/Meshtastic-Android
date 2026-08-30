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

import androidx.compose.runtime.Stable
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.proto.Config

/**
 * What the map's filter menu can do.
 *
 * Hoisted into its own type so [MapFilterSheet] never holds a view model: each flavour builds this from whichever
 * [org.meshtastic.feature.map.BaseMapViewModel] subclass it has, and the menu stays a pure function of state.
 */
@Stable
@Suppress("LongParameterList") // One callback per control; a bag of lambdas is the point of this type.
class MapFilterActions(
    val onToggleOnlyFavorites: () -> Unit,
    val onToggleShowWaypoints: () -> Unit,
    val onToggleShowPrecisionCircle: () -> Unit,
    val onSelectLastHeard: (LastHeardFilter) -> Unit,
    val onToggleRoleExcluded: (Config.DeviceConfig.Role) -> Unit,
    val onClearExcludedRoles: () -> Unit,
    val onToggleOnlyOnline: () -> Unit,
    val onToggleOnlyDirect: () -> Unit,
    val onToggleExcludeMqtt: () -> Unit,
    val onToggleShowIgnored: () -> Unit,
    val onToggleIncludeUnknown: () -> Unit,
)

/**
 * The standard wiring from a view model to [MapFilterSheet].
 *
 * Both flavours build the identical bag of method references, and eleven of them written out twice is eleven chances
 * for the two maps to drift apart again — which is exactly what [org.meshtastic.feature.map.MapNodePolicy] exists to
 * prevent on the rules side.
 */
fun BaseMapViewModel.mapFilterActions(): MapFilterActions = MapFilterActions(
    onToggleOnlyFavorites = ::toggleOnlyFavorites,
    onToggleShowWaypoints = ::toggleShowWaypointsOnMap,
    onToggleShowPrecisionCircle = ::toggleShowPrecisionCircleOnMap,
    onSelectLastHeard = ::setLastHeardFilter,
    onToggleRoleExcluded = ::toggleRoleExcluded,
    onClearExcludedRoles = ::clearExcludedRoles,
    onToggleOnlyOnline = ::toggleOnlyOnline,
    onToggleOnlyDirect = ::toggleOnlyDirect,
    onToggleExcludeMqtt = ::toggleExcludeMqtt,
    onToggleShowIgnored = ::toggleShowIgnored,
    onToggleIncludeUnknown = ::toggleIncludeUnknown,
)
