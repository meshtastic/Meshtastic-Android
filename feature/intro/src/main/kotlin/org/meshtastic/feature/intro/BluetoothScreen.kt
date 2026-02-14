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
package org.meshtastic.feature.intro

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.bluetooth_feature_config
import org.meshtastic.core.strings.bluetooth_feature_config_description
import org.meshtastic.core.strings.bluetooth_feature_discovery
import org.meshtastic.core.strings.bluetooth_feature_discovery_description
import org.meshtastic.core.strings.bluetooth_permission
import org.meshtastic.core.strings.configure_bluetooth_permissions
import org.meshtastic.core.strings.next
import org.meshtastic.core.strings.permission_missing_31
import org.meshtastic.core.strings.settings

/**
 * Screen for configuring Bluetooth permissions during the app introduction. It explains why Bluetooth permissions are
 * needed and provides options to grant them or skip.
 *
 * @param showNextButton Indicates whether to show a "Next" button (if permissions are already granted) or a "Configure"
 *   button.
 * @param onSkip Callback invoked if the user chooses to skip Bluetooth permission setup.
 * @param onConfigure Callback invoked when the user proceeds to configure or grant permissions.
 */
@Composable
internal fun BluetoothScreen(showNextButton: Boolean, onSkip: () -> Unit, onConfigure: () -> Unit) {
    val context = LocalContext.current
    val annotatedString =
        context.createClickableAnnotatedString(
            fullTextRes = Res.string.permission_missing_31,
            linkTextRes = Res.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.Bluetooth,
                titleRes = Res.string.bluetooth_feature_discovery,
                subtitleRes = Res.string.bluetooth_feature_discovery_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.SettingsInputAntenna,
                titleRes = Res.string.bluetooth_feature_config,
                subtitleRes = Res.string.bluetooth_feature_config_description,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = Res.string.bluetooth_permission,
        annotatedDescription = annotatedString,
        features = features,
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) Res.string.next else Res.string.configure_bluetooth_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}
