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
package org.meshtastic.core.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommonUriTest {

    @Test
    fun testParse() {
        val uri = CommonUri.parse("https://meshtastic.org/path/to/page?param1=value1&param2=true#fragment")
        assertEquals("meshtastic.org", uri.host)
        assertEquals("fragment", uri.fragment)
        assertEquals(listOf("path", "to", "page"), uri.pathSegments)
        assertEquals("value1", uri.getQueryParameter("param1"))
        assertTrue(uri.getBooleanQueryParameter("param2", false))
    }

    @Test
    fun testBooleanParameters() {
        val uri = CommonUri.parse("meshtastic://test?t1=true&t2=1&t3=yes&f1=false&f2=0")
        assertTrue(uri.getBooleanQueryParameter("t1", false))
        assertTrue(uri.getBooleanQueryParameter("t2", false))
        assertTrue(uri.getBooleanQueryParameter("t3", false))
        assertTrue(!uri.getBooleanQueryParameter("f1", true))
        assertTrue(!uri.getBooleanQueryParameter("f2", true))
    }
}
