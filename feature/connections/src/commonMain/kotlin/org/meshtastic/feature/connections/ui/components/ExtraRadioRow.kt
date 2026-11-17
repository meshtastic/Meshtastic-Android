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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.schemaLabelRes
import org.meshtastic.core.network.radio.PrimaryRadio
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_bearer_lora_extra
import org.meshtastic.core.resources.node_extra_radio_board
import org.meshtastic.core.resources.node_extra_radio_bridges
import org.meshtastic.core.resources.node_extra_radio_failure
import org.meshtastic.core.resources.node_extra_radio_listens
import org.meshtastic.core.resources.node_extra_radio_mirrors
import org.meshtastic.core.resources.node_extra_radio_receive_only
import org.meshtastic.core.resources.node_extra_radio_rtl_sdr
import org.meshtastic.core.resources.node_extra_radio_transmits
import org.meshtastic.core.resources.node_extra_radio_usb_stick
import org.meshtastic.core.resources.node_primary_radio
import org.meshtastic.core.ui.component.icon
import org.meshtastic.feature.connections.model.ExtraRadioPart
import org.meshtastic.feature.connections.model.ExtraRadioUi
import org.meshtastic.node.host.ExtraRadioDevice
import org.meshtastic.proto.Config

/**
 * One of the host's extra LoRa radios: what it is, then [stateText], and a switch of its own that LoRa's switch
 * overrides. The LoRa row above carries the grant when the radios wait on a permission.
 */
@Composable
internal fun ExtraRadioRow(
    extra: ExtraRadioUi,
    icon: ImageVector,
    stateText: String,
    onSetExtraRadio: (String, Boolean) -> Unit,
) {
    ListItem(
        onClick = { onSetExtraRadio(extra.name, !extra.switchedOn) },
        enabled = extra.loraOn,
        shapes = ListItemDefaults.shapes(shape = RectangleShape),
        // Only the switch dims under LoRa's: the row still has to say what the radio is and why it is off.
        colors =
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = icon.icon(),
        trailingContent = { Switch(checked = extra.switchedOn, onCheckedChange = null, enabled = extra.loraOn) },
        supportingContent = { ExtraRadioSupportingText(extra = extra, stateText = stateText) },
        content = { Text(stringResource(Res.string.node_bearer_lora_extra, extra.number)) },
    )
}

/**
 * What the radio is and does, then its state and counters. Past [EXTRA_RADIO_LINES] lines the description gives up its
 * least important parts, one per layout pass, until the text fits the width the row has.
 */
@Composable
private fun ExtraRadioSupportingText(extra: ExtraRadioUi, stateText: String) {
    var fit by remember(extra) { mutableStateOf(Fit()) }
    val shown = extra.parts(fit.dropped)
    val kind = extra.details?.device?.let { stringResource(it.label()) }
    val segments = buildList {
        if (ExtraRadioPart.KIND in shown && kind != null) {
            add(if (ExtraRadioPart.ID in shown) "$kind ${extra.id}" else kind)
        }
        if (ExtraRadioPart.ROLE in shown) add(extraRadioRole(extra.details?.preset))
        if (ExtraRadioPart.RECEIVE_ONLY in shown) add(stringResource(Res.string.node_extra_radio_receive_only))
        if (ExtraRadioPart.TRANSMITS in shown) add(stringResource(Res.string.node_extra_radio_transmits))
        if (ExtraRadioPart.BRIDGES in shown) add(stringResource(Res.string.node_extra_radio_bridges))
        add(stateText)
    }
    Text(
        text = segments.joinToString(BEARER_TEXT_SEPARATOR),
        // With nothing left to give up the text wraps on, so the state and counters are never cut.
        maxLines = if (fit.dropped < extra.droppable) EXTRA_RADIO_LINES else Int.MAX_VALUE,
        onTextLayout = { layout ->
            val width = layout.layoutInput.constraints.maxWidth
            fit =
                when {
                    // A new width starts over, so a row that grew wider gets its parts back.
                    width != fit.width && fit.dropped > 0 -> Fit(width, dropped = 0)

                    layout.hasVisualOverflow && fit.dropped < extra.droppable -> Fit(width, fit.dropped + 1)

                    else -> Fit(width, fit.dropped)
                }
        },
    )
}

private data class Fit(val width: Int = -1, val dropped: Int = 0)

@Composable
private fun extraRadioRole(preset: Config.LoRaConfig.ModemPreset?): String = if (preset == null) {
    stringResource(Res.string.node_extra_radio_mirrors)
} else {
    val label = preset.schemaLabelRes()?.let { stringResource(it) } ?: preset.name
    stringResource(Res.string.node_extra_radio_listens, label)
}

/** Why the stored choices are unusable, shown once under the extra radios. */
@Composable
internal fun ExtraRadioFailure(reason: String) {
    Text(
        text = stringResource(Res.string.node_extra_radio_failure, reason),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** What the host's first LoRa radio is, then that it is the primary: `Board lora-aio · primary`. */
@Composable
internal fun primaryRadioText(primary: PrimaryRadio): String {
    val kind = stringResource(primary.device.label())
    val id = primary.id?.trim()?.takeIf { it.isNotEmpty() }
    val named = if (id != null) "$kind $id" else kind
    return "$named$BEARER_TEXT_SEPARATOR${stringResource(Res.string.node_primary_radio)}"
}

private fun ExtraRadioDevice.label(): StringResource = when (this) {
    ExtraRadioDevice.RTL_SDR -> Res.string.node_extra_radio_rtl_sdr
    ExtraRadioDevice.USB_STICK -> Res.string.node_extra_radio_usb_stick
    ExtraRadioDevice.BOARD -> Res.string.node_extra_radio_board
}

private const val EXTRA_RADIO_LINES = 2
