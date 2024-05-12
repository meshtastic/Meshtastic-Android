package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException
import kotlin.jvm.Throws

internal const val URL_PREFIX = "https://meshtastic.org/e/#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

/**
 * Return a [ChannelSet] that represents the URL
 * @throws MalformedURLException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedURLException::class)
fun Uri.toChannelSet(): ChannelSet {
    val urlStr = this.toString()

    val pathRegex = Regex("$URL_PREFIX(.*)", RegexOption.IGNORE_CASE)
    val (base64) = pathRegex.find(urlStr)?.destructured
        ?: throw MalformedURLException("Not a Meshtastic URL: ${urlStr.take(40)}")
    val bytes = Base64.decode(base64, BASE64FLAGS)

    return ChannelSet.parseFrom(bytes)
}

/**
 * @return A list of globally unique channel IDs usable with MQTT subscribe()
 */
val ChannelSet.subscribeList: List<String>
    get() = settingsList.filter { it.downlinkEnabled }.map { Channel(it, loraConfig).name }

/**
 * Return the primary channel info
 */
val ChannelSet.primaryChannel: Channel?
    get() = if (settingsCount > 0) Channel(getSettings(0), loraConfig) else null

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

/**
 * Return a URL that represents the filtered [ChannelSet], based on user selection.
 * @param upperCasePrefix portions of the URL can be upper case to make for more efficient QR codes
 * @param channels contains the data needed to filter based on user selection.
 */
fun ChannelSet.getSelectedChannelUrl(upperCasePrefix: Boolean = false,
                                     channels: List<Channel>): Uri {
    /* Create a [ChannelSet] based on the channels selected by the user to Share */
    val currLoraConfig = this.loraConfig
    var selectedChannelSet = channelSet {
        loraConfig = loraConfig.copy {
            usePreset = currLoraConfig.usePreset
            region = currLoraConfig.region
            hopLimit = currLoraConfig.hopLimit
            txEnabled = currLoraConfig.txEnabled
            txPower = currLoraConfig.txPower
        }
    }
    for (i in 0 .. this.settingsList.lastIndex) {
        if (channels.getOrNull(i)?.shared == true) {
            selectedChannelSet = selectedChannelSet.copy {
                settings.add(channelSettings {
                    name = settingsList[i].name
                    psk = settingsList[i].psk
                })}
        }
    }

    /* Make URL based on the selected channels */
    /* Empty if unset */
    val channelBytes = selectedChannelSet.toByteArray() ?: ByteArray(0)
    val enc = Base64.encodeToString(channelBytes, BASE64FLAGS)
    val p = if (upperCasePrefix) URL_PREFIX.uppercase() else URL_PREFIX
    return Uri.parse("$p$enc")
}

/**
 * @param channels contains the data needed to filter based on user selection.
 */
fun ChannelSet.getQRCode(channels: List<Channel>): Bitmap? {
    try {
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix =
            multiFormatWriter.encode(
                getSelectedChannelUrl(channels = channels).toString(),
                BarcodeFormat.QR_CODE,
                960,
                960
            )
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    } catch (ex: Throwable) {
        errormsg("URL was too complex to render as barcode")
        return null
    }
}
