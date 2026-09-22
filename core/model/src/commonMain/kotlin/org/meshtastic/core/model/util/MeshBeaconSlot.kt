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
package org.meshtastic.core.model.util

import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.numChannels
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.MeshBeacon

// Both ends of a Mesh Beacon's frequency slot: what an incoming offer says it sits on, and what an outgoing offer
// should say about us. A slot is 1-based to match LoRaConfig.channel_num and 0 means "not sent".

/**
 * The frequency slot this beacon advertises outright, or `null` when it advertises none and the slot must be derived
 * from the offered region, channel name and preset the way [LoRaConfig.channelNum] does.
 *
 * A mesh sends this only when derivation would produce the wrong answer — it pins a slot the offered name does not
 * hash to. A value outside [lora]'s slot count is unaddressable here, so it falls back to derivation rather than
 * refusing the join: that is the behaviour we had before the field existed, and the offer is advisory either way.
 */
internal fun MeshBeacon.advertisedFrequencySlot(lora: LoRaConfig): Int? =
    offer_frequency_slot?.takeIf { it in 1..lora.numChannels }

/**
 * The frequency slot to advertise alongside an outgoing offer, or `null` to leave the field unset.
 *
 * Firmware sends this on the air only where it differs from the slot a receiver derives from the offered region,
 * channel name and preset, so stamping the radio's real slot costs nothing until the two diverge. They diverge in
 * exactly the cases the field exists for: a radio pinned to an explicit `channel_num`, a region that mandates a slot,
 * or an offered channel whose name hashes somewhere other than where the radio actually sits. Without this the radio
 * advertises a frequency it is not on, and a client that joins hears nothing.
 */
fun beaconOfferFrequencySlot(
    offerChannel: ChannelSettings?,
    primaryChannel: ChannelSettings?,
    radioLora: LoRaConfig,
): Int? {
    if (offerChannel == null || radioLora.numChannels <= 0) return null
    val actual = Channel(primaryChannel ?: ChannelSettings.Builder().build(), radioLora).channelNum
    // Derive the way a receiver must: off the offered channel's name with our own pin removed.
    val derived = Channel(offerChannel, radioLora.newBuilder().also { wb -> wb.channel_num = 0 }.build()).channelNum
    return actual.takeIf { it != derived }
}
