package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.android.Logging
import com.geeksville.mesh.AppOnlyProtos
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException

data class ChannelSet(
    val protobuf: AppOnlyProtos.ChannelSet = AppOnlyProtos.ChannelSet.getDefaultInstance()
) : Logging {
    companion object {

        const val prefix = "https://www.meshtastic.org/d/#"

        private const val base64Flags = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

        private fun urlToChannels(url: Uri): AppOnlyProtos.ChannelSet {
            val urlStr = url.toString()

            // We no longer support the super old (about 0.8ish? verison of the URLs that don't use the # separator - I doubt
            // anyone is still using that old format
            val pathRegex = Regex("$prefix(.*)", RegexOption.IGNORE_CASE)
            val (base64) = pathRegex.find(urlStr)?.destructured
                ?: throw MalformedURLException("Not a meshtastic URL: ${urlStr.take(40)}")
            val bytes = Base64.decode(base64, base64Flags)

            return AppOnlyProtos.ChannelSet.parseFrom(bytes)
        }
    }

    constructor(url: Uri) : this(urlToChannels(url))

    /// Can this channel be changed right now?
    var editable = false

    /**
     * Return the primary channel info
     */
    val primaryChannel: Channel?
        get() =
            if (protobuf.settingsCount > 0)
                Channel(protobuf.getSettings(0))
            else
                null

    /// Return an URL that represents the current channel values
    /// @param upperCasePrefix - portions of the URL can be upper case to make for more efficient QR codes
    fun getChannelUrl(upperCasePrefix: Boolean = false): Uri {
        // If we have a valid radio config use it, othterwise use whatever we have saved in the prefs

        val channelBytes = protobuf.toByteArray() ?: ByteArray(0) // if unset just use empty
        val enc = Base64.encodeToString(channelBytes, base64Flags)

        val p = if (upperCasePrefix)
            prefix.toUpperCase()
        else
            prefix
        return Uri.parse("$p$enc")
    }

    val qrCode
        get(): Bitmap? = try {
            val multiFormatWriter = MultiFormatWriter()

            // We encode as UPPER case for the QR code URL because QR codes are more efficient for that special case
            val bitMatrix =
                multiFormatWriter.encode(
                    getChannelUrl(true).toString(),
                    BarcodeFormat.QR_CODE,
                    192,
                    192
                )
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (ex: Throwable) {
            errormsg("URL was too complex to render as barcode")
            null
        }
}

