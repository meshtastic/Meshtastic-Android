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
import org.meshtastic.core.model.util.isChannelPlaceholder
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
 * Matches [offerChannel] to its true `channel_index` among [selectableChannels] by name and PSK (a channel's identity
 * for beacon purposes); null when [offerChannel] is unset, names a channel the radio no longer has, or only matches a
 * placeholder slot excluded from [selectableChannels] (design#140 Q2) -- either way the caller falls back to showing it
 * as a stale disabled item rather than a spuriously-selected placeholder.
 */
internal fun beaconOfferChannelIndex(
    offerChannel: ChannelSettings?,
    selectableChannels: List<Pair<Int, ChannelSettings>>,
): Int? {
    if (offerChannel == null) return null
    return selectableChannels
        .firstOrNull { (_, settings) -> settings.name == offerChannel.name && settings.psk == offerChannel.psk }
        ?.first
}

/**
 * Applies design#140's save-time invariants to [form] before it is written. When [radioLora] uses a standard modem
 * preset (`use_preset = true`): the radio's own region (behavior 1) and configured preset (behavior 4) are always
 * stamped, never user-chosen; every broadcast target's region is kept in lockstep; and an untouched offered channel
 * defaults to the radio's primary channel (behavior 3's required-channel rule), while an already-set offered channel
 * (even one that no longer matches a radio channel) is kept. Only the offered channel's name and PSK are carried over,
 * never the radio's own channel_index/id/uplink/downlink/module flags, which have no meaning for a channel someone
 * else's radio is being invited to join.
 *
 * When the radio uses custom LoRa parameters (`use_preset = false`), `modem_preset` is meaningless, so every broadcast
 * field instead carries over from [stored] verbatim: stamping a stale preset would mint a live on-air lie about what
 * the mesh actually runs, and firmware transmits these fields with no validation against the radio's own live
 * parameters. Only the flags (listen/broadcast) come from [form]; the editor itself only allows turning broadcast off
 * in this state, never on.
 */
internal fun stampBeaconConfigForSave(
    form: MeshBeaconConfig,
    stored: MeshBeaconConfig,
    radioLora: Config.LoRaConfig,
    channelList: List<ChannelSettings>,
): MeshBeaconConfig = if (radioLora.use_preset) {
    form.copy(
        broadcast_offer_region = radioLora.region,
        broadcast_offer_preset = radioLora.modem_preset,
        broadcast_offer_channel =
        (form.broadcast_offer_channel ?: channelList.getOrNull(0))?.let {
            ChannelSettings(name = it.name, psk = it.psk)
        },
        broadcast_targets = form.broadcast_targets.map { it.copy(region = radioLora.region) },
    )
} else {
    form.copy(
        broadcast_message = stored.broadcast_message,
        broadcast_interval_secs = stored.broadcast_interval_secs,
        broadcast_offer_region = stored.broadcast_offer_region,
        broadcast_offer_preset = stored.broadcast_offer_preset,
        broadcast_offer_channel = stored.broadcast_offer_channel,
        broadcast_targets = stored.broadcast_targets,
    )
}

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

/** The three broadcast-half gating decisions design#140 Q1 hangs off `radioLora.use_preset` and the STORED flag. */
internal data class BeaconBroadcastGate(
    val sectionsVisible: Boolean,
    val sectionsEnabled: Boolean,
    val toggleEnabled: Boolean,
)

/**
 * Resolves [BeaconBroadcastGate] for the BROADCAST half of the editor (design#140 Q1). Keyed off
 * [storedBroadcastEnabled] (the flag as last saved), not any live form value, so an in-session toggle-off is always
 * recoverable: sections stay visible (but non-editable) whenever the radio was already broadcasting, and only fully
 * editable when the radio uses a standard modem preset.
 */
internal fun beaconBroadcastGate(
    connected: Boolean,
    useStandardPreset: Boolean,
    storedBroadcastEnabled: Boolean,
): BeaconBroadcastGate {
    val visible = useStandardPreset || storedBroadcastEnabled
    return BeaconBroadcastGate(
        sectionsVisible = visible,
        sectionsEnabled = connected && useStandardPreset,
        toggleEnabled = connected && visible,
    )
}

/**
 * A beacon needs a channel to offer (behavior 3) and, when broadcasting, an interval of at least one hour; both are
 * moot when the broadcast half is gated off (design#140 Q1), since save then preserves the stored broadcast fields
 * verbatim regardless of the live channel list or interval.
 */
internal fun meshBeaconSaveEnabled(
    connected: Boolean,
    useStandardPreset: Boolean,
    intervalValid: Boolean,
    hasChannels: Boolean,
): Boolean = connected && (!useStandardPreset || (intervalValid && hasChannels))

/**
 * Filters [channelList] down to the slots a beacon picker should offer (design#140 Q2), pairing each with its true
 * `channel_index` so selection keeps firmware slot semantics. Index 0 (primary) is always kept, even blank: a
 * blank-name, zero-psk primary is a legal cleartext channel, not padding. Index >= 1 is kept only when it is not
 * [isChannelPlaceholder], matching firmware's own slot-liveness test so the picker shows exactly what firmware will
 * accept. Placeholders otherwise render as duplicate fake preset names (several identical "LongFast" rows) via
 * `Channel.name`'s empty-name fallback, not as blank rows.
 */
internal fun selectableBeaconChannels(channelList: List<ChannelSettings>): List<Pair<Int, ChannelSettings>> =
    channelList
        .withIndex()
        .filter { (index, settings) -> index == 0 || !settings.isChannelPlaceholder() }
        .map { it.index to it.value }
