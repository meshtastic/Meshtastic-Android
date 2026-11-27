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

import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import org.meshtastic.sdk.LoRaFrequency

private val EU_TRIO = setOf(RegionCode.EU_868, RegionCode.EU_866, RegionCode.EU_N_868)
private val EU_868_PRESETS =
    setOf(
        ModemPreset.LONG_FAST,
        ModemPreset.LONG_SLOW,
        ModemPreset.MEDIUM_SLOW,
        ModemPreset.MEDIUM_FAST,
        ModemPreset.SHORT_SLOW,
        ModemPreset.SHORT_FAST,
        ModemPreset.LONG_MODERATE,
    )

/**
 * The region a broadcast target on [preset] actually runs in (firmware's `MeshBeaconModule::beaconRegionFor`). Within
 * the EU 868/866/N_868 trio, selecting a preset owned by a sibling runs the target in that sibling.
 */
internal fun beaconTargetRegion(region: RegionCode, preset: ModemPreset): RegionCode = when {
    region !in EU_TRIO -> region
    preset == ModemPreset.LITE_FAST || preset == ModemPreset.LITE_SLOW -> RegionCode.EU_866
    preset == ModemPreset.NARROW_FAST || preset == ModemPreset.NARROW_SLOW -> RegionCode.EU_N_868
    preset in EU_868_PRESETS -> RegionCode.EU_868
    else -> region
}

/**
 * How many frequency slots a broadcast target on [preset] can pin in [region]. The count follows bandwidth and
 * [beaconTargetRegion], so two presets in one region (or across the EU trio) can differ; 0 for a region the app does
 * not know.
 */
internal fun beaconTargetSlotCount(region: RegionCode, preset: ModemPreset): Int = Config.LoRaConfig.Builder()
    .also { wb ->
        wb.region = beaconTargetRegion(region, preset)
        wb.use_preset = true
        wb.modem_preset = preset
    }
    .build()
    .let { LoRaFrequency.plan(it, "").numChannels }

/**
 * Keeps [slot] when it is addressable on [preset] in [region], clearing it to `null` when out of range so firmware does
 * not skip the target on TX. Leaves [slot] untouched when the region's slot count is unknown (0).
 */
internal fun sanitizeBeaconTargetSlot(slot: Int?, region: RegionCode, preset: ModemPreset): Int? {
    if (slot == null) return null
    val slotCount = beaconTargetSlotCount(region, preset)
    return if (slotCount > 0 && slot !in 1..slotCount) null else slot
}

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
        wb.frequency_slot = sanitizeBeaconTargetSlot(target.frequency_slot, region, preset ?: currentPreset)
    }
    .build()

/**
 * Returns [storedSlot] when it lies outside 1..[slotCount], so the caller can keep it selected as a disabled fallback
 * item (design#140's never-render-blank rule); null when unset or addressable.
 */
internal fun beaconTargetSlotFallback(storedSlot: Int?, slotCount: Int): Int? = storedSlot?.takeUnless {
    it in 1..slotCount
}
