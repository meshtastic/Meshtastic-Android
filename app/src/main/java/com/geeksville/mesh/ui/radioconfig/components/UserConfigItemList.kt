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

package com.geeksville.mesh.ui.radioconfig.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.deviceMetadata
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.isUnmessageableRole
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.RegularPreference
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
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
        metadata = state.metadata,
    )
}

@Suppress("LongMethod")
@Composable
fun UserConfigItemList(
    metadata: MeshProtos.DeviceMetadata?,
    userConfig: MeshProtos.User,
    enabled: Boolean,
    onSaveClicked: (MeshProtos.User) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var userInput by rememberSaveable { mutableStateOf(userConfig) }
    val firmwareVersion = DeviceVersion(metadata?.firmwareVersion ?: "")

    val validLongName = userInput.longName.isNotBlank()
    val validShortName = userInput.shortName.isNotBlank()
    val validNames = validLongName && validShortName
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.user_config)) }

        item {
            RegularPreference(
                title = stringResource(R.string.node_id),
                subtitle = userInput.id,
                onClick = {}
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.long_name),
                value = userInput.longName,
                maxSize = 39, // long_name max_size:40
                enabled = enabled,
                isError = !validLongName,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    userInput = userInput.copy { longName = it }
                }
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.short_name),
                value = userInput.shortName,
                maxSize = 4, // short_name max_size:5
                enabled = enabled,
                isError = !validShortName,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { userInput = userInput.copy { shortName = it } }
            )
        }

        item {
            RegularPreference(
                title = stringResource(R.string.hardware_model),
                subtitle = userInput.hwModel.name,
                onClick = {}
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.unmessageable),
                summary = stringResource(R.string.unmonitored_or_infrastructure),
                checked = userInput.isUnmessagable || (
                        firmwareVersion < DeviceVersion("2.6.9") &&
                                userInput.role.isUnmessageableRole()
                        ),
                enabled = enabled && firmwareVersion >= DeviceVersion("2.6.9"),
                onCheckedChange = { userInput = userInput.copy { isUnmessagable = it } }
            )
        }

        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.licensed_amateur_radio),
                summary = stringResource(R.string.licensed_amateur_radio_text),
                checked = userInput.isLicensed,
                enabled = enabled,
                onCheckedChange = { userInput = userInput.copy { isLicensed = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && userInput != userConfig && validNames,
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
        metadata = deviceMetadata {
            firmwareVersion = "2.8.0"
        }
    )
}
