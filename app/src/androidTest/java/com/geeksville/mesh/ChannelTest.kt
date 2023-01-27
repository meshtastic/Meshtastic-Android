package com.geeksville.mesh

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.model.ChannelSet
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = ChannelSet()

        Assert.assertTrue(ch.getChannelUrl().toString().startsWith(ChannelSet.prefix))
        Assert.assertEquals(ChannelSet(ch.getChannelUrl()), ch)
    }
}