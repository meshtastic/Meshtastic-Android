package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
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
fun EditTextPreference(
    title: String,
    value: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var valueState by remember(value) { mutableStateOf(value.toUInt().toString()) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toUInt().toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.isEmpty()) valueState = it
            else it.toUIntOrNull()?.toInt()?.let { int ->
                valueState = it
                onValueChanged(int)
            }
        },
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: Float,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var valueState by remember(value) { mutableStateOf(value.toString()) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.isEmpty()) valueState = it
            else it.toFloatOrNull()?.let { float ->
                valueState = it
                onValueChanged(float)
            }
        },
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: Double,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var valueState by remember(value) { mutableStateOf(value.toString()) }

    EditTextPreference(
        title = title,
        value = valueState,
        enabled = enabled,
        isError = value.toString() != valueState,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onValueChanged = {
            if (it.isEmpty()) valueState = it
            else it.toDoubleOrNull()?.let { double ->
                valueState = it
                onValueChanged(double)
            }
        },
        modifier = modifier
    )
}

@Composable
fun EditIPv4Preference(
    title: String,
    value: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun convertIntToIpAddress(int: Int): String {
        return "${int shr 24 and 0xff}.${int shr 16 and 0xff}.${int shr 8 and 0xff}.${int and 0xff}"
    }
    fun convertIpAddressToInt(ipAddress: String): Int? {
        return ipAddress.split(".")
            .map { it.toIntOrNull() }
            .fold(0) { total, next ->
                if (next == null) return null else total shl 8 or next
            }
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
            val pattern = """\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""".toRegex()
            val isValid = pattern.matches(it)
            if (it.isEmpty() || !isValid) valueState = it
            else convertIpAddressToInt(it)?.let { int ->
                valueState = it
                onValueChanged(int)
            }
        },
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: String,
    enabled: Boolean,
    isError: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        onValueChange = onValueChanged,
        label = { Text(title) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = {
            if (isError) Icon(Icons.TwoTone.Info, "Error", tint = MaterialTheme.colors.error)
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun EditTextPreferencePreview() {
    Column {
        EditTextPreference(
            title = "Advanced Settings",
            value = UInt.MAX_VALUE.toInt(),
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValueChanged = {}
        )
        EditIPv4Preference(
            title = "IP Address",
            value = 3232235521.toInt(),
            enabled = true,
            keyboardActions = KeyboardActions {},
            onValueChanged = {}
        )
    }
}
