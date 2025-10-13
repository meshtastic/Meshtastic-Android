/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Deprecated(message = "Use overload that accepts Strings for button text.")
@Composable
fun PreferenceFooter(
    enabled: Boolean,
    @StringRes negativeText: Int,
    onNegativeClicked: () -> Unit,
    @StringRes positiveText: Int,
    onPositiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceFooter(
        enabled = enabled,
        negativeText = stringResource(id = negativeText),
        onNegativeClicked = onNegativeClicked,
        positiveText = stringResource(id = positiveText),
        onPositiveClicked = onPositiveClicked,
        modifier = modifier,
    )
}

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    negativeText: String,
    onNegativeClicked: () -> Unit,
    positiveText: String,
    onPositiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElevatedButton(
            modifier = Modifier.height(48.dp).weight(1f),
            colors = ButtonDefaults.filledTonalButtonColors(),
            onClick = onNegativeClicked,
        ) {
            Text(text = negativeText)
        }
        ElevatedButton(
            modifier = Modifier.height(48.dp).weight(1f),
            colors = ButtonDefaults.buttonColors(),
            onClick = { if (enabled) onPositiveClicked() },
        ) {
            Text(text = positiveText)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceFooterPreview() {
    PreferenceFooter(
        enabled = true,
        negativeText = "Cancel",
        onNegativeClicked = {},
        positiveText = "Save",
        onPositiveClicked = {},
    )
}
