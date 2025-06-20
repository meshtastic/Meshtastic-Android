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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.SecurityConfig
import com.geeksville.mesh.MeshProtos
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
import com.geeksville.mesh.util.toByteString
import com.google.protobuf.ByteString
import java.security.SecureRandom

@Composable
fun SecurityConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val node by viewModel.destNode.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    SecurityConfigItemList(
        user = node?.user,
        securityConfig = state.radioConfig.security,
        enabled = state.connected,
        onConfirm = { securityInput ->
            val config = config { security = securityInput }
            viewModel.setConfig(config)
        },
        onExport = { uri, securityConfig ->
            viewModel.exportSecurityConfig(uri, securityConfig)
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod")
@Composable
fun SecurityConfigItemList(
    user: MeshProtos.User? = null,
    securityConfig: SecurityConfig,
    enabled: Boolean,
    onConfirm: (config: SecurityConfig) -> Unit,
    onExport: (uri: Uri, securityConfig: SecurityConfig) -> Unit = { _, _ -> },
) {
    val focusManager = LocalFocusManager.current
    var securityInput by rememberSaveable { mutableStateOf(securityConfig) }

    var publicKey by rememberSaveable { mutableStateOf(securityInput.publicKey) }
    LaunchedEffect(securityInput.privateKey) {
        if (securityInput.privateKey != securityConfig.privateKey) {
            publicKey = "".toByteString()
        } else if (securityInput.privateKey == securityConfig.privateKey) {
            publicKey = securityConfig.publicKey
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri -> onExport(uri, securityConfig) }
        }
    }

    var showKeyGenerationDialog by rememberSaveable { mutableStateOf(false) }
    PrivateKeyRegenerateDialog(
        showKeyGenerationDialog = showKeyGenerationDialog,
        config = securityInput,
        onConfirm = { newConfig ->
            securityInput = newConfig
            showKeyGenerationDialog = false
            onConfirm(securityInput)
        },
        onDismiss = { showKeyGenerationDialog = false }
    )
    var showEditSecurityConfigDialog by rememberSaveable { mutableStateOf(false) }
    if (showEditSecurityConfigDialog) {
        AlertDialog(
            title = { Text(text = stringResource(R.string.export_keys)) },
            text = { Text(text = stringResource(R.string.export_keys_confirmation)) },
            onDismissRequest = { showEditSecurityConfigDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditSecurityConfigDialog = false
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/*"
                            putExtra(
                                Intent.EXTRA_TITLE,
                                "${user?.shortName}_keys_${System.currentTimeMillis()}.json"
                            )
                        }
                        exportConfigLauncher.launch(intent)
                    },
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = stringResource(R.string.security_config)) }

        item {
            EditBase64Preference(
                title = stringResource(R.string.public_key),
                value = publicKey,
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
                modifier = Modifier.padding(horizontal = 8.dp),
                title = stringResource(R.string.regenerate_private_key),
                enabled = enabled,
                icon = Icons.TwoTone.Warning,
                onClick = {
                    showKeyGenerationDialog = true
                }
            )
        }

        item {
            NodeActionButton(
                modifier = Modifier.padding(horizontal = 8.dp),
                title = stringResource(R.string.export_keys),
                enabled = enabled,
                icon = Icons.TwoTone.Warning,
                onClick = {
                    showEditSecurityConfigDialog = true
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

@Suppress("MagicNumber")
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
                            clearPublicKey()
                            // Generate a random "f" value
                            val f = ByteArray(32).apply {
                                SecureRandom().nextBytes(this)
                            }
                            // Adjust the value to make it valid as an "s" value for eval().
                            // According to the specification we need to mask off the 3
                            // right-most bits of f[0], mask off the left-most bit of f[31],
                            // and set the second to left-most bit of f[31].
                            f[0] = (f[0].toInt() and 0xF8).toByte()
                            f[31] = ((f[31].toInt() and 0x7F) or 0x40).toByte()
                            privateKey = ByteString.copyFrom(f)
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
