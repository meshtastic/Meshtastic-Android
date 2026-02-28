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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.delivery_confirmed
import org.meshtastic.core.resources.delivery_confirmed_reboot_warning
import org.meshtastic.core.resources.error
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.feature.settings.radio.ResponseState

private const val AUTO_DISMISS_DELAY_MS = 1500L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> PacketResponseStateDialog(state: ResponseState<T>, onDismiss: () -> Unit = {}, onComplete: () -> Unit = {}) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(state) {
        if (state is ResponseState.Success) {
            delay(AUTO_DISMISS_DELAY_MS)
            onDismiss()
            backDispatcher?.onBackPressed()
        }
    }

    MeshtasticDialog(
        onDismiss = if (state is ResponseState.Loading) onDismiss else null,
        title = null,
        icon = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (state) {
                    is ResponseState.Loading -> {
                        val progress by
                            animateFloatAsState(
                                targetValue = state.completed.toFloat() / state.total.toFloat(),
                                label = "progress",
                            )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%.0f%%".format(progress * 100),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            state.status?.let {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(top = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (state.completed >= state.total) onComplete()
                    }
                    is ResponseState.Success -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(84.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.delivery_confirmed),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(Res.string.delivery_confirmed_reboot_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is ResponseState.Error -> {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(84.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.error),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "${state.error.asString()}.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ResponseState.Empty -> {}
                }
            }
        },
        dismissable = false,
        onConfirm =
        if (state !is ResponseState.Loading) {
            {
                onDismiss()
                backDispatcher?.onBackPressed()
            }
        } else {
            null
        },
        confirmText = stringResource(Res.string.close),
        dismissText = if (state is ResponseState.Loading) stringResource(Res.string.cancel) else null,
    )
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogLoadingPreview() {
    PacketResponseStateDialog(state = ResponseState.Loading(total = 17, completed = 5))
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogSuccessPreview() {
    PacketResponseStateDialog(state = ResponseState.Success(Unit))
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogErrorPreview() {
    PacketResponseStateDialog(
        state = ResponseState.Error(org.meshtastic.core.resources.UiText.DynamicString("Failed to send packet")),
    )
}
