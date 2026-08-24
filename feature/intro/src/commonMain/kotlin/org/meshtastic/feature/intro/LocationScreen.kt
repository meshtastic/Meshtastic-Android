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
import org.meshtastic.core.resources.mesh_map_location
import org.meshtastic.core.resources.mesh_map_location_description
import org.meshtastic.core.resources.next
import org.meshtastic.core.resources.phone_location
import org.meshtastic.core.resources.phone_location_description
import org.meshtastic.core.resources.settings
import org.meshtastic.core.resources.share_location
import org.meshtastic.core.resources.share_location_description
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme

/**
 * Screen for configuring location permissions during the app introduction. It explains why location permissions are
 * needed and provides options to grant them or skip.
 *
 * @param showNextButton Indicates whether to show a "Next" button (if permissions are already granted) or a "Configure"
 *   button.
 * @param onSkip Callback invoked if the user chooses to skip location permission setup.
 * @param onConfigure Callback invoked when the user proceeds to configure or grant permissions.
 * @param onOpenSettings Callback invoked when the user taps the settings link.
 */
@Composable
internal fun LocationScreen(
    showNextButton: Boolean,
    onSkip: () -> Unit,
    onConfigure: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val annotatedString =
        createClickableAnnotatedString(
            fullTextRes = Res.string.phone_location_description,
            linkTextRes = Res.string.settings,
            tag = SETTINGS_TAG,
        )

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
        annotatedDescription = annotatedString,
        features = features,
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) Res.string.next else Res.string.configure_location_permissions,
        onAnnotationClick = { onOpenSettings() },
    )
}

@PreviewLightDark
@Composable
private fun LocationScreenPreview() {
    AppTheme { Surface { LocationScreen(showNextButton = false, onSkip = {}, onConfigure = {}, onOpenSettings = {}) } }
}
