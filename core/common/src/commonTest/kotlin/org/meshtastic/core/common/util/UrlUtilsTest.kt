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

class UrlUtilsTest {

    @Test
    fun testEncode() {
        assertEquals("Hello%20World", UrlUtils.encode("Hello World"))
        assertEquals("abc-123._~", UrlUtils.encode("abc-123._~"))
        assertEquals("%21%40%23%24%25", UrlUtils.encode("!@#$%"))
        assertEquals("%C3%A1%C3%A9%C3%AD", UrlUtils.encode("áéí"))
    }
}
