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
package com.geeksville.mesh.ui.contact

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.Contact
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.sample_message
import org.meshtastic.core.strings.some_username
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet

@Suppress("LongMethod")
@Composable
fun ContactItem(
    contact: Contact,
    selected: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onNodeChipClick: () -> Unit = {},
    channels: ChannelSet? = null,
) = with(contact) {
    val isOutlined = !selected && !isActive

    val colors =
        if (isOutlined) {
            CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
        } else {
            val containerColor = if (selected) Color.Gray else MaterialTheme.colorScheme.surfaceVariant
            CardDefaults.cardColors(containerColor = containerColor)
        }

    val border =
        if (isOutlined) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }

    Card(
        modifier =
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = shortName },
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        border = border,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            ContactHeader(contact = contact, channels = channels, onNodeChipClick = onNodeChipClick)

            ChatMetadata(modifier = Modifier.padding(top = 4.dp), contact = contact)
        }
    }
}

@Composable
private fun ContactHeader(
    contact: Contact,
    channels: ChannelSet?,
    modifier: Modifier = Modifier,
    onNodeChipClick: () -> Unit = {},
) {
    val colors =
        if (contact.nodeColors != null) {
            AssistChipDefaults.assistChipColors(
                labelColor = Color(contact.nodeColors.first),
                containerColor = Color(contact.nodeColors.second),
            )
        } else {
            AssistChipDefaults.assistChipColors()
        }

    Row(modifier = modifier.padding(0.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onNodeChipClick,
            modifier =
            Modifier.width(IntrinsicSize.Min).height(32.dp).semantics { contentDescription = contact.shortName },
            label = {
                Text(
                    text = contact.shortName,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            },
            colors = colors,
        )

        // Show unlock icon for broadcast with default PSK
        val isBroadcast = with(contact.contactKey) { getOrNull(1) == '^' || endsWith("^all") || endsWith("^broadcast") }

        if (isBroadcast && channels != null) {
            val channelIndex = contact.contactKey[0].digitToIntOrNull()
            channelIndex?.let { index -> SecurityIcon(channels, index) }
        }

        Text(
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            text = contact.longName,
        )
        Text(
            text = contact.lastMessageTime.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier,
        )
    }
}

private const val UNREAD_MESSAGE_LIMIT = 99

@Composable
private fun ChatMetadata(contact: Contact, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = contact.lastMessageText.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
        AnimatedVisibility(visible = contact.isMuted) {
            Icon(
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                imageVector = Icons.AutoMirrored.TwoTone.VolumeOff,
                contentDescription = null,
            )
        }
        AnimatedVisibility(modifier = Modifier.padding(start = 4.dp), visible = contact.unreadCount > 0) {
            val text =
                if (contact.unreadCount > UNREAD_MESSAGE_LIMIT) {
                    "$UNREAD_MESSAGE_LIMIT+"
                } else {
                    contact.unreadCount.toString()
                }

            Text(
                text = text,
                modifier =
                Modifier.background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                    .defaultMinSize(minWidth = 20.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ContactItemPreview() {
    val sampleContact =
        Contact(
            contactKey = "0^all",
            shortName = stringResource(Res.string.some_username),
            longName = stringResource(Res.string.unknown_username),
            lastMessageTime = "Mon",
            lastMessageText = stringResource(Res.string.sample_message),
            unreadCount = 2,
            messageCount = 10,
            isMuted = true,
            isUnmessageable = false,
        )

    val contactsList =
        listOf(
            sampleContact,
            sampleContact.copy(
                shortName = "0",
                longName = "A very long contact name that should be truncated.",
                lastMessageTime = "15 minutes ago",
            ),
        )

    AppTheme { Column { contactsList.forEach { contact -> ContactItem(contact = contact, selected = false) } } }
}
