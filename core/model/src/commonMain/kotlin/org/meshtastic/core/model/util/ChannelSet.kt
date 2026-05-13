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
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.LoRaConfig

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

    if (fragment.isNullOrBlank() || !isCorrectHost || !isCorrectPath) {
        throw MalformedMeshtasticUrlException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }

    // Older versions of Meshtastic clients (Apple/web) included `?add=true` within the URL fragment.
    // This gracefully handles those cases until the newer version are generally available/used.
    val fragmentBase64 = fragment!!.substringBefore('?').replace('-', '+').replace('_', '/')
    val fragmentBytes =
        fragmentBase64.decodeBase64()
            ?: throw MalformedMeshtasticUrlException("Invalid Base64 in URL fragment: $fragmentBase64")
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
