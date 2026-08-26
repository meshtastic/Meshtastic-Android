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
package org.meshtastic.feature.intro

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.configure_location_permissions
import org.meshtastic.core.resources.distance_filters
import org.meshtastic.core.resources.distance_filters_description
import org.meshtastic.core.resources.distance_measurements
import org.meshtastic.core.resources.distance_measurements_description
import org.meshtastic.core.resources.location_permission_blocked_notice
import org.meshtastic.core.resources.location_permission_denied_notice
import org.meshtastic.core.resources.mesh_map_location
import org.meshtastic.core.resources.mesh_map_location_description
import org.meshtastic.core.resources.phone_location
import org.meshtastic.core.resources.phone_location_description
import org.meshtastic.core.resources.share_location
import org.meshtastic.core.resources.share_location_description
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.PermissionStatus

/**
 * Screen for configuring location permissions during the app introduction. Explains why they are needed and offers the
 * recovery that matches [status].
 *
 * Declining here is genuinely optional — every location-backed feature re-asks in context (the map, the compass, the
 * "provide location to mesh" toggle), so the notice says "later" rather than "never".
 *
 * @param status Live permission status; drives the primary action and the denied/blocked notice.
 * @param onSkip Callback invoked if the user chooses to skip location permission setup.
 * @param onPrimaryAction Callback for the status-driven primary action (request, open settings, or advance).
 */
@Composable
internal fun LocationScreen(status: PermissionStatus, onSkip: () -> Unit, onPrimaryAction: () -> Unit) {
    val features =
        listOf(
            FeatureUIData(
                icon = MeshtasticIcons.LocationOn,
                titleRes = Res.string.share_location,
                subtitleRes = Res.string.share_location_description,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.HardwareModel,
                titleRes = Res.string.distance_measurements,
                subtitleRes = Res.string.distance_measurements_description,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.HardwareModel,
                titleRes = Res.string.distance_filters,
                subtitleRes = Res.string.distance_filters_description,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.LocationOn,
                titleRes = Res.string.mesh_map_location,
                subtitleRes = Res.string.mesh_map_location_description,
            ),
        )

    PermissionScreenLayout(
        headlineRes = Res.string.phone_location,
        descriptionRes = Res.string.phone_location_description,
        features = features,
        status = status,
        deniedNoticeRes = Res.string.location_permission_denied_notice,
        blockedNoticeRes = Res.string.location_permission_blocked_notice,
        configureButtonTextRes = Res.string.configure_location_permissions,
        onSkip = onSkip,
        onPrimaryAction = onPrimaryAction,
    )
}

@PreviewLightDark
@Composable
private fun LocationScreenPreview() {
    AppTheme { Surface { LocationScreen(status = PermissionStatus.NOT_REQUESTED, onSkip = {}, onPrimaryAction = {}) } }
}

@PreviewLightDark
@Composable
private fun LocationScreenDeniedPreview() {
    AppTheme {
        Surface { LocationScreen(status = PermissionStatus.DENIED_CAN_RETRY, onSkip = {}, onPrimaryAction = {}) }
    }
}
