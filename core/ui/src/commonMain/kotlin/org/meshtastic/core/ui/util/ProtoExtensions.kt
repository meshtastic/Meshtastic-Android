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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import okio.ByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.unknown_age
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import kotlin.time.Duration.Companion.days
import org.meshtastic.core.model.Channel as ModelChannel

private const val SECONDS_TO_MILLIS = 1000L

@Composable
fun Position.formatPositionTime(): String {
    val currentTime = nowMillis
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = time * SECONDS_TO_MILLIS < sixMonthsAgo
    val timeText =
        if (isOlderThanSixMonths) {
            stringResource(Res.string.unknown_age)
        } else {
            DateFormatter.formatDateTime(time * SECONDS_TO_MILLIS)
        }
    return timeText
}

fun MeshPacket.toPosition(): Position? {
    val decoded = decoded ?: return null
    return if (decoded.want_response != true) {
        decoded.payload.let { runCatching { Position.ADAPTER.decode(it) }.getOrNull() }
    } else {
        null
    }
}

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists. Only changes are included in the
 * resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
fun getChannelList(new: List<ChannelSettings>, old: List<ChannelSettings>): List<Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) {
            add(
                Channel(
                    role =
                    when (i) {
                        0 -> Channel.Role.PRIMARY
                        in 1..new.lastIndex -> Channel.Role.SECONDARY
                        else -> Channel.Role.DISABLED
                    },
                    index = i,
                    settings = new.getOrNull(i) ?: ChannelSettings(),
                ),
            )
        }
    }
}

/**
 * Builds an authoritative [Channel] list for a full REPLACE import. Every position in [new] is emitted (PRIMARY at
 * index 0, SECONDARY for 1..new.lastIndex) and any trailing positions beyond [new]'s range are emitted as DISABLED so
 * the radio stops using them.
 *
 * Unlike [getChannelList], this does NOT skip positions where `currentSettings[i] == new[i]`: the imported set is
 * authoritative, the local cache must not gate the writes, and silent diff-skips during REPLACE were the source of
 * stale channels.
 *
 * [currentSettings] is consulted only for its size (to determine trailing DISABLED writes); its values are never
 * compared against [new]. Callers should read it from `radioConfigRepository.channelSetFlow.first().settings`, not from
 * a `stateInWhileSubscribed` StateFlow's `.value` — the StateFlow placeholder window can return an empty list and
 * suppress trailing DISABLED writes.
 *
 * Edge case: if [new] is empty, every emitted slot (including index 0) is DISABLED rather than wrongly promoting an
 * empty [ChannelSettings] to PRIMARY.
 *
 * @param new The imported [ChannelSettings] list. Every index becomes a write to the radio.
 * @param currentSettings The current [ChannelSettings] list. Only its size is used; trailing indices past [new] become
 *   DISABLED writes so leftover slots are cleared.
 * @return A [Channel] list covering every slot the radio needs written to materialize [new] and clear leftover slots.
 */
fun getChannelReplacementList(new: List<ChannelSettings>, currentSettings: List<ChannelSettings>): List<Channel> =
    buildList {
        for (i in 0..maxOf(currentSettings.lastIndex, new.lastIndex)) {
            add(
                Channel(
                    role =
                    when (i) {
                        // Empty-new is a degenerate import: every slot (including 0) must be DISABLED.
                        0 -> if (new.isEmpty()) Channel.Role.DISABLED else Channel.Role.PRIMARY

                        in 1..new.lastIndex -> Channel.Role.SECONDARY

                        else -> Channel.Role.DISABLED
                    },
                    index = i,
                    settings = new.getOrNull(i) ?: ChannelSettings(),
                ),
            )
        }
    }

/**
 * Applies an imported [ChannelSet] as an authoritative replacement to the radio and local cache.
 *
 * Reads the current channel set from [radioConfigRepository]'s flow (avoiding the StateFlow placeholder window), builds
 * the authoritative replacement list via [getChannelReplacementList], enqueues each channel write to the radio
 * sequentially via [radioController], then atomically replaces the local cached settings.
 *
 * setLocalChannel returns once the packet is enqueued, not after firmware ACK — firmware echoes via
 * MeshConfigHandlerImpl can still arrive after [radioConfigRepository.replaceAllSettings] and are tracked separately.
 *
 * Does NOT handle LoRa config — callers are responsible for comparing and sending `lora_config` if present.
 *
 * @param channelSet The imported [ChannelSet] to apply as a replacement.
 * @param radioController The [RadioController] used to enqueue channel writes.
 * @param radioConfigRepository The [RadioConfigRepository] providing the current channel flow and cache.
 */
suspend fun applyReplacementChannelSet(
    channelSet: ChannelSet,
    radioController: RadioController,
    radioConfigRepository: RadioConfigRepository,
) {
    val currentSettings = radioConfigRepository.channelSetFlow.first().settings
    for (channel in getChannelReplacementList(channelSet.settings, currentSettings)) {
        radioController.setLocalChannel(channel)
    }
    radioConfigRepository.replaceAllSettings(channelSet.settings)
}

/**
 * Builds the filtered ADD-mode preview for QR import: existing channels followed by only the unique incoming channels.
 *
 * Incoming channels that are semantic duplicates (same effective name + effective PSK) of an existing or earlier
 * incoming channel are omitted entirely from the preview — they are not shown to the user. Unique incoming channels are
 * appended in scanned order and selected by default while firmware channel capacity remains; unique channels beyond
 * [maxChannels] stay visible but unchecked.
 *
 * Semantic identity is resolved via the [Channel] domain model so preset/default channels match correctly across modem
 * presets: empty names resolve to the preset display name, and 1-byte PSK markers expand to the full default key.
 *
 * @param existing The current [ChannelSettings] list on the radio. Always shown, always selected.
 * @param incoming The imported [ChannelSettings] list. Duplicates omitted; uniques appended in order.
 * @param loraConfig The current [Config.LoRaConfig], used to resolve effective channel identity.
 * @param maxChannels Firmware channel limit. Unique incoming selections stop when this is reached.
 * @return A [ChannelAddPreview] whose [settings] and [selections] are aligned and size-matched.
 */
fun getChannelPreviewForAdd(
    existing: List<ChannelSettings>,
    incoming: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig,
    maxChannels: Int,
): ChannelAddPreview {
    val seen = existing.map { it.channelIdentity(loraConfig) }.toMutableSet()
    val previewSettings = existing.toMutableList()
    val previewSelections = MutableList(existing.size) { true }
    var remaining = (maxChannels - existing.size).coerceAtLeast(0)
    for (channel in incoming) {
        val identity = channel.channelIdentity(loraConfig)
        // Omit semantic duplicates entirely — they are not shown to the user.
        if (!seen.add(identity)) continue
        previewSettings += channel
        val shouldSelect = remaining > 0
        previewSelections += shouldSelect
        if (shouldSelect) remaining--
    }
    return ChannelAddPreview(settings = previewSettings, selections = previewSelections)
}

/** Filtered ADD-mode preview: the visible channel list paired with its default selections (always size-matched). */
data class ChannelAddPreview(val settings: List<ChannelSettings>, val selections: List<Boolean>)

/** Semantic channel identity based on effective name and effective PSK. */
private data class ChannelIdentity(val name: String, val psk: ByteString)

/** Resolves the [ChannelIdentity] of this [ChannelSettings] under the given [Config.LoRaConfig]. */
private fun ChannelSettings.channelIdentity(loraConfig: Config.LoRaConfig): ChannelIdentity {
    val channel = ModelChannel(settings = this, loraConfig = loraConfig)
    return ChannelIdentity(name = channel.name, psk = channel.psk)
}
