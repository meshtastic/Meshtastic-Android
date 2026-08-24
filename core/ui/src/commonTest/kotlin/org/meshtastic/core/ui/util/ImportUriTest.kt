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
package org.meshtastic.core.ui.util

import org.meshtastic.core.common.util.CommonUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportUriTest {
    @Test
    fun parseDeepLinkOrInvalid_dispatches_valid_uri() {
        val expected = CommonUri.parse("https://meshtastic.org/e/#payload")
        var handled: CommonUri? = null
        var invalidCalled = false

        parseDeepLinkOrInvalid(
            uriString = expected.toString(),
            onHandleDeepLink = { uri, _ -> handled = uri },
            onInvalid = { invalidCalled = true },
        )

        assertEquals(expected, handled)
        assertFalse(invalidCalled)
    }

    @Test
    fun parseDeepLinkOrInvalid_invokes_invalid_on_parse_failure() {
        var handled = false
        var invalidCalled = false

        parseDeepLinkOrInvalid(
            uriString = "not-used",
            onHandleDeepLink = { _, _ -> handled = true },
            onInvalid = { invalidCalled = true },
            parseUri = { throw IllegalArgumentException("bad uri") },
        )

        assertFalse(handled)
        assertTrue(invalidCalled)
    }
}
