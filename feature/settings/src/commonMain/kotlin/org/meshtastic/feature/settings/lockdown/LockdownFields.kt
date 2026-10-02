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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.lockdown_boots_remaining
import org.meshtastic.core.resources.lockdown_confirm_passphrase
import org.meshtastic.core.resources.lockdown_hide_passphrase
import org.meshtastic.core.resources.lockdown_hours_until_expiry
import org.meshtastic.core.resources.lockdown_passphrases_do_not_match
import org.meshtastic.core.resources.lockdown_session_minutes
import org.meshtastic.core.resources.lockdown_session_minutes_help
import org.meshtastic.core.resources.lockdown_show_passphrase
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff

// Firmware maximum: AdminMessage.lockdown_auth.passphrase is limited to 64 bytes.
private const val MAX_PASSPHRASE_LEN = 64
private const val MAX_BYTE_VALUE = 255
private const val SPACING_DP = 8

/** A passphrase the firmware accepts: non-empty and within its byte limit. */
internal fun isValidLockdownPassphrase(passphrase: String): Boolean =
    passphrase.isNotEmpty() && passphrase.encodeToByteArray().size <= MAX_PASSPHRASE_LEN

private fun fitsPassphraseLimit(text: String): Boolean = text.encodeToByteArray().size <= MAX_PASSPHRASE_LEN

@Composable
internal fun LockdownPassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (fitsPassphraseLimit(it)) onValueChange(it) },
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
        modifier = modifier.fillMaxWidth(),
    )
}

/** The confirm-passphrase field; flags a mismatch once anything has been typed. */
@Composable
internal fun LockdownConfirmPassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    matches: Boolean,
    modifier: Modifier = Modifier,
) {
    val showMismatch = value.isNotEmpty() && !matches
    OutlinedTextField(
        value = value,
        onValueChange = { if (fitsPassphraseLimit(it)) onValueChange(it) },
        label = { Text(stringResource(Res.string.lockdown_confirm_passphrase)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        isError = showMismatch,
        supportingText =
        if (showMismatch) {
            { Text(stringResource(Res.string.lockdown_passphrases_do_not_match)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Boots, hours and session-minute limits for a new lockdown passphrase, clamped to what firmware stores. */
@Composable
internal fun LockdownLimitsFields(
    boots: Int,
    onBootsChange: (Int) -> Unit,
    hours: Int,
    onHoursChange: (Int) -> Unit,
    sessionMinutes: Int,
    onSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedTextField(
                value = boots.toString(),
                onValueChange = { str -> str.toIntOrNull()?.let { onBootsChange(it.coerceIn(1, MAX_BYTE_VALUE)) } },
                label = { Text(stringResource(Res.string.lockdown_boots_remaining)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(SPACING_DP.dp))
            OutlinedTextField(
                value = hours.toString(),
                onValueChange = { str -> str.toIntOrNull()?.let { onHoursChange(it.coerceAtLeast(0)) } },
                label = { Text(stringResource(Res.string.lockdown_hours_until_expiry)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(SPACING_DP.dp))
        OutlinedTextField(
            value = sessionMinutes.toString(),
            onValueChange = { str -> str.toIntOrNull()?.let { onSessionMinutesChange(it.coerceAtLeast(0)) } },
            label = { Text(stringResource(Res.string.lockdown_session_minutes)) },
            supportingText = { Text(stringResource(Res.string.lockdown_session_minutes_help)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
