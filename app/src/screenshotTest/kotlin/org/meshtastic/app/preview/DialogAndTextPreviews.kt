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
package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_name
import org.meshtastic.core.resources.delivery_confirmed
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.resources.regenerate_keys_confirmation
import org.meshtastic.core.resources.regenerate_private_key
import org.meshtastic.core.resources.routing_error_timeout
import org.meshtastic.core.resources.warning
import org.meshtastic.core.ui.component.ClickableTextField
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.component.MeshtasticTextDialog
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.messaging.DeliveryInfo
import org.meshtastic.feature.settings.radio.component.ShutdownConfirmationDialog

@MultiPreview
@Composable
fun ClickableTextFieldPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ClickableTextField(
                    label = Res.string.channel_name,
                    enabled = true,
                    trailingIcon = MeshtasticIcons.Settings,
                    value = "LongFast",
                    onClick = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun MeshtasticResourceDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            MeshtasticResourceDialog(
                titleRes = Res.string.regenerate_private_key,
                messageRes = Res.string.regenerate_keys_confirmation,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun MeshtasticTextDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            MeshtasticTextDialog(
                titleRes = Res.string.warning,
                message = "This action cannot be undone. Proceed with caution.",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun DeliveryInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            DeliveryInfo(
                title = Res.string.message_delivery_status,
                resendOption = true,
                text = Res.string.delivery_confirmed,
                relayNodeName = "Router-Node",
                relays = 2,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun DeliveryInfoErrorPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            DeliveryInfo(
                title = Res.string.message_delivery_status,
                resendOption = true,
                text = Res.string.routing_error_timeout,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun ShutdownConfirmationDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            ShutdownConfirmationDialog(
                title = "Shutdown",
                node = null,
                onDismiss = {},
                isShutdown = true,
                onConfirm = {},
            )
        }
    }
}
