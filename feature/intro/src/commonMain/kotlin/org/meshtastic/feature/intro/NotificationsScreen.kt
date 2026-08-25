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
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.configure_notification_permissions
import org.meshtastic.core.resources.incoming_messages
import org.meshtastic.core.resources.low_battery
import org.meshtastic.core.resources.new_nodes
import org.meshtastic.core.resources.notification_permission_blocked_notice
import org.meshtastic.core.resources.notification_permission_denied_notice
import org.meshtastic.core.resources.notification_permissions_description
import org.meshtastic.core.resources.notifications_for_channel_and_direct_messages
import org.meshtastic.core.resources.notifications_for_low_battery_alerts
import org.meshtastic.core.resources.notifications_for_newly_discovered_nodes
import org.meshtastic.core.ui.icon.BatteryAlert
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.Speaker
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.PermissionStatus

/**
 * Screen for configuring notification permissions during the app introduction. Explains why they are needed and offers
 * the recovery that matches [status].
 *
 * @param status Live permission status; drives the primary action and the denied/blocked notice.
 * @param onSkip Callback invoked if the user chooses to skip notification permission setup.
 * @param onPrimaryAction Callback for the status-driven primary action (request, open settings, or advance).
 */
@Composable
internal fun NotificationsScreen(status: PermissionStatus, onSkip: () -> Unit, onPrimaryAction: () -> Unit) {
    val features =
        listOf(
            FeatureUIData(
                icon = MeshtasticIcons.Message,
                titleRes = Res.string.incoming_messages,
                subtitleRes = Res.string.notifications_for_channel_and_direct_messages,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.Speaker,
                titleRes = Res.string.new_nodes,
                subtitleRes = Res.string.notifications_for_newly_discovered_nodes,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.BatteryAlert,
                titleRes = Res.string.low_battery,
                subtitleRes = Res.string.notifications_for_low_battery_alerts,
            ),
        )

    PermissionScreenLayout(
        headlineRes = Res.string.app_notifications,
        descriptionRes = Res.string.notification_permissions_description,
        features = features,
        status = status,
        deniedNoticeRes = Res.string.notification_permission_denied_notice,
        blockedNoticeRes = Res.string.notification_permission_blocked_notice,
        configureButtonTextRes = Res.string.configure_notification_permissions,
        onSkip = onSkip,
        onPrimaryAction = onPrimaryAction,
    )
}

@PreviewLightDark
@Composable
private fun NotificationsScreenPreview() {
    AppTheme {
        Surface { NotificationsScreen(status = PermissionStatus.NOT_REQUESTED, onSkip = {}, onPrimaryAction = {}) }
    }
}
