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
package org.meshtastic.feature.messaging.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.ui.icon.Acknowledged
import org.meshtastic.core.ui.icon.CloudDone
import org.meshtastic.core.ui.icon.CloudOffTwoTone
import org.meshtastic.core.ui.icon.CloudSync
import org.meshtastic.core.ui.icon.CloudTwoTone
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val icon =
        when (status) {
            MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
            MessageStatus.QUEUED -> MeshtasticIcons.CloudSync
            MessageStatus.DELIVERED -> MeshtasticIcons.CloudDone
            MessageStatus.SFPP_ROUTING -> MeshtasticIcons.CloudSync
            MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.CloudDone
            MessageStatus.ENROUTE -> MeshtasticIcons.CloudTwoTone
            MessageStatus.ERROR -> MeshtasticIcons.CloudOffTwoTone
            else -> MeshtasticIcons.Warning
        }
    Icon(
        modifier = modifier,
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
    )
}
