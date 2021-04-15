package com.geeksville.mesh.model

import com.geeksville.mesh.ChannelProtos
import com.google.protobuf.ByteString

/** Utility function to make it easy to declare byte arrays - FIXME move someplace better */
fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }


data class Channel(val settings: ChannelProtos.ChannelSettings) {
    companion object {
        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0xbf
        )

        private val cleartextPSK = ByteString.EMPTY
        private val defaultPSK =
            byteArrayOfInts(1) // a shortstring code to indicate we need our default PSK

        // TH=he unsecured channel that devices ship with
        val default = Channel(
            ChannelProtos.ChannelSettings.newBuilder()
                .setModemConfig(ChannelProtos.ChannelSettings.ModemConfig.Bw125Cr48Sf4096)
                .setPsk(ByteString.copyFrom(defaultPSK))
                .build()
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
                cleartextPSK
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
            // start with the PSK then xor in the name
            val pskCode = xorHash(psk.toByteArray())
            val nameCode = xorHash(name.toByteArray())
            val suffix = 'A' + ((pskCode xor nameCode) % 26)

            return "#${name}-${suffix}"
        }

    override fun equals(o: Any?): Boolean = (o is Channel)
        && psk.toByteArray() contentEquals o.psk.toByteArray()
        && name == o.name
}

fun xorHash(b: ByteArray) = b.fold(0, { acc, x -> acc xor (x.toInt() and 0xff) })