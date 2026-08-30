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

import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.model.constraintFor
import org.meshtastic.feature.settings.util.FixedUpdateIntervals
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

/**
 * Presets safe to offer a beacon when the firmware gave no region legality map (design#140 behavior 2): no
 * `minFirmware` gate, not deprecated, and a bandwidth narrow enough (<=250 kHz) to fit any region's allocation,
 * including the narrowest EU bands. Never the raw, unconstrained preset list.
 */
internal val CONSERVATIVE_BEACON_PRESETS =
    listOf(
        ModemPreset.LONG_FAST,
        ModemPreset.LONG_MODERATE,
        ModemPreset.MEDIUM_SLOW,
        ModemPreset.MEDIUM_FAST,
        ModemPreset.SHORT_SLOW,
        ModemPreset.SHORT_FAST,
    )

/**
 * Resolves the preset constraint a beacon's target rows should be filtered by: the firmware's advertised region->preset
 * map when it has one, otherwise [CONSERVATIVE_BEACON_PRESETS]. Unlike [constraintFor] alone, this never returns null,
 * so a beacon row is never left showing the full unconstrained preset list.
 */
internal fun beaconPresetConstraint(
    regionPresetMap: LoRaRegionPresetMap?,
    region: Config.LoRaConfig.RegionCode,
): RegionPresetConstraint = regionPresetMap.constraintFor(region)
    ?: RegionPresetConstraint(
        presets = CONSERVATIVE_BEACON_PRESETS,
        defaultPreset = ModemPreset.LONG_FAST,
        licensedOnly = false,
    )

/**
 * Returns [storedSeconds] when it matches none of [allowed], so the caller can show it as a disabled fallback item
 * (design#140's never-render-blank rule); null when the stored value is already one of the allowed intervals.
 */
internal fun beaconIntervalFallback(storedSeconds: Long, allowed: List<FixedUpdateIntervals>): Long? =
    storedSeconds.takeUnless { seconds -> allowed.any { it.value == seconds } }

/**
 * Matches [offerChannel] to its index in [channelList] by name and PSK (a channel's identity for beacon purposes); null
 * when [offerChannel] is unset or names a channel the radio no longer has.
 */
internal fun beaconOfferChannelIndex(offerChannel: ChannelSettings?, channelList: List<ChannelSettings>): Int? {
    if (offerChannel == null) return null
    val index = channelList.indexOfFirst { it.name == offerChannel.name && it.psk == offerChannel.psk }
    return index.takeIf { it >= 0 }
}

/**
 * Applies design#140's save-time invariants to [config] before it is written: the radio's own region (behavior 1) and
 * configured preset (behavior 4) are always stamped, never user-chosen; every broadcast target's region is kept in
 * lockstep; and an untouched offered channel defaults to the radio's primary channel (behavior 3's required- channel
 * rule), while an already-set offered channel (even one that no longer matches a radio channel) is kept. Only the
 * offered channel's name and PSK are carried over, never the radio's own channel_index/id/uplink/downlink/module flags,
 * which have no meaning for a channel someone else's radio is being invited to join.
 */
internal fun stampBeaconConfigForSave(
    config: MeshBeaconConfig,
    radioLora: Config.LoRaConfig,
    channelList: List<ChannelSettings>,
): MeshBeaconConfig = config.copy(
    broadcast_offer_region = radioLora.region,
    broadcast_offer_preset = radioLora.modem_preset,
    broadcast_offer_channel =
    (config.broadcast_offer_channel ?: channelList.getOrNull(0))?.let {
        ChannelSettings(name = it.name, psk = it.psk)
    },
    broadcast_targets = config.broadcast_targets.map { it.copy(region = radioLora.region) },
)

/**
 * Applies a channel pick to one broadcast target row (design#140 behavior 7): the channel index is always set, and the
 * radio's currently-configured preset is preselected only the first time the row gets a channel, never overwriting a
 * preset the user already chose.
 */
internal fun selectBeaconTargetChannel(
    target: MeshBeaconConfig.BroadcastTarget,
    channelIndex: Int,
    currentPreset: ModemPreset,
): MeshBeaconConfig.BroadcastTarget = target.copy(channel_index = channelIndex, preset = target.preset ?: currentPreset)
