package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

@Composable // Default keyboardOptions: KeyboardType.Number, ImeAction.Send
fun EditTextPreference(
    title: String,
    value: String,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditTextPreference(
        title = title,
        value = value,
        enabled = enabled,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Send
        ),
        keyboardActions = keyboardActions,
        onValueChanged = onValueChanged,
        modifier = modifier
    )
}

@Composable
fun EditTextPreference(
    title: String,
    value: String,
    enabled: Boolean,
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
        onValueChange = onValueChanged,
        label = { Text(title) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Preview(showBackground = true)
@Composable
private fun EditTextPreferencePreview() {
    EditTextPreference(
        title = "Advanced Settings",
        value = "${UInt.MAX_VALUE}",
        enabled = true,
        keyboardActions = KeyboardActions {},
        onValueChanged = {}
    )
}
