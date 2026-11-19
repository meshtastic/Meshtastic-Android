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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.resources.meshtastic_reactions_notifications
import org.meshtastic.core.resources.meshtastic_waypoints_notifications
import org.meshtastic.core.resources.notifications_blocked_by_browser
import org.meshtastic.core.resources.notifications_turn_on
import org.meshtastic.core.resources.notifications_turn_on_summary
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.icon.AddReaction
import org.meshtastic.core.ui.icon.BatteryAlert
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.icon.PersonAdd
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState

/**
 * Notification settings section with in-app toggles. Primarily used on platforms without system notification channels.
 *
 * With a [permission] the toggles depend on, the section leads with a row that asks for it while it can still be asked
 * for, and says it is blocked once it cannot. The ask happens on that row's click, the user gesture a browser requires.
 */
@Composable
fun NotificationSection(
    messagesEnabled: Boolean,
    onToggleMessages: (Boolean) -> Unit,
    nodeEventsEnabled: Boolean,
    onToggleNodeEvents: (Boolean) -> Unit,
    lowBatteryEnabled: Boolean,
    onToggleLowBattery: (Boolean) -> Unit,
    reactionsEnabled: Boolean,
    onToggleReactions: (Boolean) -> Unit,
    newWaypointsEnabled: Boolean,
    onToggleNewWaypoints: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    permission: PermissionUiState? = null,
) {
    ExpressiveSection(title = stringResource(Res.string.app_notifications), modifier = modifier) {
        when (permission?.status) {
            PermissionStatus.NOT_REQUESTED,
            PermissionStatus.DENIED_CAN_RETRY,
            ->
                ListItem(
                    text = stringResource(Res.string.notifications_turn_on),
                    supportingText = stringResource(Res.string.notifications_turn_on_summary),
                    leadingIcon = MeshtasticIcons.Notifications,
                    trailingIcon = null,
                    onClick = permission.request,
                )

            PermissionStatus.PERMANENTLY_DENIED ->
                ListItem(
                    text = stringResource(Res.string.notifications_blocked_by_browser),
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIcon = MeshtasticIcons.Notifications,
                    leadingIconTint = MaterialTheme.colorScheme.error,
                    trailingIcon = null,
                )

            PermissionStatus.GRANTED,
            null,
            -> Unit
        }
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_messages_notifications),
            leadingIcon = MeshtasticIcons.Message,
            checked = messagesEnabled,
            onClick = { onToggleMessages(!messagesEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_new_nodes_notifications),
            leadingIcon = MeshtasticIcons.PersonAdd,
            checked = nodeEventsEnabled,
            onClick = { onToggleNodeEvents(!nodeEventsEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_low_battery_notifications),
            leadingIcon = MeshtasticIcons.BatteryAlert,
            checked = lowBatteryEnabled,
            onClick = { onToggleLowBattery(!lowBatteryEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_reactions_notifications),
            leadingIcon = MeshtasticIcons.AddReaction,
            checked = reactionsEnabled,
            onClick = { onToggleReactions(!reactionsEnabled) },
        )
        SwitchListItem(
            text = stringResource(Res.string.meshtastic_waypoints_notifications),
            leadingIcon = MeshtasticIcons.PinDrop,
            checked = newWaypointsEnabled,
            onClick = { onToggleNewWaypoints(!newWaypointsEnabled) },
        )
    }
}
