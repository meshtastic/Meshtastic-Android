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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.squareup.wire.Message
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.discard_changes
import org.meshtastic.core.strings.save_changes
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.feature.settings.radio.ResponseState

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
    additionalDirtyCheck: () -> Boolean = { false },
    onDiscard: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current

    if (responseState.isWaiting()) {
        PacketResponseStateDialog(state = responseState, onDismiss = onDismissPacketResponse)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = title,
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        val showFooterButtons = configState.isDirty || additionalDirtyCheck()

        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()

            item {
                AnimatedVisibility(
                    visible = showFooterButtons,
                    enter = fadeIn() + expandIn(),
                    exit = fadeOut() + shrinkOut(),
                ) {
                    PreferenceFooter(
                        enabled = enabled && showFooterButtons,
                        negativeText = stringResource(Res.string.discard_changes),
                        onNegativeClicked = {
                            focusManager.clearFocus()
                            configState.reset()
                            onDiscard()
                        },
                        positiveText = stringResource(Res.string.save_changes),
                        onPositiveClicked = {
                            focusManager.clearFocus()
                            onSave(configState.value)
                        },
                    )
                }
            }
        }
    }
}
