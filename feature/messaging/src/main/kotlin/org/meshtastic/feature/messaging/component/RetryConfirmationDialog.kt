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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.service.RetryEvent
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.retry_dialog_cancel
import org.meshtastic.core.strings.retry_dialog_confirm
import org.meshtastic.core.strings.retry_dialog_message
import org.meshtastic.core.strings.retry_dialog_reaction_message
import org.meshtastic.core.strings.retry_dialog_title
import org.meshtastic.core.ui.component.MeshtasticDialog

private const val COUNTDOWN_DELAY_MS = 1000L
private const val MESSAGE_PREVIEW_LENGTH = 50

@Composable
private fun RetryDialogContent(retryEvent: RetryEvent, timeRemaining: Int) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (retryEvent) {
            is RetryEvent.MessageRetry -> {
                // Show message preview
                if (retryEvent.text.isNotEmpty()) {
                    Text(
                        text =
                        "\"${retryEvent.text.take(MESSAGE_PREVIEW_LENGTH)}${
                            if (retryEvent.text.length > MESSAGE_PREVIEW_LENGTH) "…" else ""
                        }\"",
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                    stringResource(
                        Res.string.retry_dialog_message,
                        timeRemaining,
                        retryEvent.attemptNumber,
                        retryEvent.maxAttempts,
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is RetryEvent.ReactionRetry -> {
                // Show emoji preview
                Text(
                    text = retryEvent.emoji,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text =
                    stringResource(
                        Res.string.retry_dialog_reaction_message,
                        timeRemaining,
                        retryEvent.attemptNumber,
                        retryEvent.maxAttempts,
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun RetryConfirmationDialog(
    retryEvent: RetryEvent,
    countdownSeconds: Int = 5,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onTimeout: () -> Unit,
) {
    var timeRemaining by remember { mutableIntStateOf(countdownSeconds) }

    LaunchedEffect(retryEvent.packetId) {
        timeRemaining = countdownSeconds // Reset countdown for new event
        while (timeRemaining > 0) {
            delay(COUNTDOWN_DELAY_MS)
            timeRemaining--
        }
        // Countdown reached 0, auto-retry
        onTimeout()
    }

    MeshtasticDialog(
        onDismiss = onCancel,
        dismissText = stringResource(Res.string.retry_dialog_cancel),
        confirmText = stringResource(Res.string.retry_dialog_confirm),
        onConfirm = onConfirm,
        title = stringResource(Res.string.retry_dialog_title),
        text = { RetryDialogContent(retryEvent, timeRemaining) },
        dismissable = false,
    )
}
