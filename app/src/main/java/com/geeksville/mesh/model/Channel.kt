package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.MeshProtos
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException


data class Channel(
    val settings: MeshProtos.ChannelSettings = MeshProtos.ChannelSettings.getDefaultInstance()
) {
    companion object {
        // Placeholder when emulating
        val emulated = Channel(
            // Note: this string _SHOULD NOT BE LOCALIZED_ because it directly hashes to values used on the device for the default channel name.
            MeshProtos.ChannelSettings.newBuilder().setName("Default")
                .setModemConfig(MeshProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128).build()
        )

        const val prefix = "https://www.meshtastic.org/c/"

        private const val base64Flags = Base64.URL_SAFE + Base64.NO_WRAP

        private fun urlToSettings(url: Uri): MeshProtos.ChannelSettings {
            val urlStr = url.toString()
            val pathRegex = Regex("$prefix(.*)")
            val (base64) = pathRegex.find(urlStr)?.destructured
                ?: throw MalformedURLException("Not a meshtastic URL")
            val bytes = Base64.decode(base64, base64Flags)

            return MeshProtos.ChannelSettings.parseFrom(bytes)
        }
    }

    constructor(url: Uri) : this(urlToSettings(url))

    val name: String get() = settings.name
    val modemConfig: MeshProtos.ChannelSettings.ModemConfig get() = settings.modemConfig

    /// Can this channel be changed right now?
    var editable = false

    /// Return an URL that represents the current channel values
    fun getChannelUrl(): Uri {
        // If we have a valid radio config use it, othterwise use whatever we have saved in the prefs

        val channelBytes = settings.toByteArray() ?: ByteArray(0) // if unset just use empty
        val enc = Base64.encodeToString(channelBytes, base64Flags)

        return Uri.parse("$prefix$enc")
    }

    fun getChannelQR(): Bitmap {
        val multiFormatWriter = MultiFormatWriter()

        val bitMatrix =
            multiFormatWriter.encode(getChannelUrl().toString(), BarcodeFormat.QR_CODE, 192, 192);
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    }
}


/**
 * a nice readable description of modem configs
 */
fun MeshProtos.ChannelSettings.ModemConfig.toHumanString(): String = when (this) {
    MeshProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128 -> "Medium range (but fast)"
    MeshProtos.ChannelSettings.ModemConfig.Bw500Cr45Sf128 -> "Short range (but fast)"
    MeshProtos.ChannelSettings.ModemConfig.Bw31_25Cr48Sf512 -> "Long range (but slower)"
    MeshProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096 -> "Very long range (but slow)"
    else -> this.toString()
}
