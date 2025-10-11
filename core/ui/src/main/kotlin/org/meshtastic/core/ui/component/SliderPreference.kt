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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.math.roundToInt

@Composable
fun <T> SliderPreference(
    title: String,
    enabled: Boolean,
    items: List<Pair<T, String>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    if (items.isEmpty()) return

    val selectedIndex = items.indexOfFirst { it.first == selectedValue }.toFloat()
    val valueRange = 0f..(items.size - 1).toFloat()
    val steps = (items.size - 2).coerceAtLeast(0)

    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                text = items.firstOrNull { it.first == selectedValue }?.second ?: items.first().second,
            )
        },
        overlineContent = { Text(text = title) },
        supportingContent = {
            Column {
                summary?.let { Text(text = it, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
                Slider(
                    value = selectedIndex.coerceIn(valueRange),
                    onValueChange = {
                        val index = it.roundToInt()
                        if (index in items.indices) {
                            onValueChange(items[index].first)
                        }
                    },
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                )
            }
        },
    )
}

@Suppress("MagicNumber")
@Preview(showBackground = true)
@Composable
private fun SliderPreferencePreview() {
    val items = listOf(1L to "One", 2L to "Two", 3L to "Three", 4L to "Four", 5L to "Five")
    AppTheme {
        SliderPreference(
            title = "Slider",
            summary = "Select a value",
            enabled = true,
            items = items,
            selectedValue = 3L,
            onValueChange = {},
        )
    }
}

@Suppress("MagicNumber")
@Preview(showBackground = true)
@Composable
private fun SliderPreferenceDisabledPreview() {
    val items = listOf(1L to "One", 2L to "Two", 3L to "Three", 4L to "Four", 5L to "Five")
    AppTheme {
        SliderPreference(
            title = "Slider",
            summary = "Select a value",
            enabled = false,
            items = items,
            selectedValue = 3L,
            onValueChange = {},
        )
    }
}
