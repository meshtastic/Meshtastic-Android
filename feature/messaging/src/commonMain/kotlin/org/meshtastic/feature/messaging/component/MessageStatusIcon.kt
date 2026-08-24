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

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import org.jetbrains.compose.resources.stringResource
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
import org.meshtastic.core.ui.theme.StatusColors.StatusBlue
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    includeContentDescription: Boolean = true,
) {
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
    val contentDescription =
        if (includeContentDescription) {
            stringResource(Res.string.message_delivery_status)
        } else {
            null
        }
    Icon(
        modifier = modifier,
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint.takeOrElse { LocalContentColor.current },
    )
}

@Composable
internal fun messageStatusColor(status: MessageStatus, isWarning: Boolean = false): Color {
    val colorScheme = MaterialTheme.colorScheme
    if (isWarning) {
        return colorScheme.StatusYellow
    }
    return when (status) {
        MessageStatus.RECEIVED,
        MessageStatus.DELIVERED,
        MessageStatus.SFPP_CONFIRMED,
        -> colorScheme.StatusGreen

        MessageStatus.QUEUED,
        MessageStatus.UNKNOWN,
        -> colorScheme.StatusYellow

        MessageStatus.ENROUTE,
        MessageStatus.SFPP_ROUTING,
        -> colorScheme.StatusBlue

        MessageStatus.ERROR -> colorScheme.StatusRed
    }
}
