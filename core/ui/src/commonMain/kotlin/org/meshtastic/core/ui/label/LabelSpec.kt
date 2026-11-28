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
package org.meshtastic.core.ui.label

import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.SharedContact

private const val RADIO_SEPARATOR = " · "

/**
 * The first channel's name, or the preset's for the default channel, over the region and preset. The default channel is
 * named after its preset, so its second line says what it is instead of repeating the name.
 */
internal fun channelLabelContent(
    channelSet: ChannelSet,
    modemPresetName: String,
    defaultChannel: String,
    action: String,
): ChannelLabelContent {
    val firstName = channelSet.settings.firstOrNull()?.name.orEmpty()
    val lora = channelSet.lora_config
    val region = lora?.region?.takeIf { it != RegionCode.UNSET }?.name.orEmpty()
    val detail =
        when {
            firstName.isEmpty() -> defaultChannel
            lora?.use_preset == true -> modemPresetName
            else -> ""
        }
    return ChannelLabelContent(
        title = firstName.ifEmpty { modemPresetName },
        radio = listOf(region, detail).filter { it.isNotEmpty() }.joinToString(RADIO_SEPARATOR),
        action = action,
    )
}

/** A label the print sheet can draw: what it says, what its QR encodes, and the words in its band. */
sealed interface LabelSpec {
    val band: String

    /** A contact as labeltastic's name tag. The QR encodes [contact], which is what Share Contact shows. */
    data class NameTag(val content: NameTagContent, val contact: SharedContact, override val band: String) : LabelSpec

    /** A channel set as a channel label. The QR encodes [url], the link Share Channels shows. */
    data class Channels(val content: ChannelLabelContent, val url: String, override val band: String) : LabelSpec

    /**
     * The connected radio as a connect label: the name tag's layout around [link], the meshtastic/design#177 connect
     * link. [linkWithoutPin] is set only when [link] carries the radio's fixed pairing PIN, which is in the QR alone
     * and never printed as text.
     */
    data class Connect(
        val content: NameTagContent,
        val link: String,
        val linkWithoutPin: String?,
        override val band: String,
    ) : LabelSpec {
        /** This label, or the same without the PIN in its QR when [include] is false. */
        fun withPin(include: Boolean): Connect =
            if (include || linkWithoutPin == null) this else copy(link = linkWithoutPin, linkWithoutPin = null)
    }
}

/**
 * Lays [this] out for a label [lengthDots] long, or returns null when its QR does not fit the head at 2 dots per
 * module. A contact's long name is cut in the QR payload first; a channel or connect link has nothing to cut.
 */
fun LabelSpec.layout(lengthDots: Int, measurer: LabelTextMeasurer, encoder: QrEncoder = LabelQrEncoder): LabelLayout? =
    when (this) {
        is LabelSpec.NameTag ->
            fitQr(contactQrPayloads(contact), encoder)?.let { nameTagLayout(content, it, lengthDots, band, measurer) }

        is LabelSpec.Channels ->
            fitQr(sequenceOf(url), encoder)?.let { channelLabelLayout(content, it, lengthDots, band, measurer) }

        is LabelSpec.Connect ->
            fitQr(sequenceOf(link), encoder)?.let { nameTagLayout(content, it, lengthDots, band, measurer) }
    }
