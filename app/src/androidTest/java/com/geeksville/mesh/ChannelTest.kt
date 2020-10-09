package com.geeksville.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.model.Channel
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = Channel.emulated

        Assert.assertTrue(ch.getChannelUrl().toString().startsWith(Channel.prefix))
        Assert.assertEquals(Channel(ch.getChannelUrl()), ch)
    }
}