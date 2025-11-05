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

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.protobuf.ByteString
import org.meshtastic.core.model.util.encodeToString
import org.meshtastic.core.model.util.toByteString
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.EditBase64Preference
import org.meshtastic.core.ui.component.EditListPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ConfigProtos.Config.SecurityConfig
import org.meshtastic.proto.config
import org.meshtastic.proto.copy
import java.security.SecureRandom
import org.meshtastic.core.strings.R as Res

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurityConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val node by viewModel.destNode.collectAsStateWithLifecycle()
    val securityConfig = state.radioConfig.security
    val formState = rememberConfigState(initialValue = securityConfig)

    var publicKey by rememberSaveable { mutableStateOf(formState.value.publicKey) }
    LaunchedEffect(formState.value.privateKey) {
        if (formState.value.privateKey != securityConfig.privateKey) {
            publicKey = "".toByteString()
        } else if (formState.value.privateKey == securityConfig.privateKey) {
            publicKey = securityConfig.publicKey
        }
    }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.exportSecurityConfig(uri, securityConfig) }
            }
        }

    var showKeyGenerationDialog by rememberSaveable { mutableStateOf(false) }
    PrivateKeyRegenerateDialog(
        showKeyGenerationDialog = showKeyGenerationDialog,
        onConfirm = {
            formState.value = it
            showKeyGenerationDialog = false
            val config = config { security = formState.value }
            viewModel.setConfig(config)
        },
        onDismiss = { showKeyGenerationDialog = false },
    )
    var showEditSecurityConfigDialog by rememberSaveable { mutableStateOf(false) }
    if (showEditSecurityConfigDialog) {
        AlertDialog(
            title = { Text(text = stringResource(Res.string.export_keys)) },
            text = { Text(text = stringResource(Res.string.export_keys_confirmation)) },
            onDismissRequest = { showEditSecurityConfigDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditSecurityConfigDialog = false
                        val intent =
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/*"
                                putExtra(
                                    Intent.EXTRA_TITLE,
                                    "${node?.user?.shortName}_keys_${System.currentTimeMillis()}.json",
                                )
                            }
                        exportConfigLauncher.launch(intent)
                    },
                ) {
                    Text(stringResource(Res.string.okay))
                }
            },
        )
    }

    val focusManager = LocalFocusManager.current
    RadioConfigScreenList(
        title = stringResource(Res.string.security),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { security = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.direct_message_key)) {
                EditBase64Preference(
                    title = stringResource(Res.string.public_key),
                    summary = stringResource(Res.string.config_security_public_key),
                    value = publicKey,
                    enabled = state.connected,
                    readOnly = true,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChange = {
                        if (it.size() == 32) {
                            formState.value = formState.value.copy { this.publicKey = it }
                        }
                    },
                    trailingIcon = { CopyIconButton(valueToCopy = formState.value.publicKey.encodeToString()) },
                )
                HorizontalDivider()
                EditBase64Preference(
                    title = stringResource(Res.string.private_key),
                    summary = stringResource(Res.string.config_security_private_key),
                    value = formState.value.privateKey,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChange = {
                        if (it.size() == 32) {
                            formState.value = formState.value.copy { privateKey = it }
                        }
                    },
                    trailingIcon = { CopyIconButton(valueToCopy = formState.value.privateKey.encodeToString()) },
                )
                HorizontalDivider()
                NodeActionButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = stringResource(Res.string.regenerate_private_key),
                    enabled = state.connected,
                    icon = Icons.TwoTone.Warning,
                    onClick = { showKeyGenerationDialog = true },
                )
                HorizontalDivider()
                NodeActionButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = stringResource(Res.string.export_keys),
                    enabled = state.connected,
                    icon = Icons.TwoTone.Warning,
                    onClick = { showEditSecurityConfigDialog = true },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.admin_keys)) {
                EditListPreference(
                    title = stringResource(Res.string.admin_key),
                    summary = stringResource(Res.string.config_security_admin_key),
                    list = formState.value.adminKeyList,
                    maxCount = 3,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValuesChanged = {
                        formState.value =
                            formState.value.copy {
                                adminKey.clear()
                                adminKey.addAll(it)
                            }
                    },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.logs)) {
                SwitchPreference(
                    title = stringResource(Res.string.serial_console),
                    summary = stringResource(Res.string.config_security_serial_enabled),
                    checked = formState.value.serialEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { serialEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.debug_log_api_enabled),
                    summary = stringResource(Res.string.config_security_debug_log_api_enabled),
                    checked = formState.value.debugLogApiEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { debugLogApiEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.administration)) {
                SwitchPreference(
                    title = stringResource(Res.string.managed_mode),
                    summary = stringResource(Res.string.config_security_is_managed),
                    checked = formState.value.isManaged,
                    enabled = state.connected && formState.value.adminKeyCount > 0,
                    onCheckedChange = { formState.value = formState.value.copy { isManaged = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.legacy_admin_channel),
                    checked = formState.value.adminChannelEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { adminChannelEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}

@Suppress("MagicNumber")
@Composable
fun PrivateKeyRegenerateDialog(
    showKeyGenerationDialog: Boolean,
    onConfirm: (SecurityConfig) -> Unit,
    onDismiss: () -> Unit = {},
) {
    if (showKeyGenerationDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(Res.string.regenerate_private_key)) },
            text = { Text(text = stringResource(Res.string.regenerate_keys_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val securityInput =
                            SecurityConfig.newBuilder()
                                .apply {
                                    clearPrivateKey()
                                    clearPublicKey()
                                    // Generate a random "f" value
                                    val f = ByteArray(32).apply { SecureRandom().nextBytes(this) }
                                    // Adjust the value to make it valid as an "s" value for eval().
                                    // According to the specification we need to mask off the 3
                                    // right-most bits of f[0], mask off the left-most bit of f[31],
                                    // and set the second to left-most bit of f[31].
                                    f[0] = (f[0].toInt() and 0xF8).toByte()
                                    f[31] = ((f[31].toInt() and 0x7F) or 0x40).toByte()
                                    privateKey = ByteString.copyFrom(f)
                                }
                                .build()
                        onConfirm(securityInput)
                    },
                ) {
                    Text(stringResource(Res.string.okay))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
