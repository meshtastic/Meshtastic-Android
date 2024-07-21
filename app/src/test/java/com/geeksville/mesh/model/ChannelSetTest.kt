package com.geeksville.mesh.model

import android.net.Uri
import org.junit.Assert
import org.junit.Test

class ChannelSetTest {

    /** make sure we match the python and device code behavior */
    @Test
    fun matchPython() {
        val url = Uri.parse("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ")
        val cs = url.toChannelSet().first
        Assert.assertEquals("LongFast", cs.primaryChannel!!.name)
        Assert.assertEquals(url, cs.getChannelUrl(false))
    }

    /** properly parse channel config when `?add=true` is in the fragment */
    @Test
    fun handleAddInFragment() {
        val url = Uri.parse("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ?add=true")
        val (cs, shouldAdd) = url.toChannelSet()
        Assert.assertEquals("LongFast", cs.primaryChannel!!.name)
        Assert.assertTrue(shouldAdd)
    }

    /** properly parse channel config when `?add=true` is in the query parameters */
    @Test
    fun handleAddInQueryParams() {
        val url = Uri.parse("https://meshtastic.org/e/?add=true#CgMSAQESBggBQANIAQ")
        val (cs, shouldAdd) = url.toChannelSet()
        Assert.assertEquals("LongFast", cs.primaryChannel!!.name)
        Assert.assertTrue(shouldAdd)
    }
}
