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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model.util

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.channelNum
import org.meshtastic.core.model.numChannels
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.MeshBeacon
import org.meshtastic.proto.ModuleSettings

/**
 * Return a [ChannelSet] that represents the ChannelSet encoded by the URL.
 *
 * @throws MalformedMeshtasticUrlException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedMeshtasticUrlException::class)
fun CommonUri.toChannelSet(): ChannelSet {
    val h = host ?: ""
    val isCorrectHost =
        h.equals(MESHTASTIC_HOST, ignoreCase = true) || h.equals("www.$MESHTASTIC_HOST", ignoreCase = true)
    val segments = pathSegments
    val isCorrectPath = segments.any { it.equals("e", ignoreCase = true) }
    val hasFragment = !fragment.isNullOrBlank()

    if (!hasFragment || !isCorrectHost || !isCorrectPath) {
        throw MalformedMeshtasticUrlException(
            "Not a valid Meshtastic URL: host=$h, segmentCount=${segments.size}, hasFragment=$hasFragment",
        )
    }

    // Older versions of Meshtastic clients (Apple/web) included `?add=true` within the URL fragment.
    // This gracefully handles those cases until the newer version are generally available/used.
    val fragmentBase64 = fragment!!.substringBefore('?').replace('-', '+').replace('_', '/')
    val fragmentBytes =
        fragmentBase64.decodeBase64() ?: throw MalformedMeshtasticUrlException("Invalid Base64 in URL fragment")
    val url = ChannelSet.ADAPTER.decode(fragmentBytes)
    val shouldAdd =
        fragment?.substringAfter('?', "")?.takeUnless { it.isBlank() }?.equals("add=true")
            ?: getBooleanQueryParameter("add", false)

    return if (shouldAdd) url.copy(lora_config = null) else url
}

/** @return A list of globally unique channel IDs usable with MQTT subscribe() */
val ChannelSet.subscribeList: List<String>
    get() {
        val loraConfig = this.lora_config ?: LoRaConfig()
        return settings.filter { it.downlink_enabled }.map { Channel(it, loraConfig).name }
    }

fun ChannelSet.getChannel(index: Int): Channel? = if (settings.size > index) {
    val s = settings[index]
    Channel(s, lora_config ?: LoRaConfig())
} else {
    null
}

/** Return the primary channel info */
val ChannelSet.primaryChannel: Channel?
    get() = getChannel(0)

fun ChannelSet.hasLoraConfig(): Boolean = lora_config != null

/** How a received Mesh Beacon invitation can be joined, given the connected radio's current settings. */
enum class BeaconJoinOption {
    /** Add the offered channel to a free secondary slot with no retune/reboot (mesh shares the radio's frequency). */
    ADD,

    /** Retune the radio's primary channel (and LoRa preset/region) onto the offered mesh — reboots the radio. */
    SWITCH,

    /** The beacon carries no join offer (message-only) — nothing to join. */
    NONE,
}

/**
 * Decides whether a beacon can be joined by simply **adding** its channel (no reboot) or requires a **switch**
 * (retune + reboot). Adding works only when the offered mesh sits on the radio's *current* frequency slot — Meshtastic
 * secondary channels ride the primary channel's frequency, so the offered channel must resolve (name-hash) to the same
 * slot the radio's primary is on, under a matching preset and region. Mirrors the Apple `014-mesh-beacons`
 * FR-016/FR-017 logic.
 *
 * @param currentLora The radio's current [LoRaConfig] (`null` → can't reason, so [SWITCH]).
 * @param currentChannels The radio's current channel settings, index 0 = primary.
 */
@Suppress("ReturnCount")
fun MeshBeacon.beaconJoinOption(currentLora: LoRaConfig?, currentChannels: List<ChannelSettings>): BeaconJoinOption {
    val offer = offer_channel ?: return BeaconJoinOption.NONE
    val lora = currentLora ?: return BeaconJoinOption.SWITCH
    // An offered preset must match the radio's; an omitted preset (null) means "ride the current preset" → matches.
    val presetMatches = offer_preset == null || (lora.use_preset && offer_preset == lora.modem_preset)
    // offer_region == UNSET (0) means "not offered"; only a set, differing region forces a switch.
    val regionMatches = offer_region == RegionCode.UNSET || offer_region == lora.region
    if (!presetMatches || !regionMatches) return BeaconJoinOption.SWITCH
    // With an explicit slot override we can't compare the offered mesh's slot; be safe and switch.
    if (lora.channel_num != 0 || lora.numChannels <= 0) return BeaconJoinOption.SWITCH
    // Hash the *effective* names: an empty channel name resolves to its preset display name ("LongFast", …), which is
    // what firmware hashes for the slot — comparing raw "" on both sides would misclassify an unnamed primary.
    val currentSlot = lora.channelNum(Channel(currentChannels.firstOrNull() ?: ChannelSettings(), lora).name)
    val offeredSlot = lora.channelNum(Channel(offer, lora).name)
    return if (offeredSlot == currentSlot) BeaconJoinOption.ADD else BeaconJoinOption.SWITCH
}

/**
 * Builds the [ChannelSet] to hand the QR channel-import dialog for a given [option].
 *
 * [ADD][BeaconJoinOption.ADD] omits `lora_config` so the dialog merges the offered channel into a free secondary slot
 * with no reboot. [SWITCH][BeaconJoinOption.SWITCH] carries a `lora_config` derived from [currentLora] with only the
 * advertised preset+region applied and `channel_num` reset to 0 (firmware derives the frequency from the offered
 * channel name — Apple FR-006). Every other radio setting (`hop_limit`, `tx_power`, `tx_enabled`, …) is preserved from
 * [currentLora], so joining never silently zeroes them — the beacon proto advertises no such fields. Returns `null` for
 * [NONE][BeaconJoinOption.NONE] or a beacon with no offered channel.
 *
 * Both paths strip position sharing from the offered channel ([withoutPositionSharing]) so joining a stranger's mesh
 * never leaks our location — matching Apple's `joinBeaconMesh`/`addBeaconChannel`.
 *
 * @param currentLora The radio's current [LoRaConfig]; a switch alters only preset/region/channel_num.
 */
fun MeshBeacon.toJoinChannelSet(option: BeaconJoinOption, currentLora: LoRaConfig?): ChannelSet? {
    val offerChannel = (offer_channel ?: return null).withoutPositionSharing()
    return when (option) {
        BeaconJoinOption.ADD -> ChannelSet(settings = listOf(offerChannel))

        BeaconJoinOption.SWITCH -> {
            // Always ship a LoRaConfig so channel_num resets to 0 and firmware re-derives the frequency from the
            // offered
            // channel name (Apple FR-006) — even when no preset is offered (else an explicit channel_num override would
            // strand the radio on the old slot). Preserve every current field; override only the advertised
            // preset/region.
            val base = currentLora ?: LoRaConfig()
            val loraConfig =
                base.copy(
                    use_preset = true,
                    channel_num = 0,
                    modem_preset = offer_preset ?: base.modem_preset,
                    region = if (offer_region != RegionCode.UNSET) offer_region else base.region,
                )
            ChannelSet(settings = listOf(offerChannel), lora_config = loraConfig)
        }

        BeaconJoinOption.NONE -> null
    }
}

/**
 * Zeroes `position_precision` on the offered channel so joining a beaconed mesh never broadcasts our location to a mesh
 * of strangers (privacy-first; Apple sets `positionPrecision = 0` on both add and switch).
 */
private fun ChannelSettings.withoutPositionSharing(): ChannelSettings =
    copy(module_settings = (module_settings ?: ModuleSettings()).copy(position_precision = 0))

/**
 * Return a URL that represents the [ChannelSet]
 *
 * @param upperCasePrefix portions of the URL can be upper case to make for more efficient QR codes
 */
fun ChannelSet.getChannelUrl(upperCasePrefix: Boolean = false, shouldAdd: Boolean = false): CommonUri {
    val channelBytes = ChannelSet.ADAPTER.encode(this)
    val enc = channelBytes.toByteString().base64Url().replace("=", "")
    val p = if (upperCasePrefix) CHANNEL_URL_PREFIX.uppercase() else CHANNEL_URL_PREFIX
    val query = if (shouldAdd) "?add=true" else ""
    return CommonUri.parse("$p$query#$enc")
}
