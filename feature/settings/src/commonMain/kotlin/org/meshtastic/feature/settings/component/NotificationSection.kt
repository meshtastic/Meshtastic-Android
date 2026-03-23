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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.ui.component.SwitchListItem

/**
 * Notification settings section with in-app toggles. Primarily used on platforms without system notification channels.
 */
@Composable
fun NotificationSection(
    messagesEnabled: Boolean,
    onToggleMessages: (Boolean) -> Unit,
    nodeEventsEnabled: Boolean,
    onToggleNodeEvents: (Boolean) -> Unit,
    lowBatteryEnabled: Boolean,
    onToggleLowBattery: (Boolean) -> Unit,
) {
    ExpressiveSection(title = stringResource(Res.string.app_notifications)) {
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_messages_notifications),
            leadingIcon = Icons.AutoMirrored.Rounded.Message,
            checked = messagesEnabled,
            onClick = { onToggleMessages(!messagesEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_new_nodes_notifications),
            leadingIcon = Icons.Rounded.PersonAdd,
            checked = nodeEventsEnabled,
            onClick = { onToggleNodeEvents(!nodeEventsEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_low_battery_notifications),
            leadingIcon = Icons.Rounded.BatteryAlert,
            checked = lowBatteryEnabled,
            onClick = { onToggleLowBattery(!lowBatteryEnabled) },
        )
    }
}
