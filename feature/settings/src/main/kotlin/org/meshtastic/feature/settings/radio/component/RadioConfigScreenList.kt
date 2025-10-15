/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.protobuf.MessageLite
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.feature.settings.radio.ResponseState

@Composable
fun <T : MessageLite> RadioConfigScreenList(
    title: String,
    onBack: () -> Unit,
    responseState: ResponseState<Any>,
    onDismissPacketResponse: () -> Unit,
    configState: ConfigState<T>,
    enabled: Boolean,
    onSave: (T) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current

    if (responseState.isWaiting()) {
        PacketResponseStateDialog(state = responseState, onDismiss = onDismissPacketResponse)
    }

    Scaffold(
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
        val showFooterButtons = configState.isDirty

        Box(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                content()

                item {
                    AnimatedVisibility(
                        visible = showFooterButtons,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = expandIn(),
                        exit = shrinkOut(),
                    ) {
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = showFooterButtons,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                PreferenceFooter(
                    enabled = enabled && configState.isDirty,
                    negativeText = stringResource(R.string.discard_changes),
                    onNegativeClicked = {
                        focusManager.clearFocus()
                        configState.reset()
                    },
                    positiveText = stringResource(R.string.save_changes),
                    onPositiveClicked = {
                        focusManager.clearFocus()
                        onSave(configState.value)
                    },
                )
            }
        }
    }
}
