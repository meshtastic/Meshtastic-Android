/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.encodeToString
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.admin_key
import org.meshtastic.core.strings.admin_keys
import org.meshtastic.core.strings.administration
import org.meshtastic.core.strings.config_security_admin_key
import org.meshtastic.core.strings.config_security_debug_log_api_enabled
import org.meshtastic.core.strings.config_security_is_managed
import org.meshtastic.core.strings.config_security_private_key
import org.meshtastic.core.strings.config_security_public_key
import org.meshtastic.core.strings.config_security_serial_enabled
import org.meshtastic.core.strings.debug_log_api_enabled
import org.meshtastic.core.strings.direct_message_key
import org.meshtastic.core.strings.export_keys
import org.meshtastic.core.strings.export_keys_confirmation
import org.meshtastic.core.strings.legacy_admin_channel
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.managed_mode
import org.meshtastic.core.strings.private_key
import org.meshtastic.core.strings.public_key
import org.meshtastic.core.strings.regenerate_keys_confirmation
import org.meshtastic.core.strings.regenerate_private_key
import org.meshtastic.core.strings.security
import org.meshtastic.core.strings.serial_console
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.EditBase64Preference
import org.meshtastic.core.ui.component.EditListPreference
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurityConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val node by viewModel.destNode.collectAsStateWithLifecycle()
    val securityConfig = state.radioConfig.security ?: Config.SecurityConfig()
    val formState = rememberConfigState(initialValue = securityConfig)

    var publicKey by rememberSaveable { mutableStateOf(formState.value.public_key) }
    LaunchedEffect(formState.value.private_key) {
        if (formState.value.private_key != securityConfig.private_key) {
            publicKey = ByteString.EMPTY
        } else if (formState.value.private_key == securityConfig.private_key) {
            publicKey = securityConfig.public_key
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
            val config = Config(security = formState.value)
            viewModel.setConfig(config)
        },
        onDismiss = { showKeyGenerationDialog = false },
    )
    var showEditSecurityConfigDialog by rememberSaveable { mutableStateOf(false) }
    if (showEditSecurityConfigDialog) {
        MeshtasticResourceDialog(
            titleRes = Res.string.export_keys,
            messageRes = Res.string.export_keys_confirmation,
            onDismiss = { showEditSecurityConfigDialog = false },
            onConfirm = {
                showEditSecurityConfigDialog = false
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/*"
                        putExtra(Intent.EXTRA_TITLE, "${node?.user?.short_name}_keys_$nowMillis.json")
                    }
                exportConfigLauncher.launch(intent)
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
            val config = Config(security = it)
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
                        if (it.size == 32) {
                            formState.value = formState.value.copy(public_key = it)
                        }
                    },
                    trailingIcon = { CopyIconButton(valueToCopy = formState.value.public_key.encodeToString()) },
                )
                HorizontalDivider()
                EditBase64Preference(
                    title = stringResource(Res.string.private_key),
                    summary = stringResource(Res.string.config_security_private_key),
                    value = formState.value.private_key,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChange = {
                        if (it.size == 32) {
                            formState.value = formState.value.copy(private_key = it)
                        }
                    },
                    trailingIcon = { CopyIconButton(valueToCopy = formState.value.private_key.encodeToString()) },
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
                    list = formState.value.admin_key,
                    maxCount = 3,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValuesChanged = { formState.value = formState.value.copy(admin_key = it) },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.logs)) {
                SwitchPreference(
                    title = stringResource(Res.string.serial_console),
                    summary = stringResource(Res.string.config_security_serial_enabled),
                    checked = formState.value.serial_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(serial_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.debug_log_api_enabled),
                    summary = stringResource(Res.string.config_security_debug_log_api_enabled),
                    checked = formState.value.debug_log_api_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(debug_log_api_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.administration)) {
                SwitchPreference(
                    title = stringResource(Res.string.managed_mode),
                    summary = stringResource(Res.string.config_security_is_managed),
                    checked = formState.value.is_managed,
                    enabled = state.connected && formState.value.admin_key.isNotEmpty(),
                    onCheckedChange = { formState.value = formState.value.copy(is_managed = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.legacy_admin_channel),
                    checked = formState.value.admin_channel_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(admin_channel_enabled = it) },
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
    onConfirm: (Config.SecurityConfig) -> Unit,
    onDismiss: () -> Unit = {},
) {
    if (showKeyGenerationDialog) {
        MeshtasticResourceDialog(
            onDismiss = onDismiss,
            titleRes = Res.string.regenerate_private_key,
            messageRes = Res.string.regenerate_keys_confirmation,
            onConfirm = {
                // Generate a random "f" value
                val f = ByteArray(32).apply { SecureRandom().nextBytes(this) }
                // Adjust the value to make it valid as an "s" value for eval().
                // According to the specification we need to mask off the 3
                // right-most bits of f[0], mask off the left-most bit of f[31],
                // and set the second to left-most bit of f[31].
                f[0] = (f[0].toInt() and 0xF8).toByte()
                f[31] = ((f[31].toInt() and 0x7F) or 0x40).toByte()
                val securityInput = Config.SecurityConfig(private_key = f.toByteString(), public_key = ByteString.EMPTY)
                onConfirm(securityInput)
            },
        )
    }
}
