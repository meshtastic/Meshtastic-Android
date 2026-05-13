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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_filter
import org.meshtastic.core.resources.orient_north
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.toggle_my_position
import org.meshtastic.core.ui.icon.LocationDisabled
import org.meshtastic.core.ui.icon.MapCompass
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Tune
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

/**
 * Shared map controls overlay using [HorizontalFloatingToolbar] for Material 3 Expressive styling. Provides compass,
 * filter button, location tracking button, and optional slots for flavor-specific content (map type selector, layers,
 * refresh).
 *
 * @param onToggleFilterMenu Callback to open/close the filter dropdown.
 * @param filterDropdownContent Composable rendered inside a [Box] alongside the filter button — typically a
 *   `DropdownMenu` with filter options.
 * @param mapTypeContent Optional composable for a map type selector button + dropdown. Google flavor provides map type
 *   and custom tile options; F-Droid provides a tile source selector.
 * @param layersContent Optional composable for a layers management button.
 * @param showRefresh Whether to show a refresh button (e.g., for network map layers).
 * @param isRefreshing Whether a refresh is currently in progress.
 * @param onRefresh Callback when the refresh button is clicked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongParameterList")
@Composable
fun MapControlsOverlay(
    onToggleFilterMenu: () -> Unit,
    modifier: Modifier = Modifier,
    bearing: Float = 0f,
    onCompassClick: () -> Unit = {},
    followPhoneBearing: Boolean = false,
    filterDropdownContent: @Composable () -> Unit = {},
    mapTypeContent: @Composable () -> Unit = {},
    layersContent: @Composable () -> Unit = {},
    isLocationTrackingEnabled: Boolean = false,
    onToggleLocationTracking: () -> Unit = {},
    showRefresh: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        // Compass
        CompassButton(onClick = onCompassClick, bearing = bearing, isFollowing = followPhoneBearing)

        // Filter button + dropdown
        Box {
            MapButton(
                icon = MeshtasticIcons.Tune,
                contentDescription = stringResource(Res.string.map_filter),
                onClick = onToggleFilterMenu,
            )
            filterDropdownContent()
        }

        // Map type selector (flavor-specific)
        mapTypeContent()

        // Layers button (flavor-specific)
        layersContent()

        // Refresh button (optional)
        if (showRefresh) {
            if (isRefreshing) {
                Box(modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                MapButton(
                    icon = MeshtasticIcons.Refresh,
                    contentDescription = stringResource(Res.string.refresh),
                    onClick = onRefresh,
                )
            }
        }

        // Location tracking button
        MapButton(
            icon = if (isLocationTrackingEnabled) MeshtasticIcons.LocationDisabled else MeshtasticIcons.MyLocation,
            contentDescription = stringResource(Res.string.toggle_my_position),
            onClick = onToggleLocationTracking,
        )
    }
}

@Composable
private fun CompassButton(onClick: () -> Unit, bearing: Float, isFollowing: Boolean) {
    val iconTint =
        when {
            isFollowing -> MaterialTheme.colorScheme.primary
            bearing == 0f -> MaterialTheme.colorScheme.StatusRed
            else -> null
        }
    MapButton(
        modifier = Modifier.rotate(-bearing),
        icon = MeshtasticIcons.MapCompass,
        iconTint = iconTint,
        contentDescription = stringResource(Res.string.orient_north),
        onClick = onClick,
    )
}
