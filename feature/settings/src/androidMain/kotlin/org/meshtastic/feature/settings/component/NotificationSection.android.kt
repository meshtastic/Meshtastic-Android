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

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.configure_notification_permissions
import org.meshtastic.core.ui.component.ListItem

@Composable
actual fun NotificationSection(
    messagesEnabled: Boolean,
    onToggleMessages: (Boolean) -> Unit,
    nodeEventsEnabled: Boolean,
    onToggleNodeEvents: (Boolean) -> Unit,
    lowBatteryEnabled: Boolean,
    onToggleLowBattery: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    ExpressiveSection(title = stringResource(Res.string.app_notifications)) {
        ListItem(
            text = stringResource(Res.string.configure_notification_permissions),
            leadingIcon = Icons.Rounded.Notifications,
            trailingIcon = null,
            onClick = {
                val intent =
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                settingsLauncher.launch(intent)
            },
        )
    }
}
