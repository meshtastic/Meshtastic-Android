package com.geeksville.mesh

import android.net.Uri
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.model.primaryChannel
import com.geeksville.mesh.model.toChannelSet
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ChannelSetTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    /** make sure we match the python and device code behavior */
    @Test
    fun matchPython() {
        val url = Uri.parse("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ")
        val cs = url.toChannelSet()
        Assert.assertEquals("LongFast", cs.primaryChannel!!.name)
        Assert.assertEquals(url, cs.getChannelUrl(false))
    }

    /** validate against the host or path in a case-insensitive way */
    @Test
    fun parseCaseInsensitive() {
        var url = Uri.parse("HTTPS://MESHTASTIC.ORG/E/#CgMSAQESBggBQANIAQ")
        Assert.assertEquals("LongFast", url.toChannelSet().primaryChannel!!.name)

        url = Uri.parse("HTTPS://mEsHtAsTiC.OrG/e/#CgMSAQESBggBQANIAQ")
        Assert.assertEquals("LongFast", url.toChannelSet().primaryChannel!!.name)
    }

    /** properly parse channel config when `?add=true` is in the fragment */
    @Test
    fun handleAddInFragment() {
        val url = Uri.parse("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ?add=true")
        val cs = url.toChannelSet()
        Assert.assertEquals("Custom", cs.primaryChannel!!.name)
        Assert.assertFalse(cs.hasLoraConfig())
    }

    /** properly parse channel config when `?add=true` is in the query parameters */
    @Test
    fun handleAddInQueryParams() {
        val url = Uri.parse("https://meshtastic.org/e/?add=true#CgMSAQESBggBQANIAQ")
        val cs = url.toChannelSet()
        Assert.assertEquals("Custom", cs.primaryChannel!!.name)
        Assert.assertFalse(cs.hasLoraConfig())
    }
}
