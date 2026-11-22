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
package org.meshtastic.core.ui.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.Channel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add
import org.meshtastic.core.resources.bandwidth_option_khz
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.channel_already_on_radio
import org.meshtastic.core.resources.channel_import_add_description
import org.meshtastic.core.resources.channel_import_confirm_add
import org.meshtastic.core.resources.channel_import_confirm_replace
import org.meshtastic.core.resources.channel_import_downlink
import org.meshtastic.core.resources.channel_import_mode_label
import org.meshtastic.core.resources.channel_import_position_approximate
import org.meshtastic.core.resources.channel_import_position_precise
import org.meshtastic.core.resources.channel_import_replace_description
import org.meshtastic.core.resources.channel_import_title
import org.meshtastic.core.resources.channel_import_uplink
import org.meshtastic.core.resources.channel_slots_full
import org.meshtastic.core.resources.channel_slots_used
import org.meshtastic.core.resources.config_lora_coding_rate_fraction
import org.meshtastic.core.resources.dbm_value
import org.meshtastic.core.resources.default_
import org.meshtastic.core.resources.disabled
import org.meshtastic.core.resources.enabled
import org.meshtastic.core.resources.frequency_value_mhz
import org.meshtastic.core.resources.lora_change_spoken
import org.meshtastic.core.resources.lora_change_values
import org.meshtastic.core.resources.lora_changes_alert_description
import org.meshtastic.core.resources.lora_changes_alert_heading
import org.meshtastic.core.resources.lora_changes_heading
import org.meshtastic.core.resources.lora_value_none
import org.meshtastic.core.resources.lora_value_not_used
import org.meshtastic.core.resources.replace
import org.meshtastic.core.resources.slot_auto
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.icon.CloudDownload
import org.meshtastic.core.ui.icon.CloudUpload
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.getChannelPreviewForAdd
import org.meshtastic.proto.ChannelSet

@Composable
fun ScannedQrCodeDialog(
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    viewModel: ScannedQrCodeViewModel = koinViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val maxChannels by viewModel.maxChannels.collectAsStateWithLifecycle()

    ScannedQrCodeDialog(
        channels = channels,
        incoming = incoming,
        onDismiss = onDismiss,
        maxChannels = maxChannels,
        onConfirm = viewModel::setChannels,
    )
}

/**
 * Lets the user choose between adding the incoming channels to the radio and replacing the radio's channels and LoRa
 * settings, review what will change, and confirm. Choosing a mode changes nothing; only the confirm button imports.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ScannedQrCodeDialog(
    channels: ChannelSet,
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    maxChannels: Int = DEFAULT_MAX_CHANNELS,
    onConfirm: (ChannelSet) -> Unit,
) {
    var shouldReplace by rememberSaveable { mutableStateOf(incoming.lora_config != null) }

    // Freeze the channel limit for the dialog's lifetime so a late myNodeInfo emission (which updates
    // viewModel.maxChannels from the DEFAULT_MAX_CHANNELS seed to the real device value) cannot rebuild
    // addPreview/channelSelections and silently wipe the user's checkbox toggles.
    val effectiveMaxChannels = rememberSaveable { maxChannels }

    // Filtered ADD-mode preview: existing channels plus only the unique incoming channels. Duplicate incoming channels
    // are omitted entirely. REPLACE mode ignores this and uses the raw incoming set.
    val addPreview =
        remember(channels.settings, incoming.settings, channels.lora_config, effectiveMaxChannels) {
            getChannelPreviewForAdd(
                existing = channels.settings,
                incoming = incoming.settings,
                loraConfig = channels.lora_config ?: Channel.default.loraConfig,
                maxChannels = effectiveMaxChannels,
            )
        }

    val channelSet =
        remember(shouldReplace, channels, incoming, addPreview.settings) {
            if (shouldReplace) {
                // Keeps the device's own MQTT consent and TX power; loraChanges describes this exact config.
                incoming.forReplace(channels.lora_config)
            } else {
                channels.newBuilder().also { wb -> wb.settings = addPreview.settings }.build()
            }
        }

    val modemPresetName = Channel(loraConfig = channelSet.lora_config ?: Channel.default.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections =
        remember(shouldReplace, channelSet.settings, addPreview.selections) {
            val defaults =
                if (shouldReplace) {
                    List(channelSet.settings.size) { true }
                } else {
                    addPreview.selections
                }
            mutableStateListOf<Boolean>().apply { addAll(defaults) }
        }

    val selectedChannelSet =
        if (shouldReplace) {
            channelSet
                .newBuilder()
                .also { wb ->
                    wb.settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true }
                }
                .build()
        } else {
            channelSet
                .newBuilder()
                .also { wb ->
                    wb.settings =
                        channelSet.settings.filterIndexed { i, _ ->
                            // Primary (index 0) is always kept; existing secondaries can be dropped to free a slot for
                            // the incoming channel when the radio is full (Apple FR-017 "replace a secondary, never
                            // the primary").
                            i == 0 || channelSelections.getOrNull(i) == true
                        }
                }
                .build()
        }

    // Every LoRa setting whose effect differs from what channelSet will write, derived from that same config.
    val loraChanges =
        remember(shouldReplace, channels, channelSet) {
            val applied = channelSet.lora_config
            if (shouldReplace && applied != null) loraConfigChanges(channels.lora_config, applied) else emptyList()
        }

    // Follows the selection: the precision cap depends on which channel is written first.
    val writtenSelection =
        channelSet.settings.indices.map { (!shouldReplace && it == 0) || channelSelections.getOrNull(it) == true }
    val sharing = importedChannelSharing(channelSet.settings, writtenSelection)

    AlertDialog(
        onDismissRequest = onDismiss,
        // The platform's default dialog width is too narrow for a row of change text; keep Material's 24 dp margin.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = DIALOG_MARGIN),
        title = {
            Text(
                text = stringResource(Res.string.channel_import_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Box {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImportModeSelector(
                        replaceSelected = shouldReplace,
                        replaceAvailable = incoming.lora_config != null,
                        onReplaceChange = { shouldReplace = it },
                    )
                    Text(
                        text =
                        stringResource(
                            if (shouldReplace) {
                                Res.string.channel_import_replace_description
                            } else {
                                Res.string.channel_import_add_description
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    channelSet.settings.forEachIndexed { index, channel ->
                        val isPrimary = index == 0
                        val channelSharing = sharing.getOrNull(index)?.takeIf { it.isNotable }
                        ChannelSelection(
                            index = index,
                            title = channel.name.ifEmpty { modemPresetName },
                            // Primary is always kept. In ADD mode existing secondaries become deselectable so the user
                            // can drop one to make room for the incoming channel on a full radio; REPLACE keeps its
                            // prior all-selectable behavior.
                            enabled = if (shouldReplace) true else !isPrimary,
                            isSelected = if (!shouldReplace && isPrimary) true else channelSelections[index],
                            onSelected = {
                                if (it || selectedChannelSet.settings.size > 1) {
                                    channelSelections[index] = it
                                }
                            },
                            channel = Channel(channel, channelSet.lora_config ?: Channel.default.loraConfig),
                            supportingContent = channelSharing?.let { { ChannelSharingStatements(it) } },
                        )
                    }

                    if (!shouldReplace) {
                        val incomingLeftOut =
                            (channels.settings.size until channelSelections.size).any { !channelSelections[it] }
                        AddCapacitySummary(
                            selectedCount = selectedChannelSet.settings.size,
                            maxChannels = effectiveMaxChannels,
                            alreadyOnRadio = addPreview.alreadyOnRadio,
                            incomingLeftOut = incomingLeftOut,
                        )
                    }

                    if (shouldReplace && loraChanges.isNotEmpty()) {
                        LoraChangesSection(loraChanges)
                    }
                }
                // maxValue is Int.MAX_VALUE until the first layout; no divider is drawn on that unmeasured frame.
                val measured = scrollState.maxValue != Int.MAX_VALUE
                if (measured && scrollState.canScrollBackward) {
                    HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                }
                if (measured && scrollState.canScrollForward) {
                    HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
        confirmButton = {
            // Replace removes the radio's channels, so its confirm button takes the destructive color.
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(selectedChannelSet)
                },
                enabled = selectedChannelSet.settings.size in 1..effectiveMaxChannels,
                colors =
                ButtonDefaults.textButtonColors(
                    contentColor =
                    if (shouldReplace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    text =
                    stringResource(
                        if (shouldReplace) {
                            Res.string.channel_import_confirm_replace
                        } else {
                            Res.string.channel_import_confirm_add
                        },
                    ),
                )
            }
        },
    )
}

/** Replace or Add as one single-choice group; selecting a segment only changes the mode. */
@Composable
private fun ImportModeSelector(
    replaceSelected: Boolean,
    replaceAvailable: Boolean,
    onReplaceChange: (Boolean) -> Unit,
) {
    val groupLabel = stringResource(Res.string.channel_import_mode_label)
    // A segment is 40 dp tall by default, short of the 44 dp minimum touch target.
    val segmentModifier = Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().semantics { contentDescription = groupLabel }) {
        SegmentedButton(
            selected = replaceSelected,
            onClick = { onReplaceChange(true) },
            enabled = replaceAvailable,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = segmentModifier,
            label = { Text(text = stringResource(Res.string.replace)) },
        )
        SegmentedButton(
            selected = !replaceSelected,
            onClick = { onReplaceChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = segmentModifier,
            label = { Text(text = stringResource(Res.string.add)) },
        )
    }
}

/** Each thing a channel shares is its own statement, so each can be translated and read on its own. */
@Composable
private fun ChannelSharingStatements(sharing: ChannelSharing) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sharing.uplink) SharingStatement(MeshtasticIcons.CloudUpload, Res.string.channel_import_uplink)
        if (sharing.downlink) SharingStatement(MeshtasticIcons.CloudDownload, Res.string.channel_import_downlink)
        when (sharing.position) {
            PositionSharing.Approximate ->
                SharingStatement(MeshtasticIcons.LocationOn, Res.string.channel_import_position_approximate)

            PositionSharing.Precise ->
                SharingStatement(MeshtasticIcons.MyLocation, Res.string.channel_import_position_precise)

            PositionSharing.None -> Unit
        }
    }
}

@Composable
private fun SharingStatement(icon: ImageVector, text: StringResource) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The LoRa settings the import changes. Changes to sending and receiving sit in a named group first; the group's
 * heading says what it means, so the grouping never depends on color or position alone.
 */
@Composable
private fun LoraChangesSection(changes: List<LoraFieldChange>) {
    val (alerts, others) = changes.partition { it.affectsTransmission }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.lora_changes_heading),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        if (alerts.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.StatusYellow.copy(alpha = WARNING_CONTAINER_ALPHA),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MeshtasticIcons.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.lora_changes_alert_heading),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.lora_changes_alert_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ChangeList(alerts, firstIndex = 0, total = changes.size)
                }
            }
        }
        if (others.isNotEmpty()) {
            ChangeList(others, firstIndex = alerts.size, total = changes.size)
        }
    }
}

@Composable
private fun ChangeList(changes: List<LoraFieldChange>, firstIndex: Int, total: Int) {
    Column(
        modifier =
        Modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(rowCount = total, columnCount = 1) },
    ) {
        changes.forEachIndexed { offset, change -> ChangeRow(change, position = firstIndex + offset) }
    }
}

/** One change as a single statement: the label over "from → to", spoken as "label, from from to to". */
@Composable
private fun ChangeRow(change: LoraFieldChange, position: Int) {
    val label = stringResource(change.label)
    val from = change.from
    val to = change.to
    val values = if (from == null || to == null) null else loraValueText(from) to loraValueText(to)
    val spoken =
        values?.let { (fromText, toText) -> stringResource(Res.string.lora_change_spoken, label, fromText, toText) }
            ?: label
    Column(
        modifier =
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics {
            contentDescription = spoken
            collectionItemInfo =
                CollectionItemInfo(rowIndex = position, rowSpan = 1, columnIndex = 0, columnSpan = 1)
        },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        if (values != null) {
            Text(
                text = stringResource(Res.string.lora_change_values, values.first, values.second),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun loraValueText(value: LoraValue): String = when (value) {
    is LoraValue.Flag -> stringResource(if (value.on) Res.string.enabled else Res.string.disabled)

    is LoraValue.Label -> stringResource(value.res)

    is LoraValue.Text -> value.text

    is LoraValue.Count -> value.value.toString()

    is LoraValue.Megahertz -> stringResource(Res.string.frequency_value_mhz, value.text)

    is LoraValue.Kilohertz -> stringResource(Res.string.bandwidth_option_khz, value.text)

    is LoraValue.Dbm -> stringResource(Res.string.dbm_value, value.value)

    is LoraValue.CodingRate -> stringResource(Res.string.config_lora_coding_rate_fraction, value.denominator)

    is LoraValue.Nodes ->
        if (value.ids.isEmpty()) {
            stringResource(Res.string.lora_value_none)
        } else {
            // A word joiner after "!" keeps a node id on one line; without it the line may break between them.
            value.ids.joinToString(", ") { it.replace("!", "!$WORD_JOINER") }
        }

    LoraValue.Auto -> stringResource(Res.string.slot_auto)

    LoraValue.Default -> stringResource(Res.string.default_)

    LoraValue.NotUsed -> stringResource(Res.string.lora_value_not_used)
}

/**
 * Add never overwrites, so the operator sees the slot count, what was skipped, and what to do when the radio is full.
 */
@Composable
private fun AddCapacitySummary(selectedCount: Int, maxChannels: Int, alreadyOnRadio: Int, incomingLeftOut: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stringResource(Res.string.channel_slots_used, selectedCount, maxChannels),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (alreadyOnRadio > 0) {
            Text(
                text = pluralStringResource(Res.plurals.channel_already_on_radio, alreadyOnRadio, alreadyOnRadio),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Over the limit, uncheck the excess; at the limit with a new channel left out, one more slot is needed.
        val toUncheck =
            when {
                selectedCount > maxChannels -> selectedCount - maxChannels
                incomingLeftOut && selectedCount == maxChannels -> 1
                else -> 0
            }
        if (toUncheck > 0) {
            Text(
                text = pluralStringResource(Res.plurals.channel_slots_full, toUncheck, toUncheck, maxChannels),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val DIALOG_MARGIN = 24.dp

private const val WORD_JOINER = '⁠'

/** Opacity of the warning tint behind the group of changes to sending and receiving. */
private const val WARNING_CONTAINER_ALPHA = 0.2f

@PreviewLightDark
@Composable
private fun ScannedQrCodeDialogPreview() {
    AppTheme {
        ScannedQrCodeDialog(
            channels =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(Channel.default.settings)
                    wb.lora_config = Channel.default.loraConfig
                }
                .build(),
            incoming =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(Channel.default.settings)
                    wb.lora_config = Channel.default.loraConfig
                }
                .build(),
            onDismiss = {},
            onConfirm = {},
        )
    }
}
