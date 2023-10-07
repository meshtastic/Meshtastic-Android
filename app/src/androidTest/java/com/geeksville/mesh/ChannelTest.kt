package com.geeksville.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.URL_PREFIX
import com.geeksville.mesh.model.getChannelUrl
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
