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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.NoCell
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.details
import org.meshtastic.core.strings.encryption_error
import org.meshtastic.core.strings.encryption_error_text
import org.meshtastic.core.strings.long_name
import org.meshtastic.core.strings.node_number
import org.meshtastic.core.strings.node_sort_last_heard
import org.meshtastic.core.strings.role
import org.meshtastic.core.strings.short_name
import org.meshtastic.core.strings.unmonitored_or_infrastructure
import org.meshtastic.core.strings.uptime
import org.meshtastic.core.strings.user_id
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.node.model.isEffectivelyUnmessageable

@Composable
fun NodeDetailsSection(node: Node, modifier: Modifier = Modifier) {
    TitledCard(title = stringResource(Res.string.details), modifier = modifier) {
        if (node.mismatchKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.KeyOff,
                    contentDescription = stringResource(Res.string.encryption_error),
                    tint = Color.Red,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.encryption_error),
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.Red),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.encryption_error_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        MainNodeDetails(node)
    }
}

@Composable
private fun MainNodeDetails(node: Node) {
    ListItem(
        text = stringResource(Res.string.long_name),
        leadingIcon = Icons.TwoTone.Person,
        supportingText = node.user.longName.ifEmpty { "???" },
        copyable = true,
        trailingIcon = null,
    )

    InsetDivider()

    ListItem(
        text = stringResource(Res.string.short_name),
        leadingIcon = Icons.Outlined.Person,
        supportingText = node.user.shortName.ifEmpty { "???" },
        copyable = true,
        trailingIcon = null,
    )

    InsetDivider()

    ListItem(
        text = stringResource(Res.string.node_number),
        leadingIcon = Icons.Default.Numbers,
        supportingText = node.num.toUInt().toString(),
        copyable = true,
        trailingIcon = null,
    )

    InsetDivider()

    ListItem(
        text = stringResource(Res.string.user_id),
        leadingIcon = Icons.Default.Person,
        supportingText = node.user.id,
        copyable = true,
        trailingIcon = null,
    )

    InsetDivider()

    ListItem(
        text = stringResource(Res.string.role),
        leadingIcon = Icons.Default.Work,
        supportingText = node.user.role.name,
        trailingIcon = null,
    )

    if (node.isEffectivelyUnmessageable) {
        InsetDivider()

        ListItem(
            text = stringResource(Res.string.unmonitored_or_infrastructure),
            leadingIcon = Icons.Outlined.NoCell,
            trailingIcon = null,
        )
    }
    if (node.deviceMetrics.uptimeSeconds > 0) {
        InsetDivider()

        ListItem(
            text = stringResource(Res.string.uptime),
            leadingIcon = Icons.Default.CheckCircle,
            supportingText = formatUptime(node.deviceMetrics.uptimeSeconds),
            trailingIcon = null,
        )
    }

    InsetDivider()

    ListItem(
        text = stringResource(Res.string.node_sort_last_heard),
        leadingIcon = Icons.Default.History,
        supportingText = formatAgo(node.lastHeard),
        trailingIcon = null,
    )
}
