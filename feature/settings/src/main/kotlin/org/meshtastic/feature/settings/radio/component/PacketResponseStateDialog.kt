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
package org.meshtastic.feature.settings.radio.component

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.delivery_confirmed
import org.meshtastic.core.strings.error
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.feature.settings.radio.ResponseState

private const val AUTO_DISMISS_DELAY_MS = 1500L

@Composable
fun <T> PacketResponseStateDialog(state: ResponseState<T>, onDismiss: () -> Unit = {}, onComplete: () -> Unit = {}) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(state) {
        if (state is ResponseState.Success) {
            delay(AUTO_DISMISS_DELAY_MS)
            onDismiss()
        }
    }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title = "", // Title is handled in the text block for more control
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state is ResponseState.Loading) {
                    val progress by
                        animateFloatAsState(
                            targetValue = state.completed.toFloat() / state.total.toFloat(),
                            label = "progress",
                        )
                    Text("%.0f%%".format(progress * 100))
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    state.status?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.completed >= state.total) onComplete()
                }
                if (state is ResponseState.Success) {
                    Text(text = stringResource(Res.string.delivery_confirmed))
                }
                if (state is ResponseState.Error) {
                    Text(text = stringResource(Res.string.error), minLines = 2)
                    Text(text = state.error.asString())
                }
            }
        },
        dismissable = false,
        onConfirm = {
            onDismiss()
            if (state is ResponseState.Success || state is ResponseState.Error) {
                backDispatcher?.onBackPressed()
            }
        },
        confirmText = stringResource(Res.string.close),
        dismissText = null, // Hide dismiss button, only show "Close" confirm button
    )
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogPreview() {
    PacketResponseStateDialog(state = ResponseState.Loading(total = 17, completed = 5))
}
