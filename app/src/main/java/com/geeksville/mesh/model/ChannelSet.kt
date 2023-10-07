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
