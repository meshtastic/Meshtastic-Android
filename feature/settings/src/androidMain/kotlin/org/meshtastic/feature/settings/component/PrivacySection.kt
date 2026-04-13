/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.component

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.analytics_okay
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.location_disabled
import org.meshtastic.core.resources.provide_location_to_mesh
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.icon.BugReport
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.showToast

/** Section managing privacy settings like analytics and location sharing. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrivacySection(
    analyticsAvailable: Boolean,
    analyticsEnabled: Boolean,
    onToggleAnalytics: () -> Unit,
    provideLocation: Boolean,
    onToggleLocation: (Boolean) -> Unit,
    homoglyphEnabled: Boolean,
    onToggleHomoglyph: () -> Unit,
    startProvideLocation: () -> Unit,
    stopProvideLocation: () -> Unit,
) {
    val context = LocalContext.current
    val locationPermissionsState =
        rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    val isGpsDisabled = context.gpsDisabled()

    LaunchedEffect(provideLocation, locationPermissionsState.allPermissionsGranted, isGpsDisabled) {
        if (provideLocation) {
            if (locationPermissionsState.allPermissionsGranted) {
                if (!isGpsDisabled) {
                    startProvideLocation()
                } else {
                    context.showToast(Res.string.location_disabled)
                }
            } else {
                locationPermissionsState.launchMultiplePermissionRequest()
            }
        } else {
            stopProvideLocation()
        }
    }

    ExpressiveSection(title = stringResource(Res.string.app_settings)) {
        if (analyticsAvailable) {
            SwitchListItem(
                text = stringResource(Res.string.analytics_okay),
                checked = analyticsEnabled,
                leadingIcon = MeshtasticIcons.BugReport,
                onClick = onToggleAnalytics,
            )
        }

        SwitchListItem(
            text = stringResource(Res.string.provide_location_to_mesh),
            leadingIcon = MeshtasticIcons.LocationOn,
            enabled = !isGpsDisabled,
            checked = provideLocation,
            onClick = { onToggleLocation(!provideLocation) },
        )

        HomoglyphSetting(homoglyphEncodingEnabled = homoglyphEnabled, onToggle = onToggleHomoglyph)
    }
}

@Preview(showBackground = true)
@Composable
fun PrivacySectionPreview() {
    AppTheme {
        PrivacySection(
            analyticsAvailable = true,
            analyticsEnabled = true,
            onToggleAnalytics = {},
            provideLocation = true,
            onToggleLocation = {},
            homoglyphEnabled = false,
            onToggleHomoglyph = {},
            startProvideLocation = {},
            stopProvideLocation = {},
        )
    }
}
