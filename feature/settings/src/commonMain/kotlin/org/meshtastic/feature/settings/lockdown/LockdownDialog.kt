/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.lockdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.lockdown_backoff
import org.meshtastic.core.resources.lockdown_boots_remaining
import org.meshtastic.core.resources.lockdown_confirm_passphrase
import org.meshtastic.core.resources.lockdown_enter_passphrase
import org.meshtastic.core.resources.lockdown_hide_passphrase
import org.meshtastic.core.resources.lockdown_hours_until_expiry
import org.meshtastic.core.resources.lockdown_incorrect_passphrase
import org.meshtastic.core.resources.lockdown_lock_reason
import org.meshtastic.core.resources.lockdown_passphrase
import org.meshtastic.core.resources.lockdown_passphrases_do_not_match
import org.meshtastic.core.resources.lockdown_set_passphrase
import org.meshtastic.core.resources.lockdown_show_passphrase
import org.meshtastic.core.resources.lockdown_submit
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff

/**
 * Non-dismissable lockdown authentication dialog.
 *
 * Shown when the connected device requires passphrase authentication. The dialog blocks all interaction with the app
 * until the user either authenticates successfully or disconnects. Back gestures are suppressed to prevent dismissing
 * the dialog and bypassing authentication.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun LockdownDialog(
    lockdownState: LockdownState,
    onSubmit: (passphrase: String, boots: Int, hours: Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    val shouldShow =
        when (lockdownState) {
            is LockdownState.Locked -> true
            is LockdownState.NeedsProvision -> true
            is LockdownState.UnlockFailed -> true
            is LockdownState.UnlockBackoff -> true
            else -> false
        }
    if (!shouldShow) return

    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmPassphrase by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var boots by rememberSaveable { mutableIntStateOf(LockdownPassphraseStore.DEFAULT_BOOTS) }
    var hours by rememberSaveable { mutableIntStateOf(0) }

    val isProvisioning = lockdownState is LockdownState.NeedsProvision
    val title =
        stringResource(if (isProvisioning) Res.string.lockdown_set_passphrase else Res.string.lockdown_enter_passphrase)
    val inBackoff = lockdownState is LockdownState.UnlockBackoff
    val passphraseValid = passphrase.isNotEmpty() && passphrase.encodeToByteArray().size <= MAX_PASSPHRASE_LEN
    val confirmValid = !isProvisioning || passphrase == confirmPassphrase
    val isValid = passphraseValid && confirmValid && !inBackoff

    AlertDialog(
        onDismissRequest = {}, // Non-dismissable
        title = { Text(text = title) },
        text = {
            Column {
                when (lockdownState) {
                    is LockdownState.UnlockFailed -> {
                        Text(
                            text = stringResource(Res.string.lockdown_incorrect_passphrase),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    }

                    is LockdownState.UnlockBackoff -> {
                        Text(
                            text = stringResource(Res.string.lockdown_backoff, lockdownState.backoffSeconds),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    }

                    is LockdownState.Locked -> {
                        if (lockdownState.lockReason.isNotEmpty()) {
                            Text(text = stringResource(Res.string.lockdown_lock_reason, lockdownState.lockReason))
                            Spacer(modifier = Modifier.height(SPACING_DP.dp))
                        }
                    }

                    else -> {}
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { if (it.encodeToByteArray().size <= MAX_PASSPHRASE_LEN) passphrase = it },
                    label = { Text(stringResource(Res.string.lockdown_passphrase)) },
                    singleLine = true,
                    visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector =
                                if (passwordVisible) {
                                    MeshtasticIcons.VisibilityOff
                                } else {
                                    MeshtasticIcons.Visibility
                                },
                                contentDescription =
                                stringResource(
                                    if (passwordVisible) {
                                        Res.string.lockdown_hide_passphrase
                                    } else {
                                        Res.string.lockdown_show_passphrase
                                    },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isProvisioning) {
                    Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { if (it.encodeToByteArray().size <= MAX_PASSPHRASE_LEN) confirmPassphrase = it },
                        label = { Text(stringResource(Res.string.lockdown_confirm_passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase,
                        supportingText =
                        if (confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase) {
                            { Text(stringResource(Res.string.lockdown_passphrases_do_not_match)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedTextField(
                            value = boots.toString(),
                            onValueChange = { str ->
                                str.toIntOrNull()?.let { boots = it.coerceIn(1, MAX_BYTE_VALUE) }
                            },
                            label = { Text(stringResource(Res.string.lockdown_boots_remaining)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(SPACING_DP.dp))
                        OutlinedTextField(
                            value = hours.toString(),
                            onValueChange = { str -> str.toIntOrNull()?.let { hours = it.coerceAtLeast(0) } },
                            label = { Text(stringResource(Res.string.lockdown_hours_until_expiry)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(passphrase, boots, hours) }, enabled = isValid) {
                Text(stringResource(Res.string.lockdown_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.disconnect)) } },
    )
}

// Firmware maximum: AdminMessage.lockdown_auth.passphrase is limited to 64 bytes.
private const val MAX_PASSPHRASE_LEN = 64
private const val MAX_BYTE_VALUE = 255
private const val SPACING_DP = 8
