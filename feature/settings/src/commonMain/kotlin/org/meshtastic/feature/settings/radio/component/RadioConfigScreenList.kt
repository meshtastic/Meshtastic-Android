/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.squareup.wire.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connect_radio_to_change_settings
import org.meshtastic.core.resources.discard_changes
import org.meshtastic.core.resources.save_and_restart
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.search.FieldFocusRequest
import org.meshtastic.feature.settings.search.LocalSettingsFieldFocus

/** The local radio's settings open read-only while disconnected; remote-admin screens never show the prompt. */
internal val RadioConfigState.connectPromptVisible: Boolean
    get() = !connected && isLocal

@Suppress("LongMethod")
@Composable
fun <T : Message<T, *>> RadioConfigScreenList(
    title: String,
    onBack: () -> Unit,
    responseState: ResponseState<Any>,
    onDismissPacketResponse: () -> Unit,
    configState: ConfigState<T>,
    enabled: Boolean,
    onSave: (T) -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = enabled,
    showConnectPrompt: Boolean = false,
    rebootBehavior: RebootBehavior = RebootBehavior.MAY_RESTART,
    actions: @Composable () -> Unit = {},
    additionalDirtyCheck: () -> Boolean = { false },
    onDiscard: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier) {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = title,
                    canNavigateUp = true,
                    onNavigateUp = onBack,
                    ourNode = null,
                    showNodeChip = false,
                    actions = actions,
                    onClickChip = {},
                )
            },
        ) { innerPadding ->
            val showFooterButtons = configState.isDirty || additionalDirtyCheck()

            val listState = rememberLazyListState()
            val fieldFocus = LocalSettingsFieldFocus.current
            val requestedField = fieldFocus?.pending?.collectAsState()?.value
            val keyIndex = remember { FieldKeyIndex() }
            var highlightKey by remember { mutableStateOf<String?>(null) }
            var highlightActive by remember { mutableStateOf(false) }

            // A request for a key no item carries stays pending until the screen closes, and the screen just opens.
            LaunchedEffect(requestedField, fieldFocus) {
                val request = requestedField ?: return@LaunchedEffect
                val key = request.key
                val target =
                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                        .map { keyIndex.indexOf(key) }
                        .first { it != null } ?: return@LaunchedEffect
                listState.animateScrollToItem(target)
                fieldFocus.consume(request)
                highlightKey = key
                highlightActive = true
                delay(HIGHLIGHT_HOLD_MILLIS)
                highlightActive = false
            }
            // Leaving clears only the request this screen saw, never a newer one made for the next destination.
            val seenRequest = remember { mutableStateOf<FieldFocusRequest?>(null) }
            SideEffect { if (requestedField != null) seenRequest.value = requestedField }
            DisposableEffect(fieldFocus) { onDispose { fieldFocus?.clear(seenRequest.value) } }

            LazyColumn(
                state = listState,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                keyIndex.reset()
                val recording = FieldKeyRecordingScope(this, keyIndex, highlightKey, highlightActive)
                if (showConnectPrompt) {
                    recording.item {
                        Text(
                            text = stringResource(Res.string.connect_radio_to_change_settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                recording.content()

                item {
                    AnimatedVisibility(
                        modifier = Modifier.fillMaxWidth(),
                        visible = showFooterButtons,
                        enter = fadeIn() + expandIn(),
                        exit = fadeOut() + shrinkOut(),
                    ) {
                        PreferenceFooter(
                            enabled = enabled && showFooterButtons,
                            positiveEnabled = saveEnabled,
                            negativeText = stringResource(Res.string.discard_changes),
                            onNegativeClicked = {
                                focusManager.clearFocus()
                                configState.reset()
                                onDiscard()
                            },
                            positiveText =
                            if (rebootBehavior == RebootBehavior.ALWAYS) {
                                stringResource(Res.string.save_and_restart)
                            } else {
                                stringResource(Res.string.save_changes)
                            },
                            onPositiveClicked = {
                                focusManager.clearFocus()
                                if (saveEnabled) onSave(configState.value)
                            },
                        )
                    }
                }
            }
        }

        LoadingOverlay(state = responseState)

        if (responseState is ResponseState.Success || responseState is ResponseState.Error) {
            PacketResponseStateDialog(
                state = responseState,
                onDismiss = onDismissPacketResponse,
                rebootBehavior = rebootBehavior,
            )
        }
    }
}
