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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.SecurityConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.CopyIconButton
import com.geeksville.mesh.ui.common.components.EditBase64Preference
import com.geeksville.mesh.ui.common.components.EditListPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.node.NodeActionButton
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.util.encodeToString

@Composable
fun SecurityConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    SecurityConfigItemList(
        securityConfig = state.radioConfig.security,
        enabled = state.connected,
        onConfirm = { securityInput ->
            val config = config { security = securityInput }
            viewModel.setConfig(config)
        }
    )
}

@Suppress("LongMethod")
@Composable
fun SecurityConfigItemList(
    securityConfig: SecurityConfig,
    enabled: Boolean,
    onConfirm: (config: SecurityConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var securityInput by rememberSaveable { mutableStateOf(securityConfig) }

    var showKeyGenerationDialog by rememberSaveable { mutableStateOf(false) }
    PrivateKeyRegenerateDialog(
        showKeyGenerationDialog = showKeyGenerationDialog,
        config = securityInput,
        onConfirm = { newConfig ->
            securityInput = newConfig
            showKeyGenerationDialog = false
        },
        onDismiss = { showKeyGenerationDialog = false }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.security_config)) }

        item {
            EditBase64Preference(
                title = stringResource(R.string.public_key),
                value = securityInput.publicKey,
                enabled = enabled,
                readOnly = true,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChange = {
                    if (it.size() == 32) {
                        securityInput = securityInput.copy { publicKey = it }
                    }
                },
                trailingIcon = {
                    CopyIconButton(
                        valueToCopy = securityInput.publicKey.encodeToString(),
                    )
                }
            )
        }

        item {
            EditBase64Preference(
                title = stringResource(R.string.private_key),
                value = securityInput.privateKey,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChange = {
                    if (it.size() == 32) {
                        securityInput = securityInput.copy { privateKey = it }
                    }
                },
                trailingIcon = {
                    CopyIconButton(
                        valueToCopy = securityInput.privateKey.encodeToString(),
                    )
                }
            )
        }

        item {
            NodeActionButton(
                modifier = Modifier.padding(16.dp),
                title = stringResource(R.string.regenerate_private_key),
                enabled = enabled,
                icon = Icons.TwoTone.Warning,
                onClick = {
                    showKeyGenerationDialog = true
                }
            )
        }

        item {
            EditListPreference(
                title = stringResource(R.string.admin_key),
                list = securityInput.adminKeyList,
                maxCount = 3,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValuesChanged = {
                    securityInput = securityInput.copy {
                        adminKey.clear()
                        adminKey.addAll(it)
                    }
                },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.managed_mode),
                checked = securityInput.isManaged,
                enabled = enabled && securityInput.adminKeyCount > 0,
                onCheckedChange = {
                    securityInput = securityInput.copy { isManaged = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.serial_console),
                checked = securityInput.serialEnabled,
                enabled = enabled,
                onCheckedChange = { securityInput = securityInput.copy { serialEnabled = it } }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.debug_log_api_enabled),
                checked = securityInput.debugLogApiEnabled,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { debugLogApiEnabled = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.legacy_admin_channel),
                checked = securityInput.adminChannelEnabled,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { adminChannelEnabled = it }
                }
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && securityInput != securityConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    securityInput = securityConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onConfirm(securityInput)
                }
            )
        }
    }
}

@Composable
fun PrivateKeyRegenerateDialog(
    showKeyGenerationDialog: Boolean,
    config: SecurityConfig,
    onConfirm: (SecurityConfig) -> Unit,
    onDismiss: () -> Unit = {},
) {
    var securityInput by rememberSaveable { mutableStateOf(config) }
    if (showKeyGenerationDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.regenerate_private_key)) },
            text = { Text(text = stringResource(R.string.regenerate_keys_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        securityInput = securityInput.copy {
                            clearPrivateKey()
                        }
                        onConfirm(securityInput)
                    },
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SecurityConfigPreview() {
    SecurityConfigItemList(
        securityConfig = SecurityConfig.getDefaultInstance(),
        enabled = true,
        onConfirm = {},
    )
}
