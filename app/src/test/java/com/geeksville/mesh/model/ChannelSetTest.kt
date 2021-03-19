package com.geeksville.mesh.model

import android.net.Uri
import org.junit.Assert
import org.junit.Test

class ChannelSetTest {
    /** make sure we match the python and device code behavior */
    @Test
    fun matchPython() {
        val url = Uri.parse("https://www.meshtastic.org/d/#CgUYAyIBAQ")
        val cs = ChannelSet(url)
        Assert.assertEquals("LongSlow", cs.primaryChannel!!.name, )
        Assert.assertEquals("#LongSlow-V", cs.primaryChannel!!.humanName, )
        Assert.assertEquals(url, cs.getChannelUrl(false))
    }
}