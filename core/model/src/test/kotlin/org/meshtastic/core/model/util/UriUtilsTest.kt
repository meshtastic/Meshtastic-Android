/*
 * Copyright (c) 2026 Meshtastic LLC
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriUtilsTest {

    @Test
    fun `handleMeshtasticUri handles channel share uri`() {
        val uri = Uri.parse("https://meshtastic.org/e/somechannel")
        var channelCalled = false
        val handled = handleMeshtasticUri(uri, onChannel = { channelCalled = true })
        assertTrue("Should handle channel URI", handled)
        assertTrue("Should invoke onChannel callback", channelCalled)
    }

    @Test
    fun `handleMeshtasticUri handles contact share uri`() {
        val uri = Uri.parse("https://meshtastic.org/v/somecontact")
        var contactCalled = false
        val handled = handleMeshtasticUri(uri, onContact = { contactCalled = true })
        assertTrue("Should handle contact URI", handled)
        assertTrue("Should invoke onContact callback", contactCalled)
    }

    @Test
    fun `handleMeshtasticUri ignores other hosts`() {
        val uri = Uri.parse("https://example.com/e/somechannel")
        val handled = handleMeshtasticUri(uri)
        assertFalse("Should not handle other hosts", handled)
    }

    @Test
    fun `handleMeshtasticUri ignores other paths`() {
        val uri = Uri.parse("https://meshtastic.org/other/path")
        val handled = handleMeshtasticUri(uri)
        assertFalse("Should not handle unknown paths", handled)
    }

    @Test
    fun `handleMeshtasticUri handles case insensitivity`() {
        val uri = Uri.parse("https://MESHTASTIC.ORG/E/somechannel")
        var channelCalled = false
        val handled = handleMeshtasticUri(uri, onChannel = { channelCalled = true })
        assertTrue("Should handle mixed case URI", handled)
        assertTrue("Should invoke onChannel callback", channelCalled)
    }

    @Test
    fun `handleMeshtasticUri handles www host`() {
        val uri = Uri.parse("https://www.meshtastic.org/e/somechannel")
        var channelCalled = false
        val handled = handleMeshtasticUri(uri, onChannel = { channelCalled = true })
        assertTrue("Should handle www host", handled)
        assertTrue("Should invoke onChannel callback", channelCalled)
    }

    @Test
    fun `handleMeshtasticUri handles long channel path`() {
        val uri = Uri.parse("https://meshtastic.org/channel/e/somechannel")
        var channelCalled = false
        val handled = handleMeshtasticUri(uri, onChannel = { channelCalled = true })
        assertTrue("Should handle long channel path", handled)
        assertTrue("Should invoke onChannel callback", channelCalled)
    }

    @Test
    fun `handleMeshtasticUri handles long contact path`() {
        val uri = Uri.parse("https://meshtastic.org/contact/v/somecontact")
        var contactCalled = false
        val handled = handleMeshtasticUri(uri, onContact = { contactCalled = true })
        assertTrue("Should handle long contact path", handled)
        assertTrue("Should invoke onContact callback", contactCalled)
    }
}
