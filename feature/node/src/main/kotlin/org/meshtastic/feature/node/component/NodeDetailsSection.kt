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

import android.content.ClipData
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.copy
import org.meshtastic.core.strings.details
import org.meshtastic.core.strings.encryption_error
import org.meshtastic.core.strings.encryption_error_text
import org.meshtastic.core.strings.error
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
import org.meshtastic.core.ui.icon.ArrowCircleUp
import org.meshtastic.core.ui.icon.ChannelUtilization
import org.meshtastic.core.ui.icon.Cloud
import org.meshtastic.core.ui.icon.History
import org.meshtastic.core.ui.icon.Hops
import org.meshtastic.core.ui.icon.KeyOff
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.Role
import org.meshtastic.core.ui.icon.Verified
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
                    imageVector = MeshtasticIcons.KeyOff,
                    contentDescription = null,
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
        if (node.hopsAway == 0) {
            SectionDivider()
            SignalRow(node)
        }
        if (node.viaMqtt || node.manuallyVerified) {
            SectionDivider()
            MqttAndVerificationRow(node)
        }
        val publicKey = node.publicKey ?: node.user.public_key
        if (publicKey != null && publicKey.size > 0) {
            SectionDivider()
            PublicKeyItem(publicKey.toByteArray())
        }
    }
}

@Composable
private fun NameAndRoleRow(node: Node) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoItem(
            label = stringResource(Res.string.short_name),
            value = (node.user.short_name ?: "").ifEmpty { "???" },
            icon = MeshtasticIcons.Person,
            modifier = Modifier.weight(1f),
        )
        InfoItem(
            label = stringResource(Res.string.role),
            value = node.user.role?.name ?: "",
            icon = MeshtasticIcons.Role,
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
            icon = Icons.Rounded.Numbers,
            modifier = Modifier.weight(1f),
        )
        InfoItem(
            label = stringResource(Res.string.node_number),
            value = node.num.toUInt().toString(),
            icon = Icons.Rounded.Numbers,
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
            icon = MeshtasticIcons.History,
            modifier = Modifier.weight(1f),
        )
        if (node.hopsAway >= 0) {
            InfoItem(
                label = stringResource(Res.string.hops_away),
                value = node.hopsAway.toString(),
                icon = MeshtasticIcons.Hops,
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
            value = node.user.id ?: "",
            icon = MeshtasticIcons.Person,
            modifier = Modifier.weight(1f),
        )
        if ((node.deviceMetrics.uptime_seconds ?: 0) > 0) {
            InfoItem(
                label = stringResource(Res.string.uptime),
                value = formatUptime(node.deviceMetrics.uptime_seconds!!),
                icon = MeshtasticIcons.ArrowCircleUp,
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
                icon = MeshtasticIcons.ChannelUtilization,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (node.rssi != Int.MAX_VALUE) {
            InfoItem(
                label = stringResource(Res.string.rssi),
                value = "%d dBm".format(node.rssi),
                icon = MeshtasticIcons.ChannelUtilization,
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
                icon = MeshtasticIcons.Cloud,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (node.manuallyVerified) {
            InfoItem(
                label = stringResource(Res.string.supported),
                value = "Verified",
                icon = MeshtasticIcons.Verified,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "MagicNumber")
@Composable
private fun PublicKeyItem(publicKeyBytes: ByteArray) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val isMismatch = publicKeyBytes.all { it == 0.toByte() } && publicKeyBytes.size == 32
    val publicKeyBase64 =
        if (isMismatch) {
            stringResource(Res.string.error)
        } else {
            Base64.encodeToString(publicKeyBytes, Base64.DEFAULT).trim()
        }
    val label = stringResource(Res.string.public_key)
    val copyLabel = stringResource(Res.string.copy)

    Column(
        modifier =
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .combinedClickable(
                onLongClick = {
                    if (!isMismatch) {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, publicKeyBase64)))
                        }
                    }
                },
                onLongClickLabel = copyLabel,
                onClick = {},
                role = Role.Button,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $publicKeyBase64" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MeshtasticIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint =
                if (isMismatch) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = publicKeyBase64,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
