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

package org.meshtastic.feature.intro

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.SpeakerPhone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.app_notifications
import org.meshtastic.core.strings.configure_notification_permissions
import org.meshtastic.core.strings.critical_alerts
import org.meshtastic.core.strings.critical_alerts_description
import org.meshtastic.core.strings.incoming_messages
import org.meshtastic.core.strings.low_battery
import org.meshtastic.core.strings.new_nodes
import org.meshtastic.core.strings.next
import org.meshtastic.core.strings.notification_permissions_description
import org.meshtastic.core.strings.notifications_for_channel_and_direct_messages
import org.meshtastic.core.strings.notifications_for_low_battery_alerts
import org.meshtastic.core.strings.notifications_for_newly_discovered_nodes
import org.meshtastic.core.strings.settings

/**
 * Screen for configuring notification permissions during the app introduction. It explains why notification permissions
 * are needed and provides options to grant them or skip.
 *
 * @param showNextButton Indicates whether to show a "Next" button (if permissions are already granted) or a "Configure"
 *   button.
 * @param onSkip Callback invoked if the user chooses to skip notification permission setup.
 * @param onConfigure Callback invoked when the user proceeds to configure or grant permissions.
 */
@Composable
internal fun NotificationsScreen(showNextButton: Boolean, onSkip: () -> Unit, onConfigure: () -> Unit) {
    val context = LocalContext.current
    val annotatedString =
        context.createClickableAnnotatedString(
            fullTextRes = Res.string.notification_permissions_description,
            linkTextRes = Res.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.Message,
                titleRes = Res.string.incoming_messages,
                subtitleRes = Res.string.notifications_for_channel_and_direct_messages,
            ),
            FeatureUIData(
                icon = Icons.Outlined.SpeakerPhone,
                titleRes = Res.string.new_nodes,
                subtitleRes = Res.string.notifications_for_newly_discovered_nodes,
            ),
            FeatureUIData(
                icon = Icons.Outlined.BatteryAlert,
                titleRes = Res.string.low_battery,
                subtitleRes = Res.string.notifications_for_low_battery_alerts,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = Res.string.app_notifications,
        annotatedDescription = annotatedString,
        features = features,
        additionalContent = {
            Text(
                text = stringResource(Res.string.critical_alerts),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeatureRow(
                feature =
                FeatureUIData(
                    icon = Icons.Filled.Notifications,
                    subtitleRes = Res.string.critical_alerts_description,
                ),
            )
        },
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) Res.string.next else Res.string.configure_notification_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}
