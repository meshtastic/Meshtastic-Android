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
package org.meshtastic.core.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.AppTheme

/** A helper component that renders an [AlertManager.AlertData] using the same logic as MainScreen. */
@Composable
fun AlertPreviewRenderer(data: AlertManager.AlertData) {
    MeshtasticDialog(
        title = data.title,
        titleRes = data.titleRes,
        message = data.message,
        messageRes = data.messageRes,
        html = data.html,
        icon = data.icon,
        text = data.composableMessage?.let { msg -> { msg.Content() } },
        confirmText = data.confirmText,
        confirmTextRes = data.confirmTextRes,
        onConfirm = data.onConfirm,
        dismissText = data.dismissText,
        dismissTextRes = data.dismissTextRes,
        onDismiss = data.onDismiss,
        choices = data.choices,
        dismissable = data.dismissable,
    )
}

@Preview(showBackground = true, name = "Simple Text Alert")
@Composable
fun PreviewTextAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AlertPreviewRenderer(
                AlertManager.AlertData(
                    title = "Firmware Update",
                    message = "A new version is available. Would you like to update now?",
                ),
            )
        }
    }
}

@Preview(showBackground = true, name = "Icon and Text Alert")
@Composable
fun PreviewIconAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AlertPreviewRenderer(
                AlertManager.AlertData(
                    title = "Warning",
                    message = "This action cannot be undone.",
                    icon = Icons.Rounded.Warning,
                ),
            )
        }
    }
}

@Preview(showBackground = true, name = "HTML Alert")
@Composable
fun PreviewHtmlAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AlertPreviewRenderer(
                AlertManager.AlertData(title = "Release Notes", html = "Enhanced range and better battery life"),
            )
        }
    }
}

@Preview(showBackground = true, name = "Multiple Choice Alert")
@Composable
fun PreviewMultipleChoiceAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AlertPreviewRenderer(
                AlertManager.AlertData(
                    title = "Select Channel",
                    message = "Pick a channel to join:",
                    choices = mapOf("Public" to {}, "Private" to {}, "Emergency" to {}),
                ),
            )
        }
    }
}

@Preview(showBackground = true, name = "Composable Content Alert")
@Composable
fun PreviewComposableAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AlertPreviewRenderer(
                AlertManager.AlertData(
                    title = "Custom Content",
                    composableMessage = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("This is a custom composable")
                            Text("With multiple lines and styles")
                        }
                    },
                ),
            )
        }
    }
}
