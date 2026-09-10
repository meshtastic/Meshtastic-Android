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
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Waypoint

// Sample coordinates for the previews below. Exempt as named arguments until the
// buildersOnly rewrite turned them into assignments; the previews themselves are
// covered by MagicNumber's ignoreAnnotated.
@Suppress("MagicNumber")
private fun sampleGeofence(lockedTo: Int) = Waypoint.Builder()
    .also { wb ->
        wb.id = 42
        wb.name = "Trailhead"
        wb.description = "North gate of the reserve"
        wb.latitude_i = 377_749_000
        wb.longitude_i = -1_224_194_000
        wb.geofence_radius = 500
        wb.notify_on_enter = true
        wb.locked_to = lockedTo
    }
    .build()

/** Locked foreign geofence: opt-in off, no Edit affordance — but the local delete is still offered. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic")
fun WaypointInfoDialogReadOnlyPreview() {
    AppTheme {
        WaypointInfoDialog(
            waypoint = sampleGeofence(lockedTo = 7),
            displayUnits = MeasurementSystem.METRIC,
            alertsEnabled = false,
            onToggleAlerts = {},
            onDismissRequest = {},
            onEdit = null,
            onDeleteForMe = {},
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
            displayUnits = MeasurementSystem.METRIC,
            alertsEnabled = true,
            onToggleAlerts = {},
            onDismissRequest = {},
            onEdit = {},
            onDeleteForMe = {},
        )
    }
}
