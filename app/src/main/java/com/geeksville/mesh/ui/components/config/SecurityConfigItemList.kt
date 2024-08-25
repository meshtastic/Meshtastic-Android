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
import com.geeksville.mesh.ConfigProtos.Config.SecurityConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditBase64Preference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

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
                    securityInput = securityInput.copy { publicKey = it }
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
                    securityInput = securityInput.copy { privateKey = it }
                },
            )
        }

        item {
            EditBase64Preference(
                title = "Admin Key",
                value = securityInput.adminKey,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChange = {
                    securityInput = securityInput.copy { adminKey = it }
                },
            )
        }

        item {
            SwitchPreference(title = "Managed mode",
                checked = securityInput.isManaged,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { isManaged = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Serial enabled",
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
            SwitchPreference(title = "Legacy Admin Channel",
                checked = securityInput.adminChannelEnabled,
                enabled = enabled,
                onCheckedChange = {
                    securityInput = securityInput.copy { adminChannelEnabled = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = securityInput != securityConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
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
