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

import org.meshtastic.core.model.numChannels
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

/**
 * How many frequency slots a broadcast target on [preset] can pin in [region]. The count follows bandwidth, so two
 * presets in one region can differ; 0 for a region the app does not know.
 */
internal fun beaconTargetSlotCount(region: RegionCode, preset: ModemPreset): Int = Config.LoRaConfig.Builder()
    .also { wb ->
        wb.region = region
        wb.use_preset = true
        wb.modem_preset = preset
    }
    .build()
    .numChannels

/**
 * Applies a preset pick to one broadcast target row. A pinned `frequency_slot` the new effective preset (unset resolves
 * to [currentPreset]) cannot hold in [region] is cleared, since firmware skips a target whose pin is out of range.
 */
internal fun selectBeaconTargetPreset(
    target: MeshBeaconConfig.BroadcastTarget,
    preset: ModemPreset?,
    currentPreset: ModemPreset,
    region: RegionCode,
): MeshBeaconConfig.BroadcastTarget = target
    .newBuilder()
    .also { wb ->
        wb.preset = preset
        val slot = target.frequency_slot
        if (slot != null && slot > beaconTargetSlotCount(region, preset ?: currentPreset)) wb.frequency_slot = null
    }
    .build()

/**
 * Returns [storedSlot] when it lies outside 1..[slotCount], so the caller can keep it selected as a disabled fallback
 * item (design#140's never-render-blank rule); null when unset or addressable.
 */
internal fun beaconTargetSlotFallback(storedSlot: Int?, slotCount: Int): Int? =
    storedSlot?.takeUnless { it in 1..slotCount }
