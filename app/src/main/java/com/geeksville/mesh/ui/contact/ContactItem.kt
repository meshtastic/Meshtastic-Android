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

package com.geeksville.mesh.ui.contact

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.Contact
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.AppOnlyProtos

@Suppress("LongMethod")
@Composable
fun ContactItem(
    contact: Contact,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onNodeChipClick: () -> Unit = {},
    channels: AppOnlyProtos.ChannelSet? = null,
) = with(contact) {
    Card(
        modifier =
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(color = if (selected) Color.Gray else MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = shortName },
        shape = RoundedCornerShape(12.dp),
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

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = onNodeChipClick,
                modifier = Modifier.padding(end = 8.dp).width(72.dp).semantics { contentDescription = shortName },
                label = {
                    Text(
                        text = shortName,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                },
                colors = colors,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = longName, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Show unlock icon for broadcast with default PSK
                        val isBroadcast =
                            contact.contactKey.getOrNull(1) == '^' ||
                                contact.contactKey.endsWith("^all") ||
                                contact.contactKey.endsWith("^broadcast")
                        if (isBroadcast && channels != null) {
                            val channelIndex = contact.contactKey[0].digitToIntOrNull()
                            channelIndex?.let { index -> SecurityIcon(channels, index) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = lastMessageTime.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lastMessageText.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                    )
                    AnimatedVisibility(visible = isMuted) {
                        Icon(imageVector = Icons.AutoMirrored.TwoTone.VolumeOff, contentDescription = null)
                    }
                    AnimatedVisibility(visible = unreadCount > 0) {
                        Text(
                            text = unreadCount.toString(),
                            modifier =
                            Modifier.background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ContactItemPreview() {
    AppTheme {
        ContactItem(
            contact =
            Contact(
                contactKey = "0^all",
                shortName = stringResource(R.string.some_username),
                longName = stringResource(R.string.unknown_username),
                lastMessageTime = "3 minutes ago",
                lastMessageText = stringResource(R.string.sample_message),
                unreadCount = 2,
                messageCount = 10,
                isMuted = true,
                isUnmessageable = false,
            ),
            selected = false,
        )
    }
}
