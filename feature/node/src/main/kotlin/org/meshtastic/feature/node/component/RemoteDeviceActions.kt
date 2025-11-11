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

package org.meshtastic.feature.node.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.direct_message
import org.meshtastic.core.strings.exchange_userinfo
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable

@Composable
internal fun RemoteDeviceActions(node: Node, lastTracerouteTime: Long?, onAction: (NodeDetailAction) -> Unit) {
    if (!node.isEffectivelyUnmessageable) {
        ListItem(
            text = stringResource(Res.string.direct_message),
            leadingIcon = Icons.AutoMirrored.TwoTone.Message,
            trailingIcon = null,
            onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node))) },
        )

        InsetDivider()
    }

    ListItem(
        text = stringResource(Res.string.exchange_userinfo),
        leadingIcon = Icons.Default.Person,
        trailingIcon = null,
        onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.RequestUserInfo(node))) },
    )

    InsetDivider()

    TracerouteButton(
        lastTracerouteTime = lastTracerouteTime,
        onClick = { onAction(NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.TraceRoute(node))) },
    )
}
