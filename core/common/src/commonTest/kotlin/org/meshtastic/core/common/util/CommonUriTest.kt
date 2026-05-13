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

class CommonUriTest {
    @Test
    fun testParseAndToString() {
        val uriString = "content://com.example.provider/file.txt"
        val uri = CommonUri.parse(uriString)
        assertEquals(uriString, uri.toString())
    }

    @Test
    fun testQueryParameters() {
        val uri = CommonUri.parse("https://meshtastic.org/d/#key=value&complete=true")
        assertEquals("meshtastic.org", uri.host)
        assertEquals("key=value&complete=true", uri.fragment)
    }

    @Test
    fun testFileUri() {
        val uri = CommonUri.parse("file:///tmp/export.csv")
        assertEquals("file", uri.scheme)
        assertEquals("/tmp/export.csv", uri.path)
    }
}
