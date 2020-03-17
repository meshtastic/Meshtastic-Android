package com.geeksville.mesh


import androidx.compose.frames.open
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.model.Channel
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelTest {
    @Test
    fun channelUrlGood() {
        open() // Needed to make Compose think we are inside a Frame
        val ch = Channel.emulated

        Assert.assertTrue(ch.getChannelUrl().toString().startsWith(Channel.prefix))
        Assert.assertEquals(Channel(ch.getChannelUrl()), ch)
    }

}