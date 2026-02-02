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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.util.URL_PREFIX
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig)
        val channelUrl = ch.getChannelUrl()

        Assert.assertTrue(channelUrl.toString().startsWith(URL_PREFIX))
        Assert.assertEquals(channelUrl.toChannelSet(), ch)
    }

    @Test
    fun channelHashGood() {
        val ch = Channel.default

        Assert.assertEquals(8, ch.hash)
    }

    @Test
    fun numChannelsGood() {
        val ch = Channel.default

        Assert.assertEquals(104, ch.loraConfig.numChannels)
    }

    @Test
    fun channelNumGood() {
        val ch = Channel.default

        Assert.assertEquals(20, ch.channelNum)
    }

    @Test
    fun radioFreqGood() {
        val ch = Channel.default

        Assert.assertEquals(906.875f, ch.radioFreq)
    }

    @Test
    fun allModemPresetsHaveValidNames() {
        Config.LoRaConfig.ModemPreset.entries.forEach { preset ->
            // Skip UNRECOGNIZED if it exists (Wire generates it sometimes) or generic UNSET values if applicable
            if (preset.name == "UNSET" || preset.name == "UNRECOGNIZED") return@forEach

            val loraConfig = Channel.default.loraConfig.copy(use_preset = true, modem_preset = preset)
            val channel = Channel(loraConfig = loraConfig)

            // We want to ensure it is NOT "Invalid"
            Assert.assertNotEquals("Preset ${preset.name} should typically have a valid name", "Invalid", channel.name)
        }
    }
}
