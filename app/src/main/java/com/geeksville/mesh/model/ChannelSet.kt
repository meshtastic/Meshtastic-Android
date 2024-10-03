package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException
import kotlin.jvm.Throws

internal const val URL_PREFIX = "https://meshtastic.org/e/#"
private const val MESHTASTIC_DOMAIN = "meshtastic.org"
private const val MESHTASTIC_CHANNEL_CONFIG_PATH = "/e/"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

/**
 * Return a [ChannelSet] that represents the ChannelSet encoded by the URL.
 * @throws MalformedURLException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedURLException::class)
fun Uri.toChannelSet(): ChannelSet {
    if (fragment.isNullOrBlank() ||
        !host.equals(MESHTASTIC_DOMAIN, true) ||
        !path.equals(MESHTASTIC_CHANNEL_CONFIG_PATH, true)
    ) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }

    // Older versions of Meshtastic clients (Apple/web) included `?add=true` within the URL fragment.
    // This gracefully handles those cases until the newer version are generally available/used.
    return ChannelSet.parseFrom(Base64.decode(fragment!!.substringBefore('?'), BASE64FLAGS))
}

/**
 * Return a [Boolean] if the URL indicates the associated [ChannelSet] should be added to the
 * existing configuration.
 * @throws MalformedURLException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedURLException::class)
fun Uri.shouldAddChannels(): Boolean {
    if (fragment.isNullOrBlank() ||
        !host.equals(MESHTASTIC_DOMAIN, true) ||
        !path.equals(MESHTASTIC_CHANNEL_CONFIG_PATH, true)
    ) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }

    // Older versions of Meshtastic clients (Apple/web) included `?add=true` within the URL fragment.
    // This gracefully handles those cases until the newer version are generally available/used.
    return fragment?.substringAfter('?', "")
        ?.takeUnless { it.isBlank() }
        ?.equals("add=true")
        ?: getBooleanQueryParameter("add", false)
}

/**
 * @return A list of globally unique channel IDs usable with MQTT subscribe()
 */
val ChannelSet.subscribeList: List<String>
    get() = settingsList.filter { it.downlinkEnabled }.map { Channel(it, loraConfig).name }

fun ChannelSet.getChannel(index: Int): Channel? =
    if (settingsCount > index) Channel(getSettings(index), loraConfig) else null

/**
 * Return the primary channel info
 */
val ChannelSet.primaryChannel: Channel? get() = getChannel(0)

/**
 * Return a URL that represents the [ChannelSet]
 * @param upperCasePrefix portions of the URL can be upper case to make for more efficient QR codes
 */
fun ChannelSet.getChannelUrl(upperCasePrefix: Boolean = false): Uri {
    val channelBytes = this.toByteArray() ?: ByteArray(0) // if unset just use empty
    val enc = Base64.encodeToString(channelBytes, BASE64FLAGS)
    val p = if (upperCasePrefix) URL_PREFIX.uppercase() else URL_PREFIX
    return Uri.parse("$p$enc")
}

val ChannelSet.qrCode: Bitmap?
    get() = try {
        val multiFormatWriter = MultiFormatWriter()

        val bitMatrix =
            multiFormatWriter.encode(
                getChannelUrl(false).toString(),
                BarcodeFormat.QR_CODE,
                960,
                960
            )
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (ex: Throwable) {
        errormsg("URL was too complex to render as barcode")
        null
    }
