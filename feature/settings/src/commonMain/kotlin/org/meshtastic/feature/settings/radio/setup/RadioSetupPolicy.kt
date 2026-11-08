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
package org.meshtastic.feature.settings.radio.setup

import org.meshtastic.core.model.presetForRegionChange
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaRegionPresetMap

/** The steps a radio gets. A licensed operator's long name comes from the call sign, so the name page is skipped. */
internal fun radioSetupSteps(isLicensed: Boolean): List<RadioSetupStep> =
    RadioSetupStep.entries.filterNot { it == RadioSetupStep.NAME && isLicensed }

/**
 * The LoRa config the region page writes when [region] is chosen: the region, and the preset [presetForRegionChange]
 * picks for it. Custom modem settings (`use_preset` off) are kept until the user picks a preset.
 */
internal fun Config.LoRaConfig.withSetupRegion(region: RegionCode, map: LoRaRegionPresetMap?): Config.LoRaConfig {
    val preset = map.presetForRegionChange(previousRegion = this.region, newRegion = region, current = modem_preset)
    return newBuilder()
        .also { wb ->
            wb.region = region
            wb.modem_preset = preset
        }
        .build()
}

/** Choosing a preset also leaves custom modem settings behind. */
internal fun Config.LoRaConfig.withSetupPreset(preset: ModemPreset): Config.LoRaConfig = newBuilder()
    .also { wb ->
        wb.modem_preset = preset
        wb.use_preset = true
    }
    .build()

/** At most one LoRa write, and only when the region, the preset or the preset mode changed. */
internal fun loraSetupChanged(stored: Config.LoRaConfig, edited: Config.LoRaConfig): Boolean =
    stored.region != edited.region ||
        stored.modem_preset != edited.modem_preset ||
        stored.use_preset != edited.use_preset
