/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.model

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.byteArrayOfInts
import org.meshtastic.core.model.util.xorHash
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import java.security.SecureRandom

data class Channel(val settings: ChannelSettings = default.settings, val loraConfig: LoRaConfig = default.loraConfig) {
    companion object {
        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        private val channelDefaultKey =
            byteArrayOfInts(
                0xd4,
                0xf1,
                0xbb,
                0x3a,
                0x20,
                0x29,
                0x07,
                0x59,
                0xf0,
                0xbc,
                0xff,
                0xab,
                0xcf,
                0x4e,
                0x69,
                0x01,
            )

        private val cleartextPSK = ByteString.EMPTY
        private val defaultPSK = byteArrayOfInts(1) // a shortstring code to indicate we need our default PSK

        // The default channel that devices ship with
        val default =
            Channel(
                ChannelSettings(psk = defaultPSK.toByteString()),
                // references: NodeDB::installDefaultConfig / Channels::initDefaultChannel
                LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST, hop_limit = 3, tx_enabled = true),
            )

        fun getRandomKey(size: Int = 32): ByteString {
            val bytes = ByteArray(size)
            val random = SecureRandom()
            random.nextBytes(bytes)
            return bytes.toByteString()
        }
    }

    // Return the name of our channel as a human readable string.  If empty string, assume "Default" per mesh.proto spec
    val name: String
        get() =
            settings.name.ifEmpty {
                // We have a new style 'empty' channel name.  Use the same logic from the device to convert that to a
                // human readable name
                if (loraConfig.use_preset) {
                    when (loraConfig.modem_preset) {
                        ModemPreset.SHORT_TURBO -> "ShortTurbo"
                        ModemPreset.SHORT_FAST -> "ShortFast"
                        ModemPreset.SHORT_SLOW -> "ShortSlow"
                        ModemPreset.MEDIUM_FAST -> "MediumFast"
                        ModemPreset.MEDIUM_SLOW -> "MediumSlow"
                        ModemPreset.LONG_FAST -> "LongFast"
                        ModemPreset.LONG_SLOW -> "LongSlow"
                        ModemPreset.LONG_MODERATE -> "LongMod"
                        ModemPreset.VERY_LONG_SLOW -> "VLongSlow"
                        ModemPreset.LONG_TURBO -> "LongTurbo"
                        else -> "Invalid"
                    }
                } else {
                    "Custom"
                }
            }

    val psk: ByteString
        get() =
            if (settings.psk.size != 1) {
                settings.psk // A standard PSK
            } else {
                // One of our special 1 byte PSKs, see mesh.proto for docs.
                val pskIndex = settings.psk[0].toInt()

                if (pskIndex == 0) {
                    cleartextPSK
                } else {
                    // Treat an index of 1 as the old channelDefaultKey and work up from there
                    val bytes = channelDefaultKey.clone()
                    bytes[bytes.size - 1] = (0xff and (bytes[bytes.size - 1] + pskIndex - 1)).toByte()
                    bytes.toByteString()
                }
            }

    /** Given a channel name and psk, return the (0 to 255) hash for that channel */
    val hash: Int
        get() = xorHash(name.toByteArray()) xor xorHash(psk.toByteArray())

    val channelNum: Int
        get() = loraConfig.channelNum(name)

    val radioFreq: Float
        get() = loraConfig.radioFreq(channelNum)

    override fun equals(other: Any?): Boolean =
        (other is Channel) && psk.toByteArray() contentEquals other.psk.toByteArray() && name == other.name

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + loraConfig.hashCode()
        return result
    }
}
