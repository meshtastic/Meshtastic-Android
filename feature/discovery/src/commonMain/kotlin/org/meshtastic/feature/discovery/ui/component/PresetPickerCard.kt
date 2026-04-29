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
package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Suppress("MagicNumber")
private val CHIP_SPACING = 8.dp
private val CARD_PADDING = 16.dp

/** Formats a [ChannelOption] enum name (e.g. "LONG_FAST") into a human-readable label (e.g. "Long Fast"). */
internal fun ChannelOption.displayName(): String =
    name.split("_").joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

/** A card containing a [FlowRow] of [FilterChip] items for preset selection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetPickerCard(
    selectedPresets: Set<ChannelOption>,
    homePreset: ChannelOption,
    onTogglePreset: (ChannelOption) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PADDING)) {
            Text(text = "LoRa Presets", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Select one or more presets to scan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CHIP_SPACING),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
                verticalArrangement = Arrangement.spacedBy(CHIP_SPACING),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChannelOption.entries.forEach { preset ->
                    val selected = preset in selectedPresets
                    val isHome = preset == homePreset
                    FilterChip(
                        selected = selected,
                        onClick = { onTogglePreset(preset) },
                        label = { Text(if (isHome) "${preset.displayName()} (Home)" else preset.displayName()) },
                        enabled = enabled,
                        leadingIcon =
                        if (selected) {
                            {
                                Icon(
                                    imageVector = MeshtasticIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
