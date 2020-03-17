package com.geeksville.mesh.model

import org.junit.Test

class ChannelTest {
    @Test
    fun channelUrlGood() {
        val ch = Channel.emulated

        // FIXME, currently not allowed because it is a Compose model
        // Assert.assertTrue(ch.getChannelUrl().startsWith(Channel.prefix))
    }

}