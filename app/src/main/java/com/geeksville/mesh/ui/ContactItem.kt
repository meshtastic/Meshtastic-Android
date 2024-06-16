package com.geeksville.mesh.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.ui.theme.AppTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    modifier: Modifier = Modifier,
) = with(contact) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    onClick = { },
                    modifier = Modifier
                        .width(72.dp)
                        .padding(end = 8.dp),
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
        )
    }
}
