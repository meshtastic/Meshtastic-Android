package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.MeshProtos
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException

/** Utility function to make it easy to declare byte arrays - FIXME move someplace better */
fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }


data class Channel(
    val settings: MeshProtos.ChannelSettings = MeshProtos.ChannelSettings.getDefaultInstance()
) {
    companion object {
        // Note: this string _SHOULD NOT BE LOCALIZED_ because it directly hashes to values used on the device for the default channel name.
        val defaultChannelName = "Default"

        // These bytes must math the well known and not secret bytes used the default channel AES128 key device code
        val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0xbf
        )

        // Placeholder when emulating
        val emulated = Channel(
            MeshProtos.ChannelSettings.newBuilder().setName(defaultChannelName)
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
