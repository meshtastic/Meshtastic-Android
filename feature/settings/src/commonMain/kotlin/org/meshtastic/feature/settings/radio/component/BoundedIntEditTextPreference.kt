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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.feature.settings.util.intRange
import org.meshtastic.proto.FieldMetadata

/**
 * An integer field bounded by its proto field metadata: an out-of-range value shows as an error and is not applied. A
 * field the registry leaves unbounded accepts any value.
 */
@Composable
fun BoundedIntEditTextPreference(
    title: String,
    value: Int,
    metadata: FieldMetadata,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onFocusChanged: (FocusState) -> Unit = {},
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val range = remember(metadata) { metadata.intRange }
    EditTextPreference(
        title = title,
        value = value,
        enabled = enabled,
        isError = range != null && value !in range,
        keyboardActions = keyboardActions,
        onValueChanged = { if (range == null || it in range) onValueChange(it) },
        modifier = modifier,
        summary = summary,
        onFocusChanged = onFocusChanged,
        trailingIcon = trailingIcon,
    )
}
