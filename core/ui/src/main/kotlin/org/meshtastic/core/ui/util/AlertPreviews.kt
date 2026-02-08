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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.okay
import org.meshtastic.core.ui.component.MultipleChoiceAlertDialog
import org.meshtastic.core.ui.theme.AppTheme

/** A helper component that renders an [AlertManager.AlertData] using the same logic as MainScreen. */
@Composable
@Suppress("CyclomaticComplexMethod")
fun AlertPreviewRenderer(data: AlertManager.AlertData) {
    val title = data.title ?: data.titleRes?.let { stringResource(it) } ?: ""
    val message = data.message ?: data.messageRes?.let { stringResource(it) }
    val confirmText = data.confirmText ?: data.confirmTextRes?.let { stringResource(it) }
    val dismissText = data.dismissText ?: data.dismissTextRes?.let { stringResource(it) }

    if (data.choices.isNotEmpty()) {
        MultipleChoiceAlertDialog(title = title, message = message, choices = data.choices, onDismissRequest = {})
    } else {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = title) },
            text = {
                val composableMsg = data.composableMessage
                val htmlMsg = data.html
                if (composableMsg != null) {
                    composableMsg.Content()
                } else if (htmlMsg != null) {
                    Text(text = htmlMsg)
                } else {
                    Text(text = message.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = {}) { Text(text = confirmText ?: stringResource(Res.string.okay)) }
            },
            dismissButton = {
                TextButton(onClick = {}) { Text(text = dismissText ?: stringResource(Res.string.cancel)) }
            },
        )
    }
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
