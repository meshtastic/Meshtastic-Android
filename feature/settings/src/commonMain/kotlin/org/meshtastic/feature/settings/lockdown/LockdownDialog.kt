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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.lockdown_backoff
import org.meshtastic.core.resources.lockdown_enter_passphrase
import org.meshtastic.core.resources.lockdown_incorrect_passphrase
import org.meshtastic.core.resources.lockdown_lock_reason
import org.meshtastic.core.resources.lockdown_passphrase
import org.meshtastic.core.resources.lockdown_set_passphrase
import org.meshtastic.core.resources.lockdown_submit

/**
 * Non-dismissable lockdown authentication dialog.
 *
 * Shown while the connected device requires passphrase input or retry. The dialog blocks app interaction while shown.
 * After a passphrase command is admitted to the active transport, [LockdownState.AwaitingResponse] hides it until
 * firmware reports the next status. A rejected dispatch leaves the retryable state and dialog in place; a locked,
 * provisioning, or failed response shows it again, while successful authentication leaves it dismissed. Back gestures
 * are suppressed whenever the dialog is visible.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun LockdownDialog(
    lockdownState: LockdownState,
    onSubmit: (passphrase: String, boots: Int, hours: Int, sessionMinutes: Int) -> Unit,
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
    var sessionMinutes by rememberSaveable { mutableIntStateOf(0) }

    val isProvisioning = lockdownState is LockdownState.NeedsProvision
    val title =
        stringResource(if (isProvisioning) Res.string.lockdown_set_passphrase else Res.string.lockdown_enter_passphrase)
    val inBackoff = lockdownState is LockdownState.UnlockBackoff
    val confirmValid = !isProvisioning || passphrase == confirmPassphrase
    val isValid = isValidLockdownPassphrase(passphrase) && confirmValid && !inBackoff

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

                LockdownPassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(Res.string.lockdown_passphrase),
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                )

                if (isProvisioning) {
                    Spacer(modifier = Modifier.height(SPACING_DP.dp))
                    LockdownConfirmPassphraseField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        matches = passphrase == confirmPassphrase,
                    )
                }
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                LockdownLimitsFields(
                    boots = boots,
                    onBootsChange = { boots = it },
                    hours = hours,
                    onHoursChange = { hours = it },
                    sessionMinutes = sessionMinutes,
                    onSessionMinutesChange = { sessionMinutes = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(passphrase, boots, hours, sessionMinutes) }, enabled = isValid) {
                Text(stringResource(Res.string.lockdown_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.disconnect)) } },
    )
}

private const val SPACING_DP = 8
