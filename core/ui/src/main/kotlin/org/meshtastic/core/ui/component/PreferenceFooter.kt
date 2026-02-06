/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Deprecated(message = "Use overload that accepts Strings for button text.")
@Composable
fun PreferenceFooter(
    enabled: Boolean,
    negativeText: StringResource,
    onNegativeClicked: () -> Unit,
    positiveText: StringResource,
    onPositiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceFooter(
        modifier = modifier,
        enabled = enabled,
        negativeText = stringResource(negativeText),
        onNegativeClicked = onNegativeClicked,
        positiveText = stringResource(positiveText),
        onPositiveClicked = onPositiveClicked,
    )
}

@Composable
fun PreferenceFooter(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    negativeText: String? = null,
    onNegativeClicked: () -> Unit = {},
    positiveText: String? = null,
    onPositiveClicked: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (negativeText != null) {
            ElevatedButton(
                modifier = Modifier.height(48.dp).weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(),
                onClick = onNegativeClicked,
            ) {
                Text(text = negativeText)
            }
        }
        if (positiveText != null) {
            ElevatedButton(
                modifier = Modifier.height(48.dp).weight(1f),
                colors = ButtonDefaults.buttonColors(),
                onClick = { if (enabled) onPositiveClicked() },
            ) {
                Text(text = positiveText)
            }
        }
    }
}
