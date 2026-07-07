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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.meshtastic.core.model.Channel as ModelChannel

private const val SECONDS_TO_MILLIS = 1000L

// Firmware channel files expose eight slots: one primary plus up to seven secondary channels.
private const val CHANNEL_REPLACEMENT_SLOT_COUNT = 8

// Full channel replacement writes need conservative settle windows so hardware can persist each slot.
private val CHANNEL_REPLACEMENT_WRITE_DELAY = 1.seconds
private val LORA_CONFIG_SETTLE_DELAY = 2.seconds

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
 * @param minimumSlotCount The minimum slot count to emit. Full replacement callers can use this to disable firmware
 *   slots even when the local cache is stale or shorter than the radio's actual channel list.
 * @param maximumSlotCount The maximum slot count to emit. Full replacement callers use this to avoid unsupported
 *   firmware channel indices even if an imported or cached list is longer than expected.
 * @return A [Channel] list covering every slot the radio needs written to materialize [new] and clear leftover slots.
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
    for (i in 0..endIndex) {
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
 * Normalizes an imported REPLACE-mode [ChannelSettings] list so firmware only materializes real, distinct channels.
 *
 * Imported replacement sets can carry blank placeholder secondaries (trailing empty [ChannelSettings] padding) and
 * semantic duplicates (two slots resolving to the same effective channel under the active LoRa preset). Both produce
 * invalid LongFast-looking slots on the radio that cause route failures (`QueueStatus res=6` / `routeErr=6`).
 * - Slot 0 (primary) is always preserved as-is, even if blank (a blank primary is a deliberate disable signal).
 * - A blank placeholder primary does not participate in duplicate tracking.
 * - Blank placeholder secondaries (no name AND no PSK) are dropped.
 * - Semantic duplicates (same effective name + effective PSK as an earlier kept slot) are dropped.
 * - Remaining valid secondaries compact into sequential slots 1..n.
 *
 * @param settings Raw imported settings list.
 * @param loraConfig Active LoRa config used to resolve effective channel identity. Null falls back to defaults.
 * @return Compacted, deduplicated list safe to write to the radio.
 */
fun normalizeReplacementSettings(
    settings: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig?,
): List<ChannelSettings> {
    if (settings.size <= 1) return settings
    val effectiveLora = loraConfig ?: Config.LoRaConfig()
    val primary = settings.first()
    val seen = mutableSetOf<ChannelIdentity>()
    if (!primary.isPlaceholder()) {
        seen.add(primary.channelIdentity(effectiveLora))
    }
    val compact = mutableListOf(primary)
    for (index in 1..settings.lastIndex) {
        val candidate = settings[index]
        val identity = if (candidate.isPlaceholder()) null else candidate.channelIdentity(effectiveLora)
        if (identity != null && seen.add(identity)) {
            compact.add(candidate)
        }
    }
    return compact
}

/** True when a [ChannelSettings] carries no name and no PSK — a placeholder, not an intended channel. */
private fun ChannelSettings.isPlaceholder(): Boolean = name.isNullOrBlank() && psk.size == 0

/**
 * Applies an imported [ChannelSet] as an authoritative replacement to the radio and local cache.
 *
 * Reads the current LoRa config and channel set from [radioConfigRepository]'s flows (avoiding the StateFlow
 * placeholder window), builds the authoritative replacement list via [getChannelReplacementList], enqueues each channel
 * write to the radio via [radioController], pauses between writes so the radio can persist and reconfigure each slot,
 * then atomically replaces the local cached settings.
 *
 * setLocalChannel returns once the packet is enqueued, not after firmware ACK. The pacing avoids enqueueing a complete
 * channel replacement plus LoRa reconfiguration faster than real hardware can materialize the later channel slots. If
 * the sequence is interrupted after one or more successful writes, the local cache is reconciled to the successfully
 * enqueued channel settings before the original cancellation or failure continues.
 *
 * Imported settings are normalized via [normalizeReplacementSettings] before any write or bounds check, so blank
 * placeholder secondaries and semantic duplicates never reach the radio or the local cache.
 *
 * Does NOT handle LoRa config — callers are responsible for comparing and sending `lora_config` if present.
 *
 * @param channelSet The imported [ChannelSet] to apply as a replacement.
 * @param radioController The [RadioController] used to enqueue channel writes.
 * @param radioConfigRepository The [RadioConfigRepository] providing the current channel flow and cache.
 * @param writeDelay Delay after each channel write. Exposed for fast unit tests.
 * @param delayFn Delay implementation. Exposed for fast unit tests.
 * @return The device's current LoRa config snapshot used by callers to compare against an imported LoRa config.
 */
suspend fun applyReplacementChannelSet(
    channelSet: ChannelSet,
    radioController: RadioController,
    radioConfigRepository: RadioConfigRepository,
    writeDelay: Duration = CHANNEL_REPLACEMENT_WRITE_DELAY,
    delayFn: suspend (Duration) -> Unit = { delay(it) },
): Config.LoRaConfig? {
    // Resolve the LoRa preset used for semantic identity: prefer the imported config, fall back to the device's current
    // local config so duplicate detection stays correct when the import omits lora_config (e.g. a non-default preset).
    val currentLoraConfig = radioConfigRepository.localConfigFlow.first().lora
    val identityLoraConfig = channelSet.lora_config ?: currentLoraConfig
    val normalizedSettings = normalizeReplacementSettings(channelSet.settings, identityLoraConfig)
    require(normalizedSettings.size <= CHANNEL_REPLACEMENT_SLOT_COUNT) {
        "Imported channel set exceeds supported channel slot count"
    }
    val currentSettings = radioConfigRepository.channelSetFlow.first().settings
    val replacements =
        getChannelReplacementList(
            new = normalizedSettings,
            currentSettings = currentSettings,
            minimumSlotCount = CHANNEL_REPLACEMENT_SLOT_COUNT,
            maximumSlotCount = CHANNEL_REPLACEMENT_SLOT_COUNT,
        )
    Logger.i {
        "Applying imported channel replacement writes=${replacements.size} " +
            "importedSettings=${channelSet.settings.size} normalizedSettings=${normalizedSettings.size}"
    }
    val appliedSettings = currentSettings.take(CHANNEL_REPLACEMENT_SLOT_COUNT).toMutableList()
    var appliedWriteCount = 0
    var replacementComplete = false
    try {
        for (channel in replacements) {
            Logger.i {
                "Writing imported channel index=${channel.index} role=${channel.role} " +
                    "hasName=${channel.settings?.name?.isNotBlank() == true}"
            }
            radioController.setLocalChannel(channel)
            while (appliedSettings.size <= channel.index) {
                appliedSettings.add(ChannelSettings())
            }
            appliedSettings[channel.index] =
                if (channel.role == Channel.Role.DISABLED) {
                    ChannelSettings()
                } else {
                    channel.settings ?: ChannelSettings()
                }
            appliedWriteCount++
            delayFn(writeDelay)
        }
        replacementComplete = true
    } finally {
        if (!replacementComplete) {
            radioConfigRepository.reconcileInterruptedReplacement(
                appliedWriteCount = appliedWriteCount,
                totalWriteCount = replacements.size,
                appliedSettings = appliedSettings,
                normalizedSettings = normalizedSettings,
            )
        }
    }
    withContext(NonCancellable) { radioConfigRepository.replaceAllSettings(normalizedSettings) }
    return currentLoraConfig
}

private suspend fun RadioConfigRepository.reconcileInterruptedReplacement(
    appliedWriteCount: Int,
    totalWriteCount: Int,
    appliedSettings: List<ChannelSettings>,
    normalizedSettings: List<ChannelSettings>,
) {
    if (appliedWriteCount == 0) return
    val replacementSettings = if (appliedWriteCount == totalWriteCount) normalizedSettings else appliedSettings
    Logger.w {
        "Reconciling interrupted channel replacement appliedWrites=$appliedWriteCount totalWrites=$totalWriteCount"
    }
    withContext(NonCancellable) { replaceAllSettings(replacementSettings) }
}

/**
 * Applies an imported LoRa config after channel replacement writes have had time to settle.
 *
 * LoRa reconfiguration is expensive on firmware and can race with channel persistence if sent immediately after a full
 * channel replacement. The pre/post settle delays give the radio time to materialize the imported channels before and
 * after the LoRa write.
 */
suspend fun applyImportedLoraConfigAfterChannelReplacement(
    importedLoraConfig: Config.LoRaConfig?,
    currentLoraConfig: Config.LoRaConfig?,
    radioController: RadioController,
    settleDelay: Duration = LORA_CONFIG_SETTLE_DELAY,
    delayFn: suspend (Duration) -> Unit = { delay(it) },
) {
    if (importedLoraConfig == null || currentLoraConfig == importedLoraConfig) return

    Logger.i { "Settling before imported LoRa config write" }
    delayFn(settleDelay)
    radioController.setLocalConfig(Config(lora = importedLoraConfig))
    Logger.i { "Settling after imported LoRa config write" }
    delayFn(settleDelay)
}

/**
 * Builds the filtered ADD-mode preview for QR import: existing channels followed by only the unique incoming channels.
 *
 * Incoming channels that are semantic duplicates (same effective name + effective PSK) of an existing or earlier
 * incoming channel are omitted from the preview. Unique incoming channels are appended in scanned order and selected by
 * default while firmware channel capacity remains; unique channels beyond [maxChannels] stay visible but unchecked.
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
        val shouldShow = !channel.isPlaceholder()
        val identity = if (shouldShow) channel.channelIdentity(loraConfig) else null
        // Omit blank placeholders and semantic duplicates entirely — they are not shown to the user.
        if (identity != null && seen.add(identity)) {
            previewSettings += channel
            val shouldSelect = remaining > 0
            previewSelections += shouldSelect
            if (shouldSelect) remaining--
        }
    }
    return ChannelAddPreview(settings = previewSettings, selections = previewSelections)
}

/** Filtered ADD-mode preview: the visible channel list paired with its default selections (always size-matched). */
data class ChannelAddPreview(val settings: List<ChannelSettings>, val selections: List<Boolean>)

/** Semantic channel identity based on effective name and effective PSK. */
private data class ChannelIdentity(val name: String, val psk: ByteString) {
    // Redact the effective PSK from auto-generated diagnostics so a cryptographic key never leaks
    // via toString() in exception messages, debug logs, or stack traces.
    override fun toString(): String = "ChannelIdentity(name=$name, psk=<redacted>)"
}

/** Resolves the [ChannelIdentity] of this [ChannelSettings] under the given [Config.LoRaConfig]. */
private fun ChannelSettings.channelIdentity(loraConfig: Config.LoRaConfig): ChannelIdentity {
    val channel = ModelChannel(settings = this, loraConfig = loraConfig)
    return ChannelIdentity(name = channel.name, psk = channel.psk)
}
