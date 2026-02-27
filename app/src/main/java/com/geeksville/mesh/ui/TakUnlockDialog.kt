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
package com.geeksville.mesh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import org.meshtastic.core.service.TakLockState
import org.meshtastic.core.service.TakTokenInfo

@Suppress("LongMethod")
@Composable
fun TakUnlockDialog(
    takLockState: TakLockState,
    takTokenInfo: TakTokenInfo? = null,
    onSubmit: (passphrase: String, boots: Int, hours: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val shouldShow = when (takLockState) {
        is TakLockState.Locked -> true
        is TakLockState.NeedsProvision -> true
        is TakLockState.UnlockFailed -> true
        is TakLockState.UnlockBackoff -> true
        else -> false
    }
    BackHandler(enabled = shouldShow, onBack = onDismiss)
    if (!shouldShow) return

    var passphrase by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // Pre-fill from most recent TAK_UNLOCKED token info when available.
    val initialBoots = takTokenInfo?.bootsRemaining ?: DEFAULT_BOOTS
    val initialHours = if ((takTokenInfo?.expiryEpoch ?: 0L) > 0L) {
        ((takTokenInfo!!.expiryEpoch - System.currentTimeMillis() / 1000) / 3600)
            .toInt().coerceAtLeast(0)
    } else {
        0
    }
    var boots by rememberSaveable { mutableIntStateOf(initialBoots) }
    var hours by rememberSaveable { mutableIntStateOf(initialHours) }

    val isProvisioning = takLockState is TakLockState.NeedsProvision
    val title = if (isProvisioning) "Set Passphrase" else "Enter Passphrase"
    val isValid = passphrase.isNotEmpty() && passphrase.length <= MAX_PASSPHRASE_LEN

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = title) },
        text = {
            Column {
                when (takLockState) {
                    is TakLockState.UnlockFailed -> {
                        Text(
                            text = "Incorrect passphrase.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    }
                    is TakLockState.UnlockBackoff -> {
                        Text(
                            text = "Try again in ${takLockState.backoffSeconds} seconds.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    }
                    else -> {}
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { if (it.length <= MAX_PASSPHRASE_LEN) passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (passwordVisible) "Hide" else "Show",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Boot/Hour TTL fields always shown — operator can renew the token window on every unlock
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedTextField(
                        value = boots.toString(),
                        onValueChange = { str ->
                            str.toIntOrNull()?.let { boots = it.coerceIn(1, MAX_BYTE_VALUE) }
                        },
                        label = { Text("Boot TTL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(SPACING_DP.dp))
                    OutlinedTextField(
                        value = hours.toString(),
                        onValueChange = { str ->
                            str.toIntOrNull()?.let { hours = it.coerceAtLeast(0) }
                        },
                        label = { Text("Hour TTL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(passphrase, boots, hours) },
                enabled = isValid,
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val DEFAULT_BOOTS = 50
private const val MAX_PASSPHRASE_LEN = 64
private const val MAX_BYTE_VALUE = 255
private const val SPACING_DP = 8
