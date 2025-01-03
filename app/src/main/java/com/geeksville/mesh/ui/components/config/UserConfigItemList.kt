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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.model.getInitials
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.RegularPreference
import com.geeksville.mesh.ui.components.SwitchPreference
import com.geeksville.mesh.user

@Composable
fun UserConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    UserConfigItemList(
        userConfig = state.userConfig,
        enabled = true,
        onSaveClicked = viewModel::setOwner,
    )
}

@Composable
fun UserConfigItemList(
    userConfig: MeshProtos.User,
    enabled: Boolean,
    onSaveClicked: (MeshProtos.User) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var userInput by rememberSaveable { mutableStateOf(userConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "User Config") }

        item {
            RegularPreference(title = "Node ID",
                subtitle = userInput.id,
                onClick = {})
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Long name",
                value = userInput.longName,
                maxSize = 39, // long_name max_size:40
                enabled = enabled,
                isError = userInput.longName.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    userInput = userInput.copy { longName = it }
                    if (getInitials(it).toByteArray().size <= 4) { // short_name max_size:5
                        userInput = userInput.copy { shortName = getInitials(it) }
                    }
                })
        }

        item {
            EditTextPreference(title = "Short name",
                value = userInput.shortName,
                maxSize = 4, // short_name max_size:5
                enabled = enabled,
                isError = userInput.shortName.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { userInput = userInput.copy { shortName = it } })
        }

        item {
            RegularPreference(title = "Hardware model",
                subtitle = userInput.hwModel.name,
                onClick = {})
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Licensed amateur radio",
                checked = userInput.isLicensed,
                enabled = enabled,
                onCheckedChange = { userInput = userInput.copy { isLicensed = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = enabled && userInput != userConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    userInput = userConfig
                }, onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(userInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserConfigPreview() {
    UserConfigItemList(
        userConfig = user {
            id = "!a280d9c8"
            longName = "Meshtastic d9c8"
            shortName = "d9c8"
            hwModel = MeshProtos.HardwareModel.RAK4631
            isLicensed = false
        },
        enabled = true,
        onSaveClicked = { },
    )
}
