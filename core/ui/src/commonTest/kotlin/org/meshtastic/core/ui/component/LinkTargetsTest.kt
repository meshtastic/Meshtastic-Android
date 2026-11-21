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
package org.meshtastic.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTargetsTest {

    @Test
    fun surrounding_whitespace_is_trimmed() {
        assertEquals("https://e.com", openableLinkTarget("  https://e.com \n"))
    }

    @Test
    fun angle_brackets_around_the_target_are_removed() {
        assertEquals("https://e.com/a", openableLinkTarget(" < https://e.com/a > "))
    }

    @Test
    fun scheme_is_compared_case_insensitively_and_kept_as_written() {
        assertEquals("MailTo:a@e.com", openableLinkTarget("MailTo:a@e.com"))
    }

    @Test
    fun control_character_after_the_scheme_is_rejected() {
        assertNull(openableLinkTarget("https://e.com/a\u0001b"))
    }

    @Test
    fun whitespace_after_the_scheme_is_rejected() {
        assertNull(openableLinkTarget("https://e.com/a b"))
    }

    @Test
    fun backslash_in_a_target_is_rejected() {
        assertNull(openableLinkTarget("https://example.com\\@meshtastic.org"))
    }

    @Test
    fun web_address_without_a_host_after_the_slashes_is_rejected() {
        assertNull(openableLinkTarget("https:example.com"))
        assertNull(openableLinkTarget("https:///example.com"))
        assertEquals("mailto:a@e.com", openableLinkTarget("mailto:a@e.com"))
    }

    @Test
    fun tel_is_opt_in() {
        assertNull(openableLinkTarget("tel:+15551234567"))
        assertEquals("tel:+15551234567", openableLinkTarget("tel:+15551234567", allowTel = true))
    }

    @Test
    fun empty_and_schemeless_targets_are_rejected() {
        assertNull(openableLinkTarget(""))
        assertNull(openableLinkTarget("<>"))
        assertNull(openableLinkTarget(":x"))
        assertNull(openableLinkTarget("e.com"))
    }

    @Test
    fun label_host_comparison_ignores_scheme_case_port_path_and_www() {
        assertFalse(labelNamesDifferentHost("Meshtastic.ORG", "https://www.meshtastic.org:443/x?y"))
        assertTrue(labelNamesDifferentHost("meshtastic.org", "https://meshtastic.org.example.com"))
        assertTrue(labelNamesDifferentHost("https://meshtastic.org", "https://user@example.com"))
    }

    @Test
    fun label_that_is_plain_text_never_mismatches() {
        assertFalse(labelNamesDifferentHost("read the docs", "https://example.com"))
        assertFalse(labelNamesDifferentHost("v2.8", "https://example.com"))
    }
}
