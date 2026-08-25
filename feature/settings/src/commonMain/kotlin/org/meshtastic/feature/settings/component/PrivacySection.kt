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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.analytics_okay
import org.meshtastic.core.resources.location_disabled
import org.meshtastic.core.resources.location_permission
import org.meshtastic.core.resources.location_permission_blocked_toast
import org.meshtastic.core.resources.location_permission_rationale
import org.meshtastic.core.resources.provide_location_to_mesh
import org.meshtastic.core.ui.component.PermissionRationaleDialog
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.icon.BugReport
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.PermissionGateAction
import org.meshtastic.core.ui.util.isGpsDisabled
import org.meshtastic.core.ui.util.permissionGateAction
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.rememberShowToastResource

/** Section managing privacy settings like analytics and location sharing. */
@Composable
internal fun ColumnScope.PrivacySettingsContent(
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
    val locationPermission = rememberLocationPermissionState()
    val isGpsOff = isGpsDisabled()

    var showLocationRationale by remember { mutableStateOf(false) }

    if (showLocationRationale) {
        PermissionRationaleDialog(
            titleRes = Res.string.location_permission,
            rationaleRes = Res.string.location_permission_rationale,
            icon = MeshtasticIcons.LocationOn,
            onConfirm = {
                showLocationRationale = false
                locationPermission.request()
            },
            onDismiss = {
                showLocationRationale = false
                // The user declined again, so the toggle must not stay on describing something that is not happening.
                onToggleLocation(false)
            },
        )
    }

    // Keyed on the full status, not just the grant. The previous version called request() for every not-granted case
    // and noted in a comment that it was "a harmless no-op once permanently denied" — but a no-op is the whole control
    // when it is the only thing the switch does: it flipped, nothing happened, and nothing said why.
    LaunchedEffect(provideLocation, locationPermission.status, isGpsOff) {
        if (!provideLocation) {
            stopProvideLocation()
            return@LaunchedEffect
        }
        when (permissionGateAction(locationPermission.status)) {
            PermissionGateAction.PROCEED ->
                if (!isGpsOff) startProvideLocation() else showToast(Res.string.location_disabled)

            PermissionGateAction.REQUEST -> locationPermission.request()

            PermissionGateAction.SHOW_RATIONALE -> showLocationRationale = true

            // Requesting here would do nothing at all, so send the user where the switch can actually be honoured and
            // put it back until it can be.
            PermissionGateAction.OPEN_SETTINGS -> {
                onToggleLocation(false)
                showToast(Res.string.location_permission_blocked_toast)
                locationPermission.openAppSettings()
            }
        }
    }

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
