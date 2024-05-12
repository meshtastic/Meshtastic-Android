package com.geeksville.mesh.model

import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig.ModemPreset
import com.geeksville.mesh.ConfigKt.loRaConfig
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.channelSettings
import com.google.protobuf.ByteString

/** Utility function to make it easy to declare byte arrays - FIXME move someplace better */
fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
fun xorHash(b: ByteArray) = b.fold(0) { acc, x -> acc xor (x.toInt() and 0xff) }

data class Channel(
    val settings: ChannelProtos.ChannelSettings = default.settings,
    val loraConfig: ConfigProtos.Config.LoRaConfig = default.loraConfig,
    var shared: Boolean = true, /* Used to determine if the user wishes to include in the URL/QR */
) {
    companion object {
        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        private val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0x01
        )

        private val cleartextPSK = ByteString.EMPTY
        private val defaultPSK =
            byteArrayOfInts(1) // a shortstring code to indicate we need our default PSK

        // The default channel that devices ship with
        val default = Channel(
            channelSettings { psk = ByteString.copyFrom(defaultPSK) },
            // references: NodeDB::installDefaultConfig / Channels::initDefaultChannel
            loRaConfig {
                usePreset = true
                modemPreset = ModemPreset.LONG_FAST
                hopLimit = 3
                txEnabled = true
            }
        )
    }

    /// Return the name of our channel as a human readable string.  If empty string, assume "Default" per mesh.proto spec
    val name: String
        get() = settings.name.ifEmpty {
            // We have a new style 'empty' channel name.  Use the same logic from the device to convert that to a human readable name
            if (loraConfig.usePreset) when (loraConfig.modemPreset) {
                ModemPreset.SHORT_FAST -> "ShortFast"
                ModemPreset.SHORT_SLOW -> "ShortSlow"
                ModemPreset.MEDIUM_FAST -> "MediumFast"
                ModemPreset.MEDIUM_SLOW -> "MediumSlow"
                ModemPreset.LONG_FAST -> "LongFast"
                ModemPreset.LONG_SLOW -> "LongSlow"
                ModemPreset.LONG_MODERATE -> "LongMod"
                ModemPreset.VERY_LONG_SLOW -> "VLongSlow"
                else -> "Invalid"
            } else "Custom"
        }

    val psk: ByteString
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
     * Given a channel name and psk, return the (0 to 255) hash for that channel
     */
    val hash: Int get() = xorHash(name.toByteArray()) xor xorHash(psk.toByteArray())

    val channelNum: Int get() = loraConfig.channelNum(name)

    val radioFreq: Float get() = loraConfig.radioFreq(channelNum)

    override fun equals(other: Any?): Boolean = (other is Channel)
            && psk.toByteArray() contentEquals other.psk.toByteArray()
            && name == other.name

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + loraConfig.hashCode()
        return result
    }
}
