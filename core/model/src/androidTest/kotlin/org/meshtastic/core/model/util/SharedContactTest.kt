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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

@RunWith(AndroidJUnit4::class)
class SharedContactTest {

    @Test
    fun testSharedContactUrlRoundTrip() {
        val original = SharedContact(user = User(long_name = "Suzume", short_name = "SZ"), node_num = 12345)
        val url = original.getSharedContactUrl()
        val parsed = url.toSharedContact()

        assertEquals(original.node_num, parsed.node_num)
        assertEquals(original.user?.long_name, parsed.user?.long_name)
        assertEquals(original.user?.short_name, parsed.user?.short_name)
    }

    @Test
    fun testWwwHostIsAccepted() {
        val url = Uri.parse("https://www.meshtastic.org/v/#CggKBVN1enVtZRICU1oaBTEyMzQ1")
        val contact = url.toSharedContact()
        assertEquals("Suzume", contact.user?.long_name)
    }

    @Test
    fun testLongPathIsAccepted() {
        val url = Uri.parse("https://meshtastic.org/contact/v/#CggKBVN1enVtZRICU1oaBTEyMzQ1")
        val contact = url.toSharedContact()
        assertEquals("Suzume", contact.user?.long_name)
    }

    @Test(expected = java.net.MalformedURLException::class)
    fun testInvalidHostThrows() {
        val url = Uri.parse("https://example.com/v/#CggKBVN1enVtZRICU1oaBTEyMzQ1")
        url.toSharedContact()
    }
}
