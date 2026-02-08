/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelSetTest {

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

    /** validate that www.meshtastic.org host is accepted */
    @Test
    fun parseWwwHost() {
        val url = Uri.parse("https://www.meshtastic.org/e/#CgMSAQESBggBQANIAQ")
        Assert.assertEquals("LongFast", url.toChannelSet().primaryChannel!!.name)
    }

    /** validate that short /e path is accepted */
    @Test
    fun parseShortPath() {
        val url = Uri.parse("https://meshtastic.org/e#CgMSAQESBggBQANIAQ")
        Assert.assertEquals("LongFast", url.toChannelSet().primaryChannel!!.name)
    }

    /** validate that long /channel/e path is accepted */
    @Test
    fun parseLongPath() {
        val url = Uri.parse("https://meshtastic.org/channel/e/#CgMSAQESBggBQANIAQ")
        Assert.assertEquals("LongFast", url.toChannelSet().primaryChannel!!.name)
    }
}
