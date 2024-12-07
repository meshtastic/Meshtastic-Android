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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceFooter(
        enabled = enabled,
        negativeText = R.string.cancel,
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
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            modifier = modifier
                .height(48.dp)
                .weight(1f),
            enabled = enabled,
            onClick = onNegativeClicked,
            colors = ButtonDefaults.buttonColors(
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) {
            Text(
                text = stringResource(id = negativeText),
                style = MaterialTheme.typography.body1,
            )
        }
        OutlinedButton(
            modifier = modifier
                .height(48.dp)
                .weight(1f),
            enabled = enabled,
            onClick = onPositiveClicked,
            colors = ButtonDefaults.buttonColors(
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) {
            Text(
                text = stringResource(id = positiveText),
                style = MaterialTheme.typography.body1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceFooterPreview() {
    PreferenceFooter(enabled = true, onCancelClicked = {}, onSaveClicked = {})
}
