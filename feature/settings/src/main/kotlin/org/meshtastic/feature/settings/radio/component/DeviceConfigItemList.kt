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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults.MediumContainerHeight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.timezone.toPosixString
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ConfigProtos.Config.DeviceConfig
import org.meshtastic.proto.config
import org.meshtastic.proto.copy
import java.time.ZoneId
import org.meshtastic.core.strings.R as Res

private val DeviceConfig.Role.description: Int
    get() =
        when (this) {
            DeviceConfig.Role.CLIENT -> Res.string.role_client_desc
            DeviceConfig.Role.CLIENT_MUTE -> Res.string.role_client_mute_desc
            DeviceConfig.Role.ROUTER -> Res.string.role_router_desc
            DeviceConfig.Role.ROUTER_CLIENT -> Res.string.role_router_client_desc
            DeviceConfig.Role.REPEATER -> Res.string.role_repeater_desc
            DeviceConfig.Role.TRACKER -> Res.string.role_tracker_desc
            DeviceConfig.Role.SENSOR -> Res.string.role_sensor_desc
            DeviceConfig.Role.TAK -> Res.string.role_tak_desc
            DeviceConfig.Role.CLIENT_HIDDEN -> Res.string.role_client_hidden_desc
            DeviceConfig.Role.LOST_AND_FOUND -> Res.string.role_lost_and_found_desc
            DeviceConfig.Role.TAK_TRACKER -> Res.string.role_tak_tracker_desc
            DeviceConfig.Role.ROUTER_LATE -> Res.string.role_router_late_desc
            else -> Res.string.unrecognized
        }

private val DeviceConfig.RebroadcastMode.description: Int
    get() =
        when (this) {
            DeviceConfig.RebroadcastMode.ALL -> Res.string.rebroadcast_mode_all_desc
            DeviceConfig.RebroadcastMode.ALL_SKIP_DECODING -> Res.string.rebroadcast_mode_all_skip_decoding_desc
            DeviceConfig.RebroadcastMode.LOCAL_ONLY -> Res.string.rebroadcast_mode_local_only_desc
            DeviceConfig.RebroadcastMode.KNOWN_ONLY -> Res.string.rebroadcast_mode_known_only_desc
            DeviceConfig.RebroadcastMode.NONE -> Res.string.rebroadcast_mode_none_desc
            DeviceConfig.RebroadcastMode.CORE_PORTNUMS_ONLY -> Res.string.rebroadcast_mode_core_portnums_only_desc
            else -> Res.string.unrecognized
        }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val deviceConfig = state.radioConfig.device
    val formState = rememberConfigState(initialValue = deviceConfig)
    var selectedRole by rememberSaveable { mutableStateOf(formState.value.role) }
    val infrastructureRoles = listOf(DeviceConfig.Role.ROUTER, DeviceConfig.Role.REPEATER)
    if (selectedRole != formState.value.role) {
        if (selectedRole in infrastructureRoles) {
            RouterRoleConfirmationDialog(
                onDismiss = { selectedRole = formState.value.role },
                onConfirm = { formState.value = formState.value.copy { role = selectedRole } },
            )
        } else {
            formState.value = formState.value.copy { role = selectedRole }
        }
    }
    val focusManager = LocalFocusManager.current
    RadioConfigScreenList(
        title = stringResource(Res.string.device),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { device = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.options)) {
                DropDownPreference(
                    title = stringResource(Res.string.role),
                    enabled = state.connected,
                    selectedItem = formState.value.role,
                    onItemSelected = { selectedRole = it },
                    summary = stringResource(formState.value.role.description),
                )

                HorizontalDivider()

                DropDownPreference(
                    title = stringResource(Res.string.rebroadcast_mode),
                    enabled = state.connected,
                    selectedItem = formState.value.rebroadcastMode,
                    onItemSelected = { formState.value = formState.value.copy { rebroadcastMode = it } },
                    summary = stringResource(formState.value.rebroadcastMode.description),
                )

                HorizontalDivider()

                val nodeInfoBroadcastIntervals = remember { IntervalConfiguration.NODE_INFO_BROADCAST.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.nodeinfo_broadcast_interval),
                    selectedItem = formState.value.nodeInfoBroadcastSecs.toLong(),
                    enabled = state.connected,
                    items = nodeInfoBroadcastIntervals.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { nodeInfoBroadcastSecs = it.toInt() } },
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.hardware)) {
                SwitchPreference(
                    title = stringResource(Res.string.double_tap_as_button_press),
                    summary = stringResource(Res.string.config_device_doubleTapAsButtonPress_summary),
                    checked = formState.value.doubleTapAsButtonPress,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { doubleTapAsButtonPress = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )

                InsetDivider()

                SwitchPreference(
                    title = stringResource(Res.string.triple_click_adhoc_ping),
                    summary = stringResource(Res.string.config_device_tripleClickAsAdHocPing_summary),
                    checked = !formState.value.disableTripleClick,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { disableTripleClick = !it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )

                InsetDivider()

                SwitchPreference(
                    title = stringResource(Res.string.led_heartbeat),
                    summary = stringResource(Res.string.config_device_ledHeartbeatEnabled_summary),
                    checked = !formState.value.ledHeartbeatDisabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { ledHeartbeatDisabled = !it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.time_zone)) {
                val context = LocalContext.current
                val appTzPosixString by
                    produceState(initialValue = ZoneId.systemDefault().toPosixString()) {
                        val receiver =
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                                        value = ZoneId.systemDefault().toPosixString()
                                    }
                                }
                            }
                        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
                        awaitDispose { context.unregisterReceiver(receiver) }
                    }

                EditTextPreference(
                    title = "",
                    value = formState.value.tzdef,
                    summary = stringResource(Res.string.config_device_tzdef_summary),
                    maxSize = 64, // tzdef max_size:65
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { tzdef = it } },
                    trailingIcon = {
                        IconButton(onClick = { formState.value = formState.value.copy { tzdef = "" } }) {
                            Icon(imageVector = Icons.Rounded.Clear, contentDescription = null)
                        }
                    },
                )

                HorizontalDivider()

                TextButton(
                    modifier = Modifier.height(MediumContainerHeight).fillMaxWidth(),
                    enabled = state.connected,
                    shape = RectangleShape,
                    onClick = { formState.value = formState.value.copy { tzdef = appTzPosixString } },
                ) {
                    Icon(imageVector = Icons.Rounded.PhoneAndroid, contentDescription = null)

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(text = stringResource(Res.string.config_device_use_phone_tz))
                }
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.gpio)) {
                EditTextPreference(
                    title = stringResource(Res.string.button_gpio),
                    value = formState.value.buttonGpio,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { buttonGpio = it } },
                )

                HorizontalDivider()

                EditTextPreference(
                    title = stringResource(Res.string.buzzer_gpio),
                    value = formState.value.buzzerGpio,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { buzzerGpio = it } },
                )
            }
        }
    }
}

@Composable
fun RouterRoleConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val dialogTitle = stringResource(Res.string.are_you_sure)
    val annotatedDialogText =
        AnnotatedString.fromHtml(
            htmlString = stringResource(Res.string.router_role_confirmation_text),
            linkStyles = TextLinkStyles(style = SpanStyle(color = Color.Blue)),
        )

    var confirmed by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        title = { Text(text = dialogTitle) },
        text = {
            Column {
                Text(text = annotatedDialogText)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(true) { confirmed = !confirmed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(stringResource(Res.string.i_know_what_i_m_doing))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmed) { Text(stringResource(Res.string.accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
