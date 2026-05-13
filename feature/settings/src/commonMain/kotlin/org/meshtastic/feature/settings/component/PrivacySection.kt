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
package org.meshtastic.feature.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.analytics_okay
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.location_disabled
import org.meshtastic.core.resources.provide_location_to_mesh
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.icon.BugReport
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.isGpsDisabled
import org.meshtastic.core.ui.util.isLocationPermissionGranted
import org.meshtastic.core.ui.util.rememberRequestLocationPermission
import org.meshtastic.core.ui.util.rememberShowToastResource

/** Section managing privacy settings like analytics and location sharing. */
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
    val showToast = rememberShowToastResource()
    val isLocationGranted = isLocationPermissionGranted()
    val isGpsOff = isGpsDisabled()
    val requestLocationPermission =
        rememberRequestLocationPermission(onGranted = { startProvideLocation() }, onDenied = {})

    LaunchedEffect(provideLocation, isLocationGranted, isGpsOff) {
        if (provideLocation) {
            if (isLocationGranted) {
                if (!isGpsOff) {
                    startProvideLocation()
                } else {
                    showToast(Res.string.location_disabled)
                }
            } else {
                requestLocationPermission()
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
            enabled = !isGpsOff,
            checked = provideLocation,
            onClick = { onToggleLocation(!provideLocation) },
        )

        HomoglyphSetting(homoglyphEncodingEnabled = homoglyphEnabled, onToggle = onToggleHomoglyph)
    }
}
