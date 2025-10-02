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

package com.geeksville.mesh.ui.settings.radio.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ui.settings.radio.ResponseState
import com.google.protobuf.MessageLite
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PreferenceFooter

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
        Column(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(16.dp)) {
                content()
            }

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
