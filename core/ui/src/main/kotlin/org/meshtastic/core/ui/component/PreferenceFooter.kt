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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.strings.R

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceFooter(
        enabled = enabled,
        negativeText = R.string.clear_changes,
        onNegativeClicked = onCancelClicked,
        positiveText = R.string.send,
        onPositiveClicked = onSaveClicked,
        modifier = modifier,
    )
}

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    @StringRes negativeText: Int,
    onNegativeClicked: () -> Unit,
    @StringRes positiveText: Int,
    onPositiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(modifier = Modifier.height(48.dp).weight(1f), onClick = onNegativeClicked) {
            Text(text = stringResource(id = negativeText))
        }
        OutlinedButton(modifier = Modifier.height(48.dp).weight(1f), enabled = enabled, onClick = onPositiveClicked) {
            Text(text = stringResource(id = positiveText))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceFooterPreview() {
    PreferenceFooter(enabled = true, onCancelClicked = {}, onSaveClicked = {})
}
