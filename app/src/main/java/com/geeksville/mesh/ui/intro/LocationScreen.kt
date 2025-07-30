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

package com.geeksville.mesh.ui.intro

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Router
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.geeksville.mesh.R

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
            fullTextRes = R.string.phone_location_description,
            linkTextRes = R.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.LocationOn,
                titleRes = R.string.share_location,
                subtitleRes = R.string.share_location_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router,
                titleRes = R.string.distance_measurements,
                subtitleRes = R.string.distance_measurements_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router, // Consider a different icon if appropriate
                titleRes = R.string.distance_filters,
                subtitleRes = R.string.distance_filters_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.LocationOn, // Consider a different icon if appropriate
                titleRes = R.string.mesh_map_location,
                subtitleRes = R.string.mesh_map_location_description,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = R.string.phone_location,
        annotatedDescription = annotatedString,
        features = features,
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) R.string.next else R.string.configure_location_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}
