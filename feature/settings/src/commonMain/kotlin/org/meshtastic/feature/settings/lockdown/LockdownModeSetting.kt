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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.service.LockdownTokenInfo
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.lockdown_boots_remaining
import org.meshtastic.core.resources.lockdown_confirm_passphrase
import org.meshtastic.core.resources.lockdown_disable
import org.meshtastic.core.resources.lockdown_disable_message
import org.meshtastic.core.resources.lockdown_enable
import org.meshtastic.core.resources.lockdown_enable_ack
import org.meshtastic.core.resources.lockdown_enable_warning
import org.meshtastic.core.resources.lockdown_hide_passphrase
import org.meshtastic.core.resources.lockdown_hours_until_expiry
import org.meshtastic.core.resources.lockdown_lock_now
import org.meshtastic.core.resources.lockdown_mode
import org.meshtastic.core.resources.lockdown_mode_setting_up
import org.meshtastic.core.resources.lockdown_mode_summary_locked
import org.meshtastic.core.resources.lockdown_mode_summary_off
import org.meshtastic.core.resources.lockdown_mode_summary_unlocked
import org.meshtastic.core.resources.lockdown_passphrase
import org.meshtastic.core.resources.lockdown_passphrases_do_not_match
import org.meshtastic.core.resources.lockdown_session_minutes
import org.meshtastic.core.resources.lockdown_session_minutes_help
import org.meshtastic.core.resources.lockdown_set_passphrase
import org.meshtastic.core.resources.lockdown_show_passphrase
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff
import org.meshtastic.feature.settings.radio.component.NodeActionButton

/**
 * Runtime lockdown-mode toggle for the security settings screen.
 *
 * The switch and its dialogs are driven entirely by the latest [lockdownState]:
 * - [LockdownState.Disabled] / [LockdownState.NeedsProvision] → OFF; turning ON opens the set-passphrase dialog with
 *   the one-time irreversible warning.
 * - [LockdownState.Locked] → ON (locked); authentication is handled by the global lockdown dialog, so the switch is
 *   read-only here.
 * - [LockdownState.Unlocked] → ON; turning OFF opens the disable dialog, plus a "Lock now" affordance and session info.
 *
 * Visibility is gated on [supported] — the firmware-version capability from `Capabilities.supportsLockdown` (lockdown
 * ships in firmware v2.8.0). [lockdownState] drives the switch position once a `LockdownStatus` arrives.
 */
@Composable
fun ColumnScope.LockdownModeSetting(
    supported: Boolean,
    lockdownState: LockdownState,
    tokenInfo: LockdownTokenInfo?,
    connected: Boolean,
    containerColor: Color,
    onEnable: (passphrase: String, boots: Int, hours: Int, sessionMinutes: Int) -> Unit,
    onDisable: (passphrase: String) -> Unit,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!supported) return

    var showEnableDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }

    val lockdownOn = lockdownState is LockdownState.Locked || lockdownState is LockdownState.Unlocked
    val unlocked = lockdownState is LockdownState.Unlocked
    // Only DISABLED/NEEDS_PROVISION (turn on) and UNLOCKED (turn off) are actionable from here; LOCKED auth is driven
    // by the blocking global dialog.
    val toggleEnabled =
        connected &&
            (lockdownState is LockdownState.Disabled || lockdownState is LockdownState.NeedsProvision || unlocked)

    val summary =
        when (lockdownState) {
            is LockdownState.Unlocked -> stringResource(Res.string.lockdown_mode_summary_unlocked)
            is LockdownState.Locked -> stringResource(Res.string.lockdown_mode_summary_locked)
            is LockdownState.NeedsProvision -> stringResource(Res.string.lockdown_mode_setting_up)
            else -> stringResource(Res.string.lockdown_mode_summary_off)
        }

    SwitchPreference(
        modifier = modifier,
        title = stringResource(Res.string.lockdown_mode),
        summary = summary,
        checked = lockdownOn,
        enabled = toggleEnabled,
        onCheckedChange = { turnOn -> if (turnOn) showEnableDialog = true else showDisableDialog = true },
        containerColor = containerColor,
    )

    if (unlocked) {
        LockdownSessionStatus(tokenInfo = tokenInfo)
        NodeActionButton(
            modifier = Modifier.padding(horizontal = SPACING_DP.dp),
            title = stringResource(Res.string.lockdown_lock_now),
            enabled = connected,
            onClick = onLockNow,
        )
    }

    if (showEnableDialog) {
        EnableLockdownDialog(
            onConfirm = { passphrase, boots, hours, sessionMinutes ->
                showEnableDialog = false
                onEnable(passphrase, boots, hours, sessionMinutes)
            },
            onDismiss = { showEnableDialog = false },
        )
    }

    if (showDisableDialog) {
        DisableLockdownDialog(
            onConfirm = { passphrase ->
                showDisableDialog = false
                onDisable(passphrase)
            },
            onDismiss = { showDisableDialog = false },
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun EnableLockdownDialog(
    onConfirm: (passphrase: String, boots: Int, hours: Int, sessionMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmPassphrase by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var boots by rememberSaveable { mutableIntStateOf(LockdownPassphraseStore.DEFAULT_BOOTS) }
    var hours by rememberSaveable { mutableIntStateOf(0) }
    var sessionMinutes by rememberSaveable { mutableIntStateOf(0) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }

    val passphraseValid = passphrase.isNotEmpty() && passphrase.encodeToByteArray().size <= MAX_PASSPHRASE_LEN
    val matches = passphrase == confirmPassphrase
    val isValid = passphraseValid && matches && acknowledged

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.lockdown_set_passphrase)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.lockdown_enable_warning),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(Res.string.lockdown_passphrase),
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                )
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = { if (it.encodeToByteArray().size <= MAX_PASSPHRASE_LEN) confirmPassphrase = it },
                    label = { Text(stringResource(Res.string.lockdown_confirm_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmPassphrase.isNotEmpty() && !matches,
                    supportingText =
                    if (confirmPassphrase.isNotEmpty() && !matches) {
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
                        onValueChange = { str -> str.toIntOrNull()?.let { boots = it.coerceIn(1, MAX_BYTE_VALUE) } },
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
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                OutlinedTextField(
                    value = sessionMinutes.toString(),
                    onValueChange = { str -> str.toIntOrNull()?.let { sessionMinutes = it.coerceAtLeast(0) } },
                    label = { Text(stringResource(Res.string.lockdown_session_minutes)) },
                    supportingText = { Text(stringResource(Res.string.lockdown_session_minutes_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(stringResource(Res.string.lockdown_enable_ack))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase, boots, hours, sessionMinutes) }, enabled = isValid) {
                Text(stringResource(Res.string.lockdown_enable))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun DisableLockdownDialog(onConfirm: (passphrase: String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isValid = passphrase.isNotEmpty() && passphrase.encodeToByteArray().size <= MAX_PASSPHRASE_LEN

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.lockdown_disable)) },
        text = {
            Column {
                Text(stringResource(Res.string.lockdown_disable_message))
                Spacer(modifier = Modifier.height(SPACING_DP.dp))
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(Res.string.lockdown_passphrase),
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = isValid) {
                Text(stringResource(Res.string.lockdown_disable))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.encodeToByteArray().size <= MAX_PASSPHRASE_LEN) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) MeshtasticIcons.VisibilityOff else MeshtasticIcons.Visibility,
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
}

// Firmware maximum: AdminMessage.lockdown_auth.passphrase is limited to 64 bytes.
private const val MAX_PASSPHRASE_LEN = 64
private const val MAX_BYTE_VALUE = 255
private const val SPACING_DP = 8
