/*
 * Copyright (c) 2024 Meshtastic LLC
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
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.SecurityConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.EditBase64Preference
import com.geeksville.mesh.ui.components.EditListPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

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

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Security Config") }

        item {
            EditBase64Preference(
                title = "Public Key",
                value = securityInput.publicKey,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChange = {
                    if (it.size() == 32) {
                        securityInput = securityInput.copy { publicKey = it }
                    }
                },
            )
        }

        item {
            EditBase64Preference(
                title = "Private Key",
                value = securityInput.privateKey,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChange = {
                    if (it.size() == 32) {
                        securityInput = securityInput.copy { privateKey = it }
                    }
                },
            )
        }

        item {
            EditListPreference(
                title = "Admin Key",
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
            SwitchPreference(title = "Managed Mode",
                checked = securityInput.isManaged,
                enabled = enabled && securityInput.adminKeyCount > 0,
                onCheckedChange = {
                    securityInput = securityInput.copy { isManaged = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Serial console",
                checked = securityInput.serialEnabled,
                enabled = enabled,
                onCheckedChange = { securityInput = securityInput.copy { serialEnabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Debug log API enabled",
                checked = securityInput.debugLogApiEnabled,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { debugLogApiEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Legacy Admin channel",
                checked = securityInput.adminChannelEnabled,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { adminChannelEnabled = it }
                })
        }
        item { Divider() }

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

@Preview(showBackground = true)
@Composable
private fun SecurityConfigPreview() {
    SecurityConfigItemList(
        securityConfig = SecurityConfig.getDefaultInstance(),
        enabled = true,
        onConfirm = {},
    )
}
