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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.discovery_lora_presets
import org.meshtastic.core.resources.discovery_lora_presets_description
import org.meshtastic.core.resources.discovery_preset_home_label
import org.meshtastic.core.resources.discovery_stat_selected
import org.meshtastic.core.resources.discovery_stat_unselected
import org.meshtastic.core.resources.mesh_beacon_preset_indicator
import org.meshtastic.core.ui.icon.CellTower
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Suppress("MagicNumber")
private val CHIP_SPACING = 8.dp
private val CARD_PADDING = 16.dp

/** Formats a [ChannelOption] enum name (e.g. "LONG_FAST") into a human-readable label (e.g. "Long Fast"). */
internal fun ChannelOption.displayName(): String =
    name.split("_").joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }

/** Deprecated modem presets that should not appear in the discovery picker. */
private val DEPRECATED_PRESETS = setOf(ChannelOption.VERY_LONG_SLOW, ChannelOption.LONG_SLOW)

/** A card containing a [FlowRow] of [FilterChip] items for preset selection. */
@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetPickerCard(
    selectedPresets: Set<ChannelOption>,
    homePreset: ChannelOption,
    onTogglePreset: (ChannelOption) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    beaconPresets: Set<ChannelOption> = emptySet(),
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PADDING)) {
            Text(
                text = stringResource(Res.string.discovery_lora_presets),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.discovery_lora_presets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CHIP_SPACING),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
                verticalArrangement = Arrangement.spacedBy(CHIP_SPACING),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChannelOption.entries
                    .filter { it !in DEPRECATED_PRESETS }
                    .forEach { preset ->
                        val selected = preset in selectedPresets
                        val isHome = preset == homePreset
                        val label =
                            if (isHome) {
                                stringResource(Res.string.discovery_preset_home_label, preset.displayName())
                            } else {
                                preset.displayName()
                            }
                        val selectedDesc = stringResource(Res.string.discovery_stat_selected)
                        val unselectedDesc = stringResource(Res.string.discovery_stat_unselected)
                        val fromBeacon = preset in beaconPresets
                        val beaconDesc = stringResource(Res.string.mesh_beacon_preset_indicator)
                        FilterChip(
                            selected = selected,
                            onClick = { onTogglePreset(preset) },
                            label = { Text(label) },
                            enabled = enabled,
                            modifier =
                            Modifier.semantics {
                                stateDescription = if (selected) selectedDesc else unselectedDesc
                            },
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
                            // A tower badge marks presets a nearby beacon advertised (Apple 014-mesh-beacons FR-004).
                            trailingIcon =
                            if (fromBeacon) {
                                {
                                    Icon(
                                        imageVector = MeshtasticIcons.CellTower,
                                        contentDescription = beaconDesc,
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
