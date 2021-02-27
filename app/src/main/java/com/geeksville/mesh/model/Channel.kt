package com.geeksville.mesh.model

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.MeshProtos
import com.google.protobuf.ByteString
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.net.MalformedURLException

/** Utility function to make it easy to declare byte arrays - FIXME move someplace better */
fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }


data class Channel(
    val settings: ChannelProtos.ChannelSettings = ChannelProtos.ChannelSettings.getDefaultInstance()
) {
    companion object {
        // Note: this string _SHOULD NOT BE LOCALIZED_ because it directly hashes to values used on the device for the default channel name.
        // FIXME - make this work with new channel name system
        const val defaultChannelName = "Default"

        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0xbf
        )

        // Placeholder when emulating
        val emulated = Channel(
            ChannelProtos.ChannelSettings.newBuilder().setName(defaultChannelName)
                .setModemConfig(ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128).build()
        )
    }

    /// Return the name of our channel as a human readable string.  If empty string, assume "Default" per mesh.proto spec
    val name: String
        get() = if (settings.name.isEmpty()) {
            // We have a new style 'empty' channel name.  Use the same logic from the device to convert that to a human readable name
            if (settings.bandwidth != 0)
                "Unset"
            else when (settings.modemConfig) {
                ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr45Sf128 -> "Medium"
                ChannelProtos.ChannelSettings.ModemConfig.Bw500Cr45Sf128 -> "ShortFast"
                ChannelProtos.ChannelSettings.ModemConfig.Bw31_25Cr48Sf512 -> "LongAlt"
                ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096 -> "LongSlow"
                else -> "Invalid"
            }
        } else
            settings.name

    val modemConfig: ChannelProtos.ChannelSettings.ModemConfig get() = settings.modemConfig

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
}
