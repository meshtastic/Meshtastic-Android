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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Waypoint

private fun sampleGeofence(lockedTo: Int) = Waypoint(
    id = 42,
    name = "Trailhead",
    description = "North gate of the reserve",
    latitude_i = 377_749_000,
    longitude_i = -1_224_194_000,
    geofence_radius = 500,
    notify_on_enter = true,
    locked_to = lockedTo,
)

/** Locked foreign geofence: read-only, opt-in off, no Edit affordance. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic")
fun WaypointInfoDialogReadOnlyPreview() {
    AppTheme {
        WaypointInfoDialog(
            waypoint = sampleGeofence(lockedTo = 7),
            displayUnits = DisplayUnits.METRIC,
            alertsEnabled = false,
            onToggleAlerts = {},
            onDismissRequest = {},
            onEdit = null,
        )
    }
}

/** Unlocked foreign geofence: opted in, with the Edit affordance into the full editor. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic")
fun WaypointInfoDialogOptedInPreview() {
    AppTheme {
        WaypointInfoDialog(
            waypoint = sampleGeofence(lockedTo = 0),
            displayUnits = DisplayUnits.METRIC,
            alertsEnabled = true,
            onToggleAlerts = {},
            onDismissRequest = {},
            onEdit = {},
        )
    }
}
