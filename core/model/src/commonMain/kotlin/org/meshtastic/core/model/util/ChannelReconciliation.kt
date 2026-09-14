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

import org.meshtastic.proto.ChannelSet

/** Where a broadcast conversation's messages live: a configured channel slot, or parked under a retired identity. */
sealed interface ConversationSlot {
    /** Slot [index] of the radio's channel set. */
    data class Live(val index: Int) : ConversationSlot

    /** No longer configured; parked under a `ChannelIdentity.token`. */
    data class Retired(val token: String) : ConversationSlot
}

/**
 * One conversation re-key. [displayName] is the effective channel name at the time of the change, kept so a retired
 * conversation can still be labelled once its slot belongs to someone else.
 */
data class ChannelKeyChange(val from: ConversationSlot, val to: ConversationSlot, val displayName: String)

/**
 * Works out how conversations must move when the radio's channel set changes from [old] to [new].
 *
 * Conversations are keyed by channel *index*, so a slot that changes occupant would otherwise show the previous
 * channel's messages under the new channel. Matching is on resolved [ChannelIdentity] — the effective name (a blank
 * name resolves to the modem preset's display name) and the expanded PSK (a one-byte marker expands to the full default
 * key) — which is the same notion [ChannelSettings.isAlreadyJoined] and [normalizeReplacementSettings] use, and the
 * same pair the firmware hashes into the on-air channel. So changing the modem preset *is* a channel change even though
 * neither raw field moved, and changing region alone is not.
 *
 * Every change has a distinct [ChannelKeyChange.from], so applying the list needs a single pass over the rows and a
 * slot swap needs no ordering care.
 *
 * @param retiredTokens tokens that currently hold parked conversations, so a re-added channel reclaims its history.
 * @return the moves, retirements and reclaims to apply; slots that keep their occupant produce nothing.
 */
fun planChannelReconciliation(
    old: ChannelSet,
    new: ChannelSet,
    retiredTokens: Set<String> = emptySet(),
): List<ChannelKeyChange> {
    val oldIdentities = old.identitiesBySlot()
    val newIdentities = new.identitiesBySlot()
    val newSlotsByToken = newIdentities.entries.sortedBy { it.key }.groupBy({ it.value.token }, { it.key })

    // Two slots can resolve to the same channel — a blank-named secondary carrying the one-byte default PSK has the
    // primary's identity — so pairing has to be one-to-one. A slot that still holds its own identity claims itself
    // before anything else moves, and every move then takes the lowest slot nobody has claimed yet. Without this a
    // duplicate would be moved on top of the conversation that rightfully owns the slot.
    val staysPut = oldIdentities.filterKeys { index -> newIdentities[index]?.token == oldIdentities[index]?.token }
    val claimedSlots = staysPut.keys.toMutableSet()

    // Two ways the new set can be unknowable rather than empty, and retiring against either would archive live
    // conversations: a null lora_config cannot resolve a blank channel name (it would read as "Custom"), and an empty
    // channel list means the radio's channels have not arrived yet — a real radio always has a primary. Moves still
    // apply; only the decision that a channel is *gone* waits for a set that can support it.
    val canRetire = new.lora_config != null && new.settings.isNotEmpty()

    val changes = mutableListOf<ChannelKeyChange>()
    for ((oldIndex, identity) in oldIdentities) {
        if (oldIndex in staysPut) continue
        val newIndex = newSlotsByToken[identity.token]?.firstOrNull { slot -> slot !in claimedSlots }
        val destination =
            when {
                newIndex != null -> ConversationSlot.Live(newIndex).also { claimedSlots += newIndex }
                canRetire -> ConversationSlot.Retired(identity.token)
                else -> null
            }
        if (destination != null) {
            changes += ChannelKeyChange(ConversationSlot.Live(oldIndex), destination, identity.name)
        }
    }

    val reclaimed = mutableSetOf<String>()
    for ((newIndex, identity) in newIdentities.entries.sortedBy { it.key }) {
        val token = identity.token
        // Only reclaim what is actually parked, once, and never into a slot another conversation already holds or is
        // moving into. Claiming the token last leaves it available to a later slot when this one is spoken for.
        if (token in retiredTokens && newIndex !in claimedSlots && reclaimed.add(token)) {
            claimedSlots += newIndex
            changes +=
                ChannelKeyChange(
                    from = ConversationSlot.Retired(token),
                    to = ConversationSlot.Live(newIndex),
                    displayName = identity.name,
                )
        }
    }
    return changes
}

/**
 * Resolved identity per occupied slot, or nothing at all when the LoRa config is unknown (a blank channel name is only
 * resolvable through the modem preset). Blank padding secondaries are skipped: a gap left by a removed channel is
 * padded with a bare `ChannelSettings`, and treating that as a channel would retire whatever sat there. Slot 0 is never
 * padding; it always represents a real channel.
 */
private fun ChannelSet.identitiesBySlot(): Map<Int, ChannelIdentity> {
    val lora = lora_config ?: return emptyMap()
    return settings
        .withIndex()
        .filter { (index, channel) -> index == 0 || !channel.isChannelPlaceholder() }
        .associate { (index, channel) -> index to channel.channelIdentity(lora) }
}
