package com.geeksville.mesh.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager

private enum class PositionPrecision(val value: Int) {
    HIGH_PRECISION(32),
    MED_PRECISION(16),
    LOW_PRECISION(11),
    DISABLED(0),
    ;
}

@Composable
fun PositionPrecisionPreference(
    title: String,
    value: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val useDropDown = PositionPrecision.entries.any { it.value == value }

    if (useDropDown) {
        DropDownPreference(
            title = title,
            enabled = enabled,
            items = PositionPrecision.entries.map { it.value to it.name },
            selectedItem = value,
            onItemSelected = onValueChanged,
        )
    } else {
        EditTextPreference(
            title = title,
            value = value,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = onValueChanged,
        )
    }
}
