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

import okio.ByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.core.model.Channel as ModelChannel

/** Firmware channel files expose eight slots: one primary plus up to seven secondary channels. */
const val CHANNEL_REPLACEMENT_SLOT_COUNT = 8

/** A normalized authoritative channel set and the complete radio writes needed to materialize it. */
data class ChannelReplacementPlan(val normalizedSettings: List<ChannelSettings>, val channelWrites: List<Channel>)

/**
 * Produces the canonical authoritative replacement plan used by every full channel-set import path.
 *
 * Normalization runs before the firmware-slot bound is checked, allowing padded exports to shed blank or duplicate
 * secondaries before validation. [requirePrimary] lets profile installation reject an empty decoded set while the
 * explicit QR replacement flow retains its existing ability to clear every slot.
 *
 * @param currentSettings The destination's current settings. Only the size is used when deciding which trailing slots
 *   must be disabled.
 * @param fallbackLoraConfig LoRa config used for semantic identity when this channel set does not carry one.
 * @param requirePrimary Whether an empty normalized set must be rejected.
 */
fun ChannelSet.toChannelReplacementPlan(
    currentSettings: List<ChannelSettings>,
    fallbackLoraConfig: Config.LoRaConfig?,
    requirePrimary: Boolean = false,
): ChannelReplacementPlan {
    val normalizedSettings = normalizeReplacementSettings(settings, lora_config ?: fallbackLoraConfig)
    require(!requirePrimary || normalizedSettings.isNotEmpty()) {
        "Imported channel set must contain a primary channel"
    }
    require(normalizedSettings.size <= CHANNEL_REPLACEMENT_SLOT_COUNT) {
        "Imported channel set exceeds supported channel slot count"
    }
    return ChannelReplacementPlan(
        normalizedSettings = normalizedSettings,
        channelWrites =
        getChannelReplacementList(
            new = normalizedSettings,
            currentSettings = currentSettings,
            minimumSlotCount = CHANNEL_REPLACEMENT_SLOT_COUNT,
            maximumSlotCount = CHANNEL_REPLACEMENT_SLOT_COUNT,
        ),
    )
}

/**
 * Builds an authoritative [Channel] list for a full replacement. Every position in [new] is emitted, and any trailing
 * positions beyond [new]'s range are emitted as disabled so the radio stops using them.
 *
 * Unlike a diff, this never skips settings already present in [currentSettings]. The imported set is authoritative, and
 * stale local cache entries must not suppress a radio write.
 */
fun getChannelReplacementList(
    new: List<ChannelSettings>,
    currentSettings: List<ChannelSettings>,
    minimumSlotCount: Int = 0,
    maximumSlotCount: Int = Int.MAX_VALUE,
): List<Channel> = buildList {
    require(minimumSlotCount <= maximumSlotCount) { "minimumSlotCount must be <= maximumSlotCount" }
    val minimumLastIndex = minimumSlotCount.coerceAtLeast(0) - 1
    val maximumLastIndex = maximumSlotCount.coerceAtLeast(0) - 1
    val endIndex = maxOf(currentSettings.lastIndex, new.lastIndex, minimumLastIndex).coerceAtMost(maximumLastIndex)
    if (endIndex < 0) return@buildList
    for (index in 0..endIndex) {
        add(
            Channel(
                role =
                when (index) {
                    0 -> if (new.isEmpty()) Channel.Role.DISABLED else Channel.Role.PRIMARY
                    in 1..new.lastIndex -> Channel.Role.SECONDARY
                    else -> Channel.Role.DISABLED
                },
                index = index,
                settings = new.getOrNull(index) ?: ChannelSettings(),
            ),
        )
    }
}

/**
 * Normalizes replacement settings so firmware only materializes real, distinct channels.
 *
 * Slot zero is preserved as-is. Blank placeholder secondaries and semantic duplicates under [loraConfig] are removed,
 * and the remaining secondaries compact into sequential slots.
 */
fun normalizeReplacementSettings(
    settings: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig?,
): List<ChannelSettings> {
    if (settings.size <= 1) return settings
    val effectiveLora = loraConfig ?: Config.LoRaConfig()
    val primary = settings.first()
    val seen = mutableSetOf<ChannelIdentity>()
    if (!primary.isChannelPlaceholder()) {
        seen.add(primary.channelIdentity(effectiveLora))
    }
    val compact = mutableListOf(primary)
    for (index in 1..settings.lastIndex) {
        val candidate = settings[index]
        val identity = if (candidate.isChannelPlaceholder()) null else candidate.channelIdentity(effectiveLora)
        if (identity != null && seen.add(identity)) compact.add(candidate)
    }
    return compact
}

/** True when these settings carry no name and no PSK, making them padding rather than an intended channel. */
fun ChannelSettings.isChannelPlaceholder(): Boolean = name.isNullOrBlank() && psk.size == 0

/** Semantic channel identity based on the effective name and effective PSK. */
data class ChannelIdentity(val name: String, val psk: ByteString) {
    // Never expose an effective PSK through diagnostics or an auto-generated data-class toString.
    override fun toString(): String = "ChannelIdentity(name=$name, psk=<redacted>)"
}

/** Resolves this setting's semantic identity under [loraConfig]. */
fun ChannelSettings.channelIdentity(loraConfig: Config.LoRaConfig): ChannelIdentity {
    val channel = ModelChannel(settings = this, loraConfig = loraConfig)
    return ChannelIdentity(name = channel.name, psk = channel.psk)
}
