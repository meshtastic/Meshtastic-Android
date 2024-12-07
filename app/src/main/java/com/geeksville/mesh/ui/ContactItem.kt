/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Chip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.ui.theme.AppTheme

@Suppress("LongMethod")
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) = with(contact) {
    Card(
        modifier = Modifier
            .background(color = if (selected) Color.Gray else MaterialTheme.colors.background)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Surface(
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    onClick = { },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(72.dp),
                ) {
                    Text(
                        text = shortName,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MaterialTheme.typography.button.fontSize,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = longName,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = lastMessageTime.orEmpty(),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lastMessageText.orEmpty(),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                        AnimatedVisibility(visible = isMuted) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_twotone_volume_off_24),
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(visible = unreadCount > 0) {
                            Text(
                                text = unreadCount.toString(),
                                modifier = Modifier
                                    .background(MaterialTheme.colors.primary, shape = CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = MaterialTheme.colors.onPrimary,
                                style = MaterialTheme.typography.caption,
                            )
                        }
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
            contact = Contact(
                contactKey = "0^all",
                shortName = stringResource(R.string.some_username),
                longName = stringResource(R.string.unknown_username),
                lastMessageTime = "3 minutes ago",
                lastMessageText = stringResource(R.string.sample_message),
                unreadCount = 2,
                messageCount = 10,
                isMuted = true,
            ),
            selected = false,
        )
    }
}
