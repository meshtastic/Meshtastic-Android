package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.MeshProtos
import com.google.protobuf.ByteString
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
        // FIXME - make this work with new channel name system
        val defaultChannelName = "Default"

        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0xbf
        )

        // Placeholder when emulating
        val emulated = Channel(
            MeshProtos.ChannelSettings.newBuilder().setName(defaultChannelName)
                .setModemConfig(MeshProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128).build()
        )

        const val prefix = "https://www.meshtastic.org/c/#"

        private const val base64Flags = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

        private fun urlToSettings(url: Uri): MeshProtos.ChannelSettings {
            val urlStr = url.toString()

            // We no longer support the super old (about 0.8ish? verison of the URLs that don't use the # separator - I doubt
            // anyone is still using that old format
            val pathRegex = Regex("$prefix(.*)", RegexOption.IGNORE_CASE)
            val (base64) = pathRegex.find(urlStr)?.destructured
                ?: throw MalformedURLException("Not a meshtastic URL: ${urlStr.take(40)}")
            val bytes = Base64.decode(base64, base64Flags)

            return MeshProtos.ChannelSettings.parseFrom(bytes)
        }
    }

    constructor(url: Uri) : this(urlToSettings(url))

    /// Return the name of our channel as a human readable string.  If empty string, assume "Default" per mesh.proto spec
    val name: String
        get() = if (settings.name.isEmpty()) {
            // We have a new style 'empty' channel name.  Use the same logic from the device to convert that to a human readable name
            if (settings.bandwidth != 0)
                "Unset"
            else when (settings.modemConfig) {
                MeshProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128 -> "Medium"
                MeshProtos.ChannelSettings.ModemConfig.Bw500Cr45Sf128 -> "ShortFast"
                MeshProtos.ChannelSettings.ModemConfig.Bw31_25Cr48Sf512 -> "LongAlt"
                MeshProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096 -> "LongSlow"
                else -> "Invalid"
            }
        } else
            settings.name

    val modemConfig: MeshProtos.ChannelSettings.ModemConfig get() = settings.modemConfig

    val psk
        get() = if (settings.psk.size() != 1)
            settings.psk // A standard PSK
        else {
            // One of our special 1 byte PSKs, see mesh.proto for docs.
            val pskIndex = settings.psk.byteAt(0).toInt()

            if (pskIndex == 0)
                ByteString.EMPTY // Treat as an empty PSK (no encryption)
            else {
                // Treat an index of 1 as the old channelDefaultKey and work up from there
                val bytes = channelDefaultKey.clone()
                bytes[bytes.size - 1] = (0xff and (bytes[bytes.size - 1] + pskIndex - 1)).toByte()
                ByteString.copyFrom(bytes)
            }
        }

    /**
     * Return a name that is formatted as #channename-suffix
     *
     * Where suffix indicates the hash of the PSK
     */
    val humanName: String
        get() {
            val suffix: Char = if (settings.psk.size() != 1) {
                // we have a full PSK, so hash it to generate the suffix
                val code = settings.psk.fold(0, { acc, x -> acc xor (x.toInt() and 0xff) })

                'A' + (code % 26)
            } else
                '0' + settings.psk.byteAt(0).toInt()

            return "#${name}-${suffix}"
        }

    /// Can this channel be changed right now?
    var editable = false

    /// Return an URL that represents the current channel values
    /// @param upperCasePrefix - portions of the URL can be upper case to make for more efficient QR codes
    fun getChannelUrl(upperCasePrefix: Boolean = false): Uri {
        // If we have a valid radio config use it, othterwise use whatever we have saved in the prefs

        val channelBytes = settings.toByteArray() ?: ByteArray(0) // if unset just use empty
        val enc = Base64.encodeToString(channelBytes, base64Flags)

        val p = if(upperCasePrefix)
            prefix.toUpperCase()
        else
            prefix
        return Uri.parse("$p$enc")
    }

    fun getChannelQR(): Bitmap {
        val multiFormatWriter = MultiFormatWriter()

        // We encode as UPPER case for the QR code URL because QR codes are more efficient for that special case
        val bitMatrix =
            multiFormatWriter.encode(getChannelUrl(true).toString(), BarcodeFormat.QR_CODE, 192, 192);
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.createBitmap(bitMatrix)
    }
}
