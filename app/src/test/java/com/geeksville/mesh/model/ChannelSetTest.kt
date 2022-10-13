package com.geeksville.mesh.model

import android.net.Uri
import org.junit.Assert
import org.junit.Test

class ChannelSetTest {
    /** make sure we match the python and device code behavior */
    @Test
    fun matchPython() {
        val url = Uri.parse("https://meshtastic.org/e/#CgUYAiIBAQ")
        val cs = ChannelSet(url)
        Assert.assertEquals("LongFast", cs.primaryChannel!!.name)
        Assert.assertEquals("#LongFast-K", cs.primaryChannel!!.humanName)
        Assert.assertEquals(url, cs.getChannelUrl(false))
    }
}
