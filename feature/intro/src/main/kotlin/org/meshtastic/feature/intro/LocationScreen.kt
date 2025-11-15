/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Router
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.configure_location_permissions
import org.meshtastic.core.strings.distance_filters
import org.meshtastic.core.strings.distance_filters_description
import org.meshtastic.core.strings.distance_measurements
import org.meshtastic.core.strings.distance_measurements_description
import org.meshtastic.core.strings.mesh_map_location
import org.meshtastic.core.strings.mesh_map_location_description
import org.meshtastic.core.strings.next
import org.meshtastic.core.strings.phone_location
import org.meshtastic.core.strings.phone_location_description
import org.meshtastic.core.strings.settings
import org.meshtastic.core.strings.share_location
import org.meshtastic.core.strings.share_location_description

/**
 * Screen for configuring location permissions during the app introduction. It explains why location permissions are
 * needed and provides options to grant them or skip.
 *
 * @param showNextButton Indicates whether to show a "Next" button (if permissions are already granted) or a "Configure"
 *   button.
 * @param onSkip Callback invoked if the user chooses to skip location permission setup.
 * @param onConfigure Callback invoked when the user proceeds to configure or grant permissions.
 */
@Composable
internal fun LocationScreen(showNextButton: Boolean, onSkip: () -> Unit, onConfigure: () -> Unit) {
    val context = LocalContext.current
    val annotatedString =
        context.createClickableAnnotatedString(
            fullTextRes = Res.string.phone_location_description,
            linkTextRes = Res.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.LocationOn,
                titleRes = Res.string.share_location,
                subtitleRes = Res.string.share_location_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router,
                titleRes = Res.string.distance_measurements,
                subtitleRes = Res.string.distance_measurements_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router, // Consider a different icon if appropriate
                titleRes = Res.string.distance_filters,
                subtitleRes = Res.string.distance_filters_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.LocationOn, // Consider a different icon if appropriate
                titleRes = Res.string.mesh_map_location,
                subtitleRes = Res.string.mesh_map_location_description,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = Res.string.phone_location,
        annotatedDescription = annotatedString,
        features = features,
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) Res.string.next else Res.string.configure_location_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}
