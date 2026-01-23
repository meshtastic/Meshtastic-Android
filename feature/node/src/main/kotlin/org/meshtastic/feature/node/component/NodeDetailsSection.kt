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
package org.meshtastic.feature.node.component

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.details
import org.meshtastic.core.strings.encryption_error
import org.meshtastic.core.strings.encryption_error_text
import org.meshtastic.core.strings.hops_away
import org.meshtastic.core.strings.node_id
import org.meshtastic.core.strings.node_number
import org.meshtastic.core.strings.node_sort_last_heard
import org.meshtastic.core.strings.public_key
import org.meshtastic.core.strings.role
import org.meshtastic.core.strings.rssi
import org.meshtastic.core.strings.short_name
import org.meshtastic.core.strings.snr
import org.meshtastic.core.strings.supported
import org.meshtastic.core.strings.uptime
import org.meshtastic.core.strings.user_id
import org.meshtastic.core.strings.via_mqtt
import org.meshtastic.core.ui.util.formatAgo

@Composable
fun NodeDetailsSection(node: Node, modifier: Modifier = Modifier) {
    SectionCard(title = Res.string.details, modifier = modifier) {
        Column {
            if (node.mismatchKey) {
                MismatchKeyWarning(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(16.dp))
            }
            MainNodeDetails(node)
        }
    }
}

@Composable
private fun MismatchKeyWarning(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.KeyOff,
                    contentDescription = stringResource(Res.string.encryption_error),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.encryption_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.encryption_error_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MainNodeDetails(node: Node) {
    Column {
        NameAndRoleRow(node)
        SectionDivider()
        NodeIdentificationRow(node)
        SectionDivider()
        HearsAndHopsRow(node)
        SectionDivider()
        UserAndUptimeRow(node)
        SectionDivider()
        SignalRow(node)
        if (node.viaMqtt || node.manuallyVerified) {
            SectionDivider()
            MqttAndVerificationRow(node)
        }
        val publicKey = node.publicKey ?: node.user.publicKey
        if (!publicKey.isEmpty) {
            SectionDivider()
            InfoItem(
                label = stringResource(Res.string.public_key),
                value = Base64.encodeToString(publicKey.toByteArray(), Base64.DEFAULT).trim(),
                icon = Icons.Default.Lock,
                valueStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun NameAndRoleRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
            label = stringResource(Res.string.short_name),
            value = node.user.shortName.ifEmpty { "???" },
            icon = Icons.Default.Person,
            modifier = Modifier.weight(1f),
        )
        InfoItem(
            label = stringResource(Res.string.role),
            value = node.user.role.name,
            icon = Icons.Default.Work,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NodeIdentificationRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
            label = stringResource(Res.string.node_id),
            value = DataPacket.nodeNumToDefaultId(node.num),
            icon = Icons.Default.Numbers,
            modifier = Modifier.weight(1f),
        )
        InfoItem(
            label = stringResource(Res.string.node_number),
            value = node.num.toUInt().toString(),
            icon = Icons.Default.Numbers,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HearsAndHopsRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
            label = stringResource(Res.string.node_sort_last_heard),
            value = formatAgo(node.lastHeard),
            icon = Icons.Default.History,
            modifier = Modifier.weight(1f),
        )
        if (node.hopsAway >= 0) {
            InfoItem(
                label = stringResource(Res.string.hops_away),
                value = node.hopsAway.toString(),
                icon = Icons.Default.SignalCellularAlt,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun UserAndUptimeRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
            label = stringResource(Res.string.user_id),
            value = node.user.id,
            icon = Icons.Default.Person,
            modifier = Modifier.weight(1f),
        )
        if (node.deviceMetrics.uptimeSeconds > 0) {
            InfoItem(
                label = stringResource(Res.string.uptime),
                value = formatUptime(node.deviceMetrics.uptimeSeconds),
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SignalRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (node.snr != Float.MAX_VALUE) {
            InfoItem(
                label = stringResource(Res.string.snr),
                value = "%.1f dB".format(node.snr),
                icon = Icons.Default.SignalCellularAlt,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (node.rssi != Int.MAX_VALUE) {
            InfoItem(
                label = stringResource(Res.string.rssi),
                value = "%d dBm".format(node.rssi),
                icon = Icons.Default.SignalCellularAlt,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MqttAndVerificationRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (node.viaMqtt) {
            InfoItem(
                label = stringResource(Res.string.via_mqtt),
                value = "Yes",
                icon = Icons.Default.Cloud,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (node.manuallyVerified) {
            InfoItem(
                label = stringResource(Res.string.supported),
                value = "Verified",
                icon = Icons.Default.Verified,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}
