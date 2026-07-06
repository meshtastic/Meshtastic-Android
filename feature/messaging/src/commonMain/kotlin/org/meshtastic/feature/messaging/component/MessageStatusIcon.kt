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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.ui.icon.Acknowledged
import org.meshtastic.core.ui.icon.AddLink
import org.meshtastic.core.ui.icon.CloudUpload
import org.meshtastic.core.ui.icon.LinkIcon
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MessageEnroute
import org.meshtastic.core.ui.icon.MessageError
import org.meshtastic.core.ui.icon.MqttDelivered
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val icon =
        when (status) {
            MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
            MessageStatus.QUEUED -> MeshtasticIcons.CloudUpload
            MessageStatus.DELIVERED -> MeshtasticIcons.MqttDelivered
            MessageStatus.SFPP_ROUTING -> MeshtasticIcons.AddLink
            MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.LinkIcon
            MessageStatus.ENROUTE -> MeshtasticIcons.MessageEnroute
            MessageStatus.ERROR -> MeshtasticIcons.MessageError
            else -> MeshtasticIcons.Warning
        }
    Icon(
        modifier = modifier,
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
        tint = tint,
    )
}

/**
 * Delivery status shown as text next to the bubble (meshtastic/design#43) — the descriptive wording, not just an icon.
 * The icon and color reinforce the text; they are never the sole signal. The parent bubble carries the same wording in
 * its merged [contentDescription] for TalkBack, so this row stays a visual-only reinforcement.
 */
@Composable
fun MessageStatusLabel(message: Message, modifier: Modifier = Modifier) {
    val status = message.status ?: MessageStatus.UNKNOWN
    val (_, textRes) = message.getStatusStringRes()
    val tint = messageStatusColor(message)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MessageStatusIcon(status = status, modifier = Modifier.size(14.dp), tint = tint)
        Text(text = stringResource(textRes), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun messageStatusColor(message: Message): Color = when (message.status) {
    MessageStatus.RECEIVED,
    MessageStatus.SFPP_CONFIRMED,
    -> MaterialTheme.colorScheme.StatusGreen

    MessageStatus.DELIVERED ->
        if (message.isBroadcast) MaterialTheme.colorScheme.StatusGreen else MaterialTheme.colorScheme.StatusOrange

    MessageStatus.QUEUED,
    MessageStatus.ENROUTE,
    MessageStatus.SFPP_ROUTING,
    -> MaterialTheme.colorScheme.StatusYellow

    MessageStatus.ERROR -> MaterialTheme.colorScheme.StatusRed

    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
