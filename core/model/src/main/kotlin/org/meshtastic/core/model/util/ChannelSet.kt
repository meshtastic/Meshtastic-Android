/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.LoRaConfig
import java.net.MalformedURLException

private const val MESHTASTIC_HOST = "meshtastic.org"
private const val CHANNEL_PATH = "/e/"
const val URL_PREFIX = "https://$MESHTASTIC_HOST$CHANNEL_PATH"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

/**
 * Return a [ChannelSet] that represents the ChannelSet encoded by the URL.
 *
 * @throws MalformedURLException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedURLException::class)
fun Uri.toChannelSet(): ChannelSet {
    if (fragment.isNullOrBlank() || !host.equals(MESHTASTIC_HOST, true) || !path.equals(CHANNEL_PATH, true)) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }

    // Older versions of Meshtastic clients (Apple/web) included `?add=true` within the URL fragment.
    // This gracefully handles those cases until the newer version are generally available/used.
    val fragmentBytes = Base64.decode(fragment!!.substringBefore('?'), BASE64FLAGS)
    val url = ChannelSet.ADAPTER.decode(fragmentBytes.toByteString())
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
fun ChannelSet.getChannelUrl(upperCasePrefix: Boolean = false, shouldAdd: Boolean = false): Uri {
    val channelBytes = ChannelSet.ADAPTER.encode(this)
    val enc = Base64.encodeToString(channelBytes, BASE64FLAGS)
    val p = if (upperCasePrefix) URL_PREFIX.uppercase() else URL_PREFIX
    val query = if (shouldAdd) "?add=true" else ""
    return Uri.parse("$p$query#$enc")
}

fun ChannelSet.qrCode(shouldAdd: Boolean): Bitmap? = try {
    val multiFormatWriter = MultiFormatWriter()
    val bitMatrix =
        multiFormatWriter.encode(getChannelUrl(false, shouldAdd).toString(), BarcodeFormat.QR_CODE, 960, 960)
    val barcodeEncoder = BarcodeEncoder()
    barcodeEncoder.createBitmap(bitMatrix)
} catch (ex: Throwable) {
    Logger.e { "URL was too complex to render as barcode" }
    null
}
