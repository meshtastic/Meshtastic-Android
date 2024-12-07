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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EditIPv4Preference(
    title: String,
    value: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pattern = """\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""".toRegex()

    fun convertIntToIpAddress(int: Int): String {
        return "${int and 0xff}.${int shr 8 and 0xff}.${int shr 16 and 0xff}.${int shr 24 and 0xff}"
    }

    fun convertIpAddressToInt(ipAddress: String): Int? = ipAddress.split(".")
        .map { it.toIntOrNull() }.reversed() // little-endian byte order
        .fold(0) { total, next ->
            if (next == null) return null else total shl 8 or next
        }

    var valueState by remember(value) { mutableStateOf(convertIntToIpAddress(value)) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = convertIntToIpAddress(value) != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            valueState = it
            if (pattern.matches(it)) convertIpAddressToInt(it)?.let { int -> onValueChanged(int) }
        },
        onFocusChanged = {},
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun EditIPv4PreferencePreview() {
    EditIPv4Preference(
        title = "IP Address",
        value = 16820416,
        enabled = true,
        keyboardActions = KeyboardActions {},
        onValueChanged = {}
    )
}
