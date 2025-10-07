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

package com.geeksville.mesh.ui.node.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ui.node.isEffectivelyUnmessageable
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SettingsItemDetail

@Composable
internal fun NodeDetailsContent(
    node: Node,
    ourNode: Node?,
    displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits,
) {
    if (node.mismatchKey) {
        EncryptionErrorContent()
    }
    MainNodeDetails(node, ourNode, displayUnits)
}

@Composable
private fun EncryptionErrorContent() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.KeyOff,
            contentDescription = stringResource(id = R.string.encryption_error),
            tint = Color.Red,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.encryption_error),
            style = MaterialTheme.typography.titleLarge.copy(color = Color.Red),
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(id = R.string.encryption_error_text),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun MainNodeDetails(node: Node, ourNode: Node?, displayUnits: ConfigProtos.Config.DisplayConfig.DisplayUnits) {
    SettingsItemDetail(
        text = stringResource(R.string.long_name),
        icon = Icons.TwoTone.Person,
        supportingText = node.user.longName.ifEmpty { "???" },
    )
    SettingsItemDetail(
        text = stringResource(R.string.short_name),
        icon = Icons.Outlined.Person,
        supportingText = node.user.shortName.ifEmpty { "???" },
    )
    SettingsItemDetail(
        text = stringResource(R.string.node_number),
        icon = Icons.Default.Numbers,
        supportingText = node.num.toUInt().toString(),
    )
    SettingsItemDetail(
        text = stringResource(R.string.user_id),
        icon = Icons.Default.Person,
        supportingText = node.user.id,
    )
    SettingsItemDetail(
        text = stringResource(R.string.role),
        icon = Icons.Default.Work,
        supportingText = node.user.role.name,
    )
    if (node.isEffectivelyUnmessageable) {
        SettingsItemDetail(
            text = stringResource(R.string.unmonitored_or_infrastructure),
            icon = Icons.Outlined.NoCell,
            supportingText = null,
        )
    }
    if (node.deviceMetrics.uptimeSeconds > 0) {
        SettingsItemDetail(
            text = stringResource(R.string.uptime),
            icon = Icons.Default.CheckCircle,
            supportingText = formatUptime(node.deviceMetrics.uptimeSeconds),
        )
    }
    SettingsItemDetail(
        text = stringResource(R.string.node_sort_last_heard),
        icon = Icons.Default.History,
        supportingText = formatAgo(node.lastHeard),
    )
}
