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
package org.meshtastic.core.model

import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaRegionPresetMap

/**
 * The modem presets the firmware advertised as legal for one LoRa region, decoded from a [LoRaRegionPresetMap].
 *
 * @property presets the presets that are legal in the region.
 * @property defaultPreset the firmware's default preset for the region (always one of [presets]).
 * @property licensedOnly true when the region's presets are for licensed operators only (e.g. amateur bands); the whole
 *   group is gated, not individual presets.
 */
data class RegionPresetConstraint(
    val presets: List<ModemPreset>,
    val defaultPreset: ModemPreset,
    val licensedOnly: Boolean,
) {
    /** True when an operator with the given licensing state may not select any preset in this region. */
    fun isGated(isLicensed: Boolean): Boolean = licensedOnly && !isLicensed
}

/**
 * Resolves the [RegionPresetConstraint] the firmware advertised for [region], or `null` when there is no constraint
 * information and the client must therefore NOT restrict the preset list. A `null` result happens when:
 * - the map is `null` (firmware older than 2.8 never sends it),
 * - [region] is absent from `region_groups` (no firmware table entry — treated as unconstrained),
 * - the referenced `group_index` is out of range (defensive against a malformed map), or
 * - the referenced group has no presets (a degenerate/malformed group — must not collapse the picker to nothing).
 */
@Suppress("ReturnCount") // Guard clauses for defensive null checks and missing lookups are idiomatic
fun LoRaRegionPresetMap?.constraintFor(region: RegionCode): RegionPresetConstraint? {
    if (this == null) return null
    val entry = region_groups.firstOrNull { it.region == region } ?: return null
    val group = groups.getOrNull(entry.group_index)?.takeIf { it.presets.isNotEmpty() } ?: return null
    return RegionPresetConstraint(
        presets = group.presets,
        defaultPreset = group.default_preset,
        licensedOnly = group.licensed_only,
    )
}

/**
 * Returns the modem preset the LoRa form should hold for [region] given the currently-selected [current] preset.
 *
 * Keeps [current] when it is still legal in [region]; otherwise falls back to the region's default preset (and, if that
 * is somehow not in the legal set, the first legal preset). Returns [current] unchanged when [region] is unconstrained.
 * Licensing is intentionally NOT considered here — auto-swap is about legality; selectability is decided separately by
 * [RegionPresetConstraint.isGated].
 */
fun LoRaRegionPresetMap?.repairPresetFor(region: RegionCode, current: ModemPreset): ModemPreset {
    val constraint = constraintFor(region) ?: return current
    return when {
        current in constraint.presets -> current
        constraint.defaultPreset in constraint.presets -> constraint.defaultPreset
        else -> constraint.presets.firstOrNull() ?: current
    }
}
