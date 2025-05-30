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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.DeviceConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel

private val DeviceConfig.Role.stringRes: Int
    get() = when (this) {
        DeviceConfig.Role.CLIENT -> R.string.role_client
        DeviceConfig.Role.CLIENT_MUTE -> R.string.role_client_mute
        DeviceConfig.Role.ROUTER -> R.string.role_router
        DeviceConfig.Role.ROUTER_CLIENT -> R.string.role_router_client
        DeviceConfig.Role.REPEATER -> R.string.role_repeater
        DeviceConfig.Role.TRACKER -> R.string.role_tracker
        DeviceConfig.Role.SENSOR -> R.string.role_sensor
        DeviceConfig.Role.TAK -> R.string.role_tak
        DeviceConfig.Role.CLIENT_HIDDEN -> R.string.role_client_hidden
        DeviceConfig.Role.LOST_AND_FOUND -> R.string.role_lost_and_found
        DeviceConfig.Role.TAK_TRACKER -> R.string.role_tak_tracker
        DeviceConfig.Role.ROUTER_LATE -> R.string.role_router_late
        else -> R.string.unrecognized
    }

private val DeviceConfig.RebroadcastMode.stringRes: Int
    get() = when (this) {
        DeviceConfig.RebroadcastMode.ALL -> R.string.rebroadcast_mode_all
        DeviceConfig.RebroadcastMode.ALL_SKIP_DECODING -> R.string.rebroadcast_mode_all_skip_decoding
        DeviceConfig.RebroadcastMode.LOCAL_ONLY -> R.string.rebroadcast_mode_local_only
        DeviceConfig.RebroadcastMode.KNOWN_ONLY -> R.string.rebroadcast_mode_known_only
        DeviceConfig.RebroadcastMode.NONE -> R.string.rebroadcast_mode_none
        DeviceConfig.RebroadcastMode.CORE_PORTNUMS_ONLY -> R.string.rebroadcast_mode_core_portnums_only
        else -> R.string.unrecognized
    }

@Composable
fun DeviceConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    DeviceConfigItemList(
        deviceConfig = state.radioConfig.device,
        enabled = state.connected,
        onSaveClicked = { deviceInput ->
            val config = config { device = deviceInput }
            viewModel.setConfig(config)
        }
    )
}

@Suppress("LongMethod")
@Composable
fun RouterRoleConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogTitle = stringResource(R.string.are_you_sure)
    val annotatedDialogText = AnnotatedString.fromHtml(
        htmlString = stringResource(R.string.router_role_confirmation_text),
        linkStyles = TextLinkStyles(style = SpanStyle(color = Color.Blue))
    )

    var confirmed by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Column {
                Text(text = annotatedDialogText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(true) {
                            confirmed = !confirmed
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = { confirmed = it }
                    )
                    Text(stringResource(R.string.i_know_what_i_m_doing))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed
            ) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeviceConfigItemList(
    deviceConfig: DeviceConfig,
    enabled: Boolean,
    onSaveClicked: (DeviceConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var deviceInput by rememberSaveable { mutableStateOf(deviceConfig) }
    var selectedRole by rememberSaveable { mutableStateOf(deviceInput.role) }
    val infrastructureRoles = listOf(
        DeviceConfig.Role.ROUTER,
        DeviceConfig.Role.REPEATER,
    )
    if (selectedRole != deviceInput.role) {
        if (selectedRole in infrastructureRoles) {
            RouterRoleConfirmationDialog(
                onDismiss = { selectedRole = deviceInput.role },
                onConfirm = {
                    deviceInput = deviceInput.copy { role = selectedRole }
                }
            )
        } else {
            deviceInput = deviceInput.copy { role = selectedRole }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.device_config)) }

        item {
            DropDownPreference(
                title = stringResource(R.string.role),
                enabled = enabled,
                selectedItem = deviceInput.role,
                onItemSelected = {
                    selectedRole = it
                },
                summary = stringResource(id = deviceInput.role.stringRes),
            )
            HorizontalDivider()
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.redefine_pin_button),
                value = deviceInput.buttonGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buttonGpio = it }
                }
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.redefine_pin_buzzer),
                value = deviceInput.buzzerGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buzzerGpio = it }
                }
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.rebroadcast_mode),
                enabled = enabled,
                selectedItem = deviceInput.rebroadcastMode,
                onItemSelected = { deviceInput = deviceInput.copy { rebroadcastMode = it } },
                summary = stringResource(id = deviceInput.rebroadcastMode.stringRes),
            )
            HorizontalDivider()
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.nodeinfo_broadcast_interval_seconds),
                value = deviceInput.nodeInfoBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { nodeInfoBroadcastSecs = it }
                }
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.double_tap_as_button_press),
                summary = stringResource(id = R.string.config_device_doubleTapAsButtonPress_summary),
                checked = deviceInput.doubleTapAsButtonPress,
                enabled = enabled,
                onCheckedChange = { deviceInput = deviceInput.copy { doubleTapAsButtonPress = it } }
            )
            HorizontalDivider()
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.disable_triple_click),
                summary = stringResource(id = R.string.config_device_disableTripleClick_summary),
                checked = deviceInput.disableTripleClick,
                enabled = enabled,
                onCheckedChange = { deviceInput = deviceInput.copy { disableTripleClick = it } }
            )
            HorizontalDivider()
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.posix_timezone),
                value = deviceInput.tzdef,
                maxSize = 64, // tzdef max_size:65
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { tzdef = it }
                },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.disable_led_heartbeat),
                summary = stringResource(id = R.string.config_device_ledHeartbeatDisabled_summary),
                checked = deviceInput.ledHeartbeatDisabled,
                enabled = enabled,
                onCheckedChange = { deviceInput = deviceInput.copy { ledHeartbeatDisabled = it } }
            )
            HorizontalDivider()
        }

        item {
            PreferenceFooter(
                enabled = enabled && deviceInput != deviceConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    deviceInput = deviceConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(deviceInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceConfigPreview() {
    DeviceConfigItemList(
        deviceConfig = DeviceConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
