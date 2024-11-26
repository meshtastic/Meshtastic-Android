/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.URL_PREFIX
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.model.numChannels
import com.geeksville.mesh.model.toChannelSet
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        }
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
}
