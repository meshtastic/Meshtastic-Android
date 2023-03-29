package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
    maxSize: Int = 0, // max_size - 1 (in bytes)
) {
    var isFocused by remember { mutableStateOf(false) }

    TextField(
        value = value,
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .onFocusEvent { isFocused = it.isFocused },
        enabled = enabled,
        isError = isError,
        onValueChange = {
            if (maxSize > 0) {
                if (it.toByteArray().size <= maxSize) {
                    onValueChanged(it)
                }
            } else onValueChanged(it)
        },
        label = { Text(title) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = {
            if (isError) Icon(Icons.TwoTone.Info, "Error", tint = MaterialTheme.colors.error)
        }
    )

    if (maxSize > 0 && isFocused) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "${value.toByteArray().size}/$maxSize",
                style = MaterialTheme.typography.caption,
                color = if (isError) MaterialTheme.colors.error else MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditTextPreferencePreview() {
    Column {
        EditTextPreference(
            title = "String",
            value = "Meshtastic",
            maxSize = 39,
            enabled = true,
            isError = false,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions {},
            onValueChanged = {},
        )
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
