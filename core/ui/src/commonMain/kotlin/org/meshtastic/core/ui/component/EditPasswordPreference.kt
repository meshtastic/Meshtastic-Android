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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.hide_password
import org.meshtastic.core.resources.show_password
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff

@Composable
fun EditPasswordPreference(
    title: String,
    value: String,
    maxSize: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    EditTextPreference(
        title = title,
        value = value,
        maxSize = maxSize,
        enabled = enabled,
        isError = false,
        keyboardOptions =
        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = keyboardActions,
        onValueChanged = { onValueChanged(it) },
        onFocusChanged = {},
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconToggleButton(checked = isPasswordVisible, onCheckedChange = { isPasswordVisible = it }) {
                Icon(
                    imageVector = if (isPasswordVisible) MeshtasticIcons.VisibilityOff else MeshtasticIcons.Visibility,
                    contentDescription =
                    if (isPasswordVisible) {
                        stringResource(Res.string.hide_password)
                    } else {
                        stringResource(Res.string.show_password)
                    },
                )
            }
        },
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun EditPasswordPreferencePreview() {
    EditPasswordPreference(
        title = "Password",
        value = "top secret",
        maxSize = 63,
        enabled = true,
        keyboardActions = KeyboardActions {},
        onValueChanged = {},
    )
}
