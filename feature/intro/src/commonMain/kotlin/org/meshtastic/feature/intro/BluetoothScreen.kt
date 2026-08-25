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
import org.meshtastic.core.resources.bluetooth_feature_config
import org.meshtastic.core.resources.bluetooth_feature_config_description
import org.meshtastic.core.resources.bluetooth_feature_discovery
import org.meshtastic.core.resources.bluetooth_feature_discovery_description
import org.meshtastic.core.resources.bluetooth_permission
import org.meshtastic.core.resources.bluetooth_permission_blocked_notice
import org.meshtastic.core.resources.bluetooth_permission_blocked_notice_pre31
import org.meshtastic.core.resources.bluetooth_permission_denied_notice
import org.meshtastic.core.resources.bluetooth_permission_denied_notice_pre31
import org.meshtastic.core.resources.configure_bluetooth_permissions
import org.meshtastic.core.resources.configure_location_permissions
import org.meshtastic.core.resources.permission_missing_31
import org.meshtastic.core.resources.permission_missing_pre31
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.PermissionStatus

/**
 * Screen for configuring Bluetooth permissions during the app introduction. Explains why they are needed and offers the
 * recovery that matches [status].
 *
 * @param status Live permission status; drives the primary action and the denied/blocked notice.
 * @param requiresLocation True on API < 31, where BLE scanning is gated by the location permission instead of "Nearby
 *   devices". The copy follows, because naming a permission the user will never be shown is worse than saying nothing.
 * @param onSkip Callback invoked if the user chooses to skip Bluetooth permission setup.
 * @param onPrimaryAction Callback for the status-driven primary action (request, open settings, or advance).
 */
@Composable
internal fun BluetoothScreen(
    status: PermissionStatus,
    requiresLocation: Boolean,
    onSkip: () -> Unit,
    onPrimaryAction: () -> Unit,
) {
    val features =
        listOf(
            FeatureUIData(
                icon = MeshtasticIcons.Bluetooth,
                titleRes = Res.string.bluetooth_feature_discovery,
                subtitleRes = Res.string.bluetooth_feature_discovery_description,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.Antenna,
                titleRes = Res.string.bluetooth_feature_config,
                subtitleRes = Res.string.bluetooth_feature_config_description,
            ),
        )

    PermissionScreenLayout(
        headlineRes = Res.string.bluetooth_permission,
        descriptionRes =
        if (requiresLocation) Res.string.permission_missing_pre31 else Res.string.permission_missing_31,
        features = features,
        status = status,
        deniedNoticeRes =
        if (requiresLocation) {
            Res.string.bluetooth_permission_denied_notice_pre31
        } else {
            Res.string.bluetooth_permission_denied_notice
        },
        blockedNoticeRes =
        if (requiresLocation) {
            Res.string.bluetooth_permission_blocked_notice_pre31
        } else {
            Res.string.bluetooth_permission_blocked_notice
        },
        configureButtonTextRes =
        if (requiresLocation) {
            Res.string.configure_location_permissions
        } else {
            Res.string.configure_bluetooth_permissions
        },
        onSkip = onSkip,
        onPrimaryAction = onPrimaryAction,
    )
}

@PreviewLightDark
@Composable
private fun BluetoothScreenPreview() {
    AppTheme {
        Surface {
            BluetoothScreen(
                status = PermissionStatus.NOT_REQUESTED,
                requiresLocation = false,
                onSkip = {},
                onPrimaryAction = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BluetoothScreenBlockedPreview() {
    AppTheme {
        Surface {
            BluetoothScreen(
                status = PermissionStatus.PERMANENTLY_DENIED,
                requiresLocation = false,
                onSkip = {},
                onPrimaryAction = {},
            )
        }
    }
}
