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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.util.channelIdentity
import org.meshtastic.core.model.util.isChannelPlaceholder
import org.meshtastic.core.model.util.toChannelReplacementPlan
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
 * Imports a [ChannelSet] as an authoritative REPLACE: writes every channel and — when present and actually different —
 * the imported LoRa config, all inside one [RadioController.editLocalSettings] transaction, then replaces the local
 * channel cache.
 *
 * Reads the current LoRa config and channel set from [radioConfigRepository]'s flows (avoiding the StateFlow
 * placeholder window) and builds the shared authoritative replacement plan. The edit-settings transaction defers disk
 * persistence, radio reload/reconfiguration, and reboot until the closing commit, so channels + LoRa land in a single
 * reboot with no per-slot reconfigure to pace against. (Firmware still writes each `set_channel` into its in-memory
 * channel table as it arrives — the transaction is not a full staging of channel state — but the expensive
 * persist/reload path runs once at commit.) Writing LoRa inside the same session mirrors `InstallProfileUseCase` and is
 * why the old pre/post settle delays are gone: the begin/commit boundary is the settle.
 *
 * The local channel cache is commit-shaped: transactional channel writes deliberately do not mirror per slot (see
 * `AdminControllerImpl.EditSettingsSession.setChannel`), and this function replaces the cached channel list once, after
 * the session succeeds — so an import interrupted before that point leaves the local channel cache untouched. The
 * post-commit [RadioConfigRepository.updateChannelSet] call updates the normalized settings and imported LoRa config
 * together; an import without LoRa preserves the cached LoRa config.
 *
 * Imported settings are normalized before any write or bounds check, so blank placeholder secondaries and semantic
 * duplicates never reach the radio or the local cache.
 *
 * @param channelSet The imported [ChannelSet] to apply as a replacement. Its `lora_config`, if present and different
 *   from the device's current LoRa config, is written inside the same transaction.
 * @param radioController The [RadioController] used to run the edit transaction.
 * @param radioConfigRepository The [RadioConfigRepository] providing the current channel/LoRa flows and cache.
 */
suspend fun importChannelSet(
    channelSet: ChannelSet,
    radioController: RadioController,
    radioConfigRepository: RadioConfigRepository,
) {
    val currentLoraConfig = radioConfigRepository.localConfigFlow.first().lora
    val currentSettings = radioConfigRepository.channelSetFlow.first().settings
    val replacementPlan =
        channelSet.toChannelReplacementPlan(currentSettings = currentSettings, fallbackLoraConfig = currentLoraConfig)
    // Only write LoRa when the import carries one that actually differs from the device — avoids a redundant
    // reconfigure.
    val importedLoraConfig = channelSet.lora_config?.takeIf { it != currentLoraConfig }
    Logger.i {
        "Applying imported channel replacement writes=${replacementPlan.channelWrites.size} " +
            "importedSettings=${channelSet.settings.size} " +
            "normalizedSettings=${replacementPlan.normalizedSettings.size} " +
            "writesLora=${importedLoraConfig != null}"
    }
    radioController.editLocalSettings {
        for (channel in replacementPlan.channelWrites) {
            Logger.i {
                "Writing imported channel index=${channel.index} role=${channel.role} " +
                    "hasName=${channel.settings?.name?.isNotBlank() == true}"
            }
            setChannel(channel)
        }
        importedLoraConfig?.let { setConfig(Config(lora = it)) }
    }
    withContext(NonCancellable) {
        radioConfigRepository.updateChannelSet(
            settingsList = replacementPlan.normalizedSettings,
            loraConfig = importedLoraConfig,
        )
    }
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
        val shouldShow = !channel.isChannelPlaceholder()
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
